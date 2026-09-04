package at.ymeri.my.finance.domain.service.recurring;

import at.ymeri.my.finance.domain.api.ReconcileAutoPostedService;
import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.data.income.IncomeDto;
import at.ymeri.my.finance.domain.spi.bill.AddBillPersistencePort;
import at.ymeri.my.finance.domain.spi.bill.GetBillPersistencePort;
import at.ymeri.my.finance.domain.spi.income.AddIncomePersistencePort;
import at.ymeri.my.finance.domain.spi.income.GetIncomePersistencePort;
import at.ymeri.my.finance.domain.spi.recurring.GetAutoPostedTransactionsPersistencePort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Supersedes an auto-posted prediction when the bank's own version of the same transaction arrives.
 *
 * <p><strong>Why this exists.</strong> An auto-posted transaction is an opinion; an imported booking
 * is a statement of fact. Once statement import is in normal use, every prediction for an imported
 * account is on a collision course with the bank's own row, and leaving both double-counts the
 * operator's rent.
 *
 * <p><strong>How it supersedes.</strong> Through the same compensating entry a manual correction
 * writes — a reversal with the negated amount referencing the original. The prediction is never
 * modified and never deleted (Principle I), the ledger nets to the imported figure, and the history
 * views built for feature 008 already know how to explain what happened.
 *
 * <p><strong>Why the tolerances are borrowed rather than invented.</strong> Matching reuses
 * {@link RecurringMatching}'s cadence window and its 5%/€2.00 amount band — the same rules that
 * recognised the series in the first place. A second, different notion of "close enough" would let
 * the app recognise a series it then refuses to reconcile, and nobody would ever successfully debug
 * that.
 *
 * <p>Deliberately <strong>not</strong> called when a transaction is entered by hand: the operator is
 * looking at their own history, where the auto-posted row is visible and marked, and silently
 * reversing it because they typed a similar amount would be the app second-guessing someone who can
 * already see the situation.
 */
@Service
public class ReconcileAutoPostedServiceImpl implements ReconcileAutoPostedService {

    private static final BigDecimal AMOUNT_PERCENT_TOLERANCE = new BigDecimal("0.05");
    private static final BigDecimal AMOUNT_FLOOR_TOLERANCE = new BigDecimal("2.00");

    private final GetBillPersistencePort getBillPersistencePort;
    private final AddBillPersistencePort addBillPersistencePort;
    private final GetIncomePersistencePort getIncomePersistencePort;
    private final AddIncomePersistencePort addIncomePersistencePort;
    private final GetAutoPostedTransactionsPersistencePort getAutoPostedTransactionsPersistencePort;

    public ReconcileAutoPostedServiceImpl(
            GetBillPersistencePort getBillPersistencePort,
            AddBillPersistencePort addBillPersistencePort,
            GetIncomePersistencePort getIncomePersistencePort,
            AddIncomePersistencePort addIncomePersistencePort,
            GetAutoPostedTransactionsPersistencePort getAutoPostedTransactionsPersistencePort) {
        this.getBillPersistencePort = getBillPersistencePort;
        this.addBillPersistencePort = addBillPersistencePort;
        this.getIncomePersistencePort = getIncomePersistencePort;
        this.addIncomePersistencePort = addIncomePersistencePort;
        this.getAutoPostedTransactionsPersistencePort = getAutoPostedTransactionsPersistencePort;
    }

    @Override
    public int reconcileBills(List<String> incomingBillIds) {
        if (incomingBillIds == null || incomingBillIds.isEmpty()) {
            return 0;
        }
        // Predictions claimed earlier in this same run. The query below excludes anything a
        // committed reversal already references; this set covers the reversals written since, so
        // two imported rows in one statement cannot both cancel the same prediction.
        Set<String> claimedInThisRun = new HashSet<>();

        int superseded = 0;
        for (String incomingId : incomingBillIds) {
            Optional<BillDto> incoming = byId(incomingId, getBillPersistencePort::getBillById);
            if (incoming.isEmpty() || incoming.get().getRecurringSeriesId() != null
                    || incoming.get().getAccountId() == null) {
                // Never reconcile a prediction against another prediction, and an account is what
                // scopes the search — without one there is nothing to search.
                continue;
            }
            BillDto fact = incoming.get();
            Optional<BillDto> match = getAutoPostedTransactionsPersistencePort
                    .supersedableBills(fact.getAccountId()).stream()
                    .filter(b -> !claimedInThisRun.contains(b.getId()))
                    .filter(b -> withinAmountTolerance(b.getAmount(), fact.getAmount()))
                    .filter(b -> withinDateTolerance(b.getTime(), fact.getTime()))
                    .min(closestTo(fact.getTime()));
            if (match.isPresent()) {
                addBillPersistencePort.addBill(reversalOf(match.get()));
                claimedInThisRun.add(match.get().getId());
                superseded++;
            }
        }
        return superseded;
    }

