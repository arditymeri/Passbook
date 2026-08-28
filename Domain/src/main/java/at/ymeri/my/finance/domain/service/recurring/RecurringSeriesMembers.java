package at.ymeri.my.finance.domain.service.recurring;

import at.ymeri.my.finance.domain.api.GetBillService;
import at.ymeri.my.finance.domain.api.GetIncomeService;
import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesDto;
import at.ymeri.my.finance.domain.data.recurring.TransactionType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Resolves a confirmed series' matching transactions — shared by {@link GetUpcomingRecurringServiceImpl}
 * (single next-occurrence prediction) and the cash flow forecast (multi-occurrence prediction), so both
 * agree on exactly which transactions belong to a series.
 */
@Component
public class RecurringSeriesMembers {

    private final GetBillService getBillService;
    private final GetIncomeService getIncomeService;

    public RecurringSeriesMembers(GetBillService getBillService, GetIncomeService getIncomeService) {
        this.getBillService = getBillService;
        this.getIncomeService = getIncomeService;
    }

    /**
     * A confirmed series' matching transactions, sorted oldest first — a bill matched on
     * {@code categoryId}, or an income matched on {@code source.name()}, plus a shared
     * normalized-description match, mirroring {@link DetectRecurringSeriesServiceImpl}'s grouping.
     */
    public List<MemberOccurrence> membersOf(RecurringSeriesDto series) {
        if (series.getTransactionType() == TransactionType.BILL) {
            return getBillService.getAll().stream()
                    .filter(b -> series.getGroupKey().equals(b.getCategoryId()))
                    .filter(b -> series.getDescription().equals(RecurringMatching.normalizeDescription(b.getDescription())))
                    .map(b -> new MemberOccurrence(b.getId(), b.getTime(), b.getAmount(), b.getAccountId()))
                    .sorted(Comparator.comparing(MemberOccurrence::time))
                    .toList();
        }
        return getIncomeService.getAll().stream()
                .filter(i -> i.getSource() != null && series.getGroupKey().equals(i.getSource().name()))
                .filter(i -> series.getDescription().equals(RecurringMatching.normalizeDescription(i.getDescription())))
                .map(i -> new MemberOccurrence(i.getId(), i.getTime(), i.getAmount(), i.getAccountId()))
                .sorted(Comparator.comparing(MemberOccurrence::time))
                .toList();
    }

    public record MemberOccurrence(String id, OffsetDateTime time, BigDecimal amount, String accountId) {
    }
}
