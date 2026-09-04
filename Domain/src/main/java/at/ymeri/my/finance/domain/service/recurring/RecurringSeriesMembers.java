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

    /**
     * The series' <strong>real</strong> occurrences — {@link #membersOf} minus anything this app
     * predicted and anything that reverses a transaction.
     *
     * <p><strong>Why this exists, and why it is a separate method.</strong> Feature 023 posts
     * transactions for a confirmed series. Those postings match the same grouping {@code membersOf}
     * uses, so they immediately become "occurrences" of the series that produced them. Deriving the
     * next occurrence's date or amount from that set gives a system that learns from its own
     * guesses: the anchor drifts onto dates nobody observed, and a rent increase is never picked up
     * because each month copies the previous month's prediction. Nothing looks wrong while it
     * happens.
     *
     * <p>A prediction is not evidence. Only a transaction that was entered by a person or came from
     * a bank may anchor the next one.
     *
     * <p>Reversals are excluded for a related reason: after an import supersedes a prediction, the
     * compensating entry also matches the series' description and would otherwise be counted as an
     * occurrence in its own right.
     *
     * <p><strong>{@link #membersOf} is deliberately left alone.</strong> Feature 015's forecast and
     * feature 018's dashboard both read it, and for them an auto-posted transaction genuinely is an
     * occurrence — it is a real entry in the ledger. Narrowing the shared method to suit this
     * feature would quietly change two others.
     */
    public List<MemberOccurrence> realOccurrencesOf(RecurringSeriesDto series) {
        if (series.getTransactionType() == TransactionType.BILL) {
            return getBillService.getAll().stream()
                    .filter(b -> b.getRecurringSeriesId() == null)
                    .filter(b -> !b.isReversal())
                    .filter(b -> series.getGroupKey().equals(b.getCategoryId()))
                    .filter(b -> series.getDescription().equals(RecurringMatching.normalizeDescription(b.getDescription())))
                    .map(b -> new MemberOccurrence(b.getId(), b.getTime(), b.getAmount(), b.getAccountId()))
                    .sorted(Comparator.comparing(MemberOccurrence::time))
                    .toList();
        }
        return getIncomeService.getAll().stream()
                .filter(i -> i.getRecurringSeriesId() == null)
                .filter(i -> !i.isReversal())
                .filter(i -> i.getSource() != null && series.getGroupKey().equals(i.getSource().name()))
                .filter(i -> series.getDescription().equals(RecurringMatching.normalizeDescription(i.getDescription())))
                .map(i -> new MemberOccurrence(i.getId(), i.getTime(), i.getAmount(), i.getAccountId()))
                .sorted(Comparator.comparing(MemberOccurrence::time))
                .toList();
    }

    public record MemberOccurrence(String id, OffsetDateTime time, BigDecimal amount, String accountId) {
    }
}
