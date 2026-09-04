package at.ymeri.my.finance.domain.service.ingestion;

import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.spi.bill.GetBillPersistencePort;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Suggests a category for an incoming statement row by reusing the category of the most recent past
 * bill with the same normalised description.
 *
 * <p>This is feature 017's rule moved server-side, unchanged in behaviour, so the operator loses
 * nothing in the move (022 research R9). It is not a classifier and does not learn: learning from the
 * operator's corrections is a separate feature, and pretending otherwise here would be the
 * speculative generality the constitution prohibits.
 *
 * <p>Normalisation is deliberately more forgiving than the one used for <em>identity</em>: getting a
 * suggestion wrong costs the operator one dropdown change, whereas getting identity wrong loses or
 * duplicates a transaction. The two rules answer different questions and correctly differ.
 */
@Service
public class SuggestCategoryService {

    private final GetBillPersistencePort getBillPersistencePort;

    public SuggestCategoryService(GetBillPersistencePort getBillPersistencePort) {
        this.getBillPersistencePort = getBillPersistencePort;
    }

    /**
     * @return the suggested category id, or empty when no past bill matches
     */
    public Optional<String> suggestFor(String description) {
        String normalized = normalize(description);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        return getBillPersistencePort.getAll().stream()
                .filter(bill -> bill.getCategoryId() != null && !bill.getCategoryId().isBlank())
                .filter(bill -> normalize(bill.getDescription()).equals(normalized))
                .max(Comparator.comparing(BillDto::getTime, Comparator.nullsFirst(OffsetDateTime::compareTo)))
                .map(BillDto::getCategoryId);
    }

    /**
     * Suggestions for a whole statement, reading past bills once rather than per row.
     *
     * @return one entry per description, in the order given; an entry is null when nothing matched
     */
    public List<String> suggestFor(List<String> descriptions) {
        List<BillDto> categorised = getBillPersistencePort.getAll().stream()
                .filter(bill -> bill.getCategoryId() != null && !bill.getCategoryId().isBlank())
                .toList();

        return descriptions.stream()
                .map(description -> {
                    String normalized = normalize(description);
                    if (normalized.isEmpty()) {
                        return (String) null;
                    }
                    return categorised.stream()
                            .filter(bill -> normalize(bill.getDescription()).equals(normalized))
                            .max(Comparator.comparing(BillDto::getTime,
                                    Comparator.nullsFirst(OffsetDateTime::compareTo)))
                            .map(BillDto::getCategoryId)
                            .orElse(null);
                })
                .toList();
    }

    private static String normalize(String description) {
        return description == null
                ? ""
                : description.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