    @Override
    public int reconcileIncomes(List<String> incomingIncomeIds) {
        if (incomingIncomeIds == null || incomingIncomeIds.isEmpty()) {
            return 0;
        }
        Set<String> claimedInThisRun = new HashSet<>();

        int superseded = 0;
        for (String incomingId : incomingIncomeIds) {
            Optional<IncomeDto> incoming = byId(incomingId, getIncomePersistencePort::getIncomeById);
            if (incoming.isEmpty() || incoming.get().getRecurringSeriesId() != null
                    || incoming.get().getAccountId() == null) {
                continue;
            }
            IncomeDto fact = incoming.get();
            Optional<IncomeDto> match = getAutoPostedTransactionsPersistencePort
                    .supersedableIncomes(fact.getAccountId()).stream()
                    .filter(i -> !claimedInThisRun.contains(i.getId()))
                    .filter(i -> withinAmountTolerance(i.getAmount(), fact.getAmount()))
                    .filter(i -> withinDateTolerance(i.getTime(), fact.getTime()))
                    .min(Comparator.<IncomeDto, Duration>comparing(i -> gap(i.getTime(), fact.getTime()))
                            .thenComparing(IncomeDto::getTime));
            if (match.isPresent()) {
                addIncomePersistencePort.addIncome(reversalOf(match.get()));
                claimedInThisRun.add(match.get().getId());
                superseded++;
            }
        }
        return superseded;
    }

    /** Ids reach here as strings; a row whose id is not a UUID simply has no match to find. */
    private static <T> Optional<T> byId(String id, java.util.function.Function<UUID, Optional<T>> lookup) {
        try {
            return lookup.apply(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Closest by date, ties to the earlier period (FR-011). The rule matters more than which rule it
     * is: the same import must resolve to the same prediction every time, or an operator re-running
     * an import could supersede a different row than the first attempt did.
     */
    private static Comparator<BillDto> closestTo(OffsetDateTime factTime) {
        return Comparator.<BillDto, Duration>comparing(b -> gap(b.getTime(), factTime))
                .thenComparing(BillDto::getTime);
    }

    private static Duration gap(OffsetDateTime a, OffsetDateTime b) {
        return Duration.between(a, b).abs();
    }

    /**
     * The 5% band with a €2.00 floor, matching {@link RecurringMatching}'s own constants. The floor
     * matters for small subscriptions, where 5% of €3.99 is meaninglessly tight.
     */
    static boolean withinAmountTolerance(BigDecimal predicted, BigDecimal actual) {
        if (predicted == null || actual == null) {
            return false;
        }
        BigDecimal tolerance = predicted.abs().multiply(AMOUNT_PERCENT_TOLERANCE).max(AMOUNT_FLOOR_TOLERANCE);
        return predicted.subtract(actual).abs().compareTo(tolerance) <= 0;
    }

    /**
     * Within a week either way. Deliberately looser than the cadence-scaled window used for
     * <em>detecting</em> a series: here the prediction's date is one the app chose, and a bank
     * posting a few days either side of it is the normal case rather than evidence of a different
     * transaction.
     */
    static boolean withinDateTolerance(OffsetDateTime predicted, OffsetDateTime actual) {
        if (predicted == null || actual == null) {
            return false;
        }
        return gap(predicted, actual).compareTo(Duration.ofDays(7)) <= 0;
    }

    private static BillDto reversalOf(BillDto prediction) {
        BillDto reversal = new BillDto();
        reversal.setAmount(prediction.getAmount().negate());
        reversal.setDescription(prediction.getDescription());
        reversal.setTime(prediction.getTime());
        reversal.setCategoryId(prediction.getCategoryId());
        reversal.setAccountId(prediction.getAccountId());
        reversal.setCurrency(prediction.getCurrency());
        reversal.setCorrectsTransactionId(prediction.getId());
        reversal.setReversal(true);
        // The reversal carries the series too, so it is recognisably part of this feature's activity
        // and is excluded from the series' real occurrences.
        reversal.setRecurringSeriesId(prediction.getRecurringSeriesId());
        return reversal;
    }

    private static IncomeDto reversalOf(IncomeDto prediction) {
        IncomeDto reversal = new IncomeDto();
        reversal.setAmount(prediction.getAmount().negate());
        reversal.setDescription(prediction.getDescription());
        reversal.setTime(prediction.getTime());
        reversal.setAccountId(prediction.getAccountId());
        reversal.setCurrency(prediction.getCurrency());
        reversal.setCorrectsTransactionId(prediction.getId());
        reversal.setReversal(true);
        reversal.setRecurringSeriesId(prediction.getRecurringSeriesId());
        return reversal;
    }
}
