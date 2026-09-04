package at.ymeri.my.finance.domain.service.recurring;

import at.ymeri.my.finance.domain.api.PostDueOccurrencesService;
import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.data.recurring.TransactionType;
import at.ymeri.my.finance.domain.data.income.IncomeDto;
import at.ymeri.my.finance.domain.data.recurring.PostingRunResult;
import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesDto;
import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesStatus;
import at.ymeri.my.finance.domain.spi.ingestion.IngestTransactionsPersistencePort;
import at.ymeri.my.finance.domain.spi.recurring.GetRecurringSeriesPersistencePort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Posts the transactions that confirmed recurring series are currently due to produce.
 *
 * <p><strong>Stateless by design.</strong> Every run recomputes the entire due set from the ledger
 * and attempts each occurrence; there is no cursor and no "last run" timestamp. A run an hour ago
 * and a run after three weeks of downtime take the identical path, because an occurrence already
 * recorded is refused by the database rather than by a check here. The alternative — tracking
 * progress — is state that can be written but not committed, or reset by a restore from backup, and
 * each of those silently skips or repeats an operator's rent.
 *
 * <p><strong>A prediction is never evidence.</strong> The anchor date, the amount and the account all
 * come from {@link RecurringSeriesMembers#realOccurrencesOf}, which excludes this service's own
 * output and any reversal. Anchoring on a prediction would make the app derive next month from last
 * month's guess: the date would drift onto something nobody observed and a rent increase would never
 * be picked up, all while looking entirely correct.
 *
 * <p><strong>A series with no real occurrence is skipped, not guessed at.</strong> A series carries
 * no amount and no account of its own — only a grouping key, description, cadence, direction and
 * status — so with no history there is nothing to derive them from, and inventing them would
 * fabricate financial records (FR-006).
 */
@Service
public class PostDueOccurrencesServiceImpl implements PostDueOccurrencesService {

    /** Namespace for the identity these transactions are stored under. */
    static final String IDENTITY_PREFIX = "recurring:";

    private final GetRecurringSeriesPersistencePort getRecurringSeriesPersistencePort;
    private final RecurringSeriesMembers recurringSeriesMembers;
    private final IngestTransactionsPersistencePort ingestTransactionsPersistencePort;

    public PostDueOccurrencesServiceImpl(
            GetRecurringSeriesPersistencePort getRecurringSeriesPersistencePort,
            RecurringSeriesMembers recurringSeriesMembers,
            IngestTransactionsPersistencePort ingestTransactionsPersistencePort) {
        this.getRecurringSeriesPersistencePort = getRecurringSeriesPersistencePort;
        this.recurringSeriesMembers = recurringSeriesMembers;
        this.ingestTransactionsPersistencePort = ingestTransactionsPersistencePort;
    }

    /**
     * The identity an occurrence is stored under: deterministic from the series and the calendar
     * date of the period, so re-deriving it on a later run produces the same string. That is what
     * feature 022's unique index refuses, and therefore what makes catch-up idempotent rather than
     * merely careful.
     */
    public static String identityFor(String seriesId, LocalDate occurrence) {
        return IDENTITY_PREFIX + seriesId + ":" + occurrence;
    }

    @Override
    public PostingRunResult postDueOccurrences(LocalDate today) {
        List<BillDto> bills = new ArrayList<>();
        List<IncomeDto> incomes = new ArrayList<>();
        List<Candidate> candidates = new ArrayList<>();
        int skippedSeries = 0;

        for (RecurringSeriesDto series : getRecurringSeriesPersistencePort.getAll()) {
            if (series.getStatus() != RecurringSeriesStatus.CONFIRMED) {
                continue;
            }

            List<RecurringSeriesMembers.MemberOccurrence> real =
                    recurringSeriesMembers.realOccurrencesOf(series);
            if (real.isEmpty()) {
                skippedSeries++;
                continue;
            }

            RecurringSeriesMembers.MemberOccurrence latest = real.get(real.size() - 1);
            if (latest.accountId() == null || latest.amount() == null) {
                skippedSeries++;
                continue;
            }

            List<LocalDate> due = OccurrenceSchedule.dueOccurrences(
                    series.getFrequency(),
                    latest.time().atZoneSameInstant(ZoneOffset.UTC).toLocalDate(),
                    confirmedOn(series),
                    today);

            for (LocalDate occurrence : due) {
                String identity = identityFor(series.getId(), occurrence);
                candidates.add(new Candidate(series.getId(), occurrence, latest.amount(), identity));
                if (series.getTransactionType() == TransactionType.BILL) {
                    bills.add(toBill(series, latest, occurrence, identity));
                } else {
                    incomes.add(toIncome(series, latest, occurrence, identity));
                }
            }
        }

        // One write for the whole run. What landed comes back from the write itself, never from a
        // lookup beforehand — which is what makes two overlapping runs safe.
        Map<String, String> insertedByIdentity =
                ingestTransactionsPersistencePort.insertNew(bills, incomes);

        List<PostingRunResult.PostedOccurrence> posted = new ArrayList<>();
        int alreadyPosted = 0;
        for (Candidate candidate : candidates) {
            String transactionId = insertedByIdentity.get(candidate.identity());
            if (transactionId != null) {
                posted.add(new PostingRunResult.PostedOccurrence(
                        candidate.seriesId(), candidate.occurrence(), transactionId, candidate.amount()));
            } else {
                alreadyPosted++;
            }
        }
        return new PostingRunResult(List.copyOf(posted), alreadyPosted, skippedSeries);
    }

    /**
     * When the operator confirmed this series, approximated by its last update.
     *
     * <p>Approximate on purpose rather than by omission: a later edit moves it, which errs toward
     * posting <em>less</em>. That is the safe direction — a bound set too late skips a period the
     * operator can enter by hand, while one set too early invents transactions they never had.
     */
    private static LocalDate confirmedOn(RecurringSeriesDto series) {
        var stamp = series.getUpdatedAt() != null ? series.getUpdatedAt() : series.getCreatedAt();
        return stamp == null ? null : stamp.atZoneSameInstant(ZoneOffset.UTC).toLocalDate();
    }

    private BillDto toBill(RecurringSeriesDto series,
                           RecurringSeriesMembers.MemberOccurrence latest,
                           LocalDate occurrence, String identity) {
        BillDto bill = new BillDto();
        bill.setDescription(series.getDescription());
        bill.setAmount(latest.amount());
        bill.setTime(occurrence.atStartOfDay().atOffset(ZoneOffset.UTC));
        bill.setAccountId(latest.accountId());
        // For a bill series the grouping key IS the category — no derivation needed.
        bill.setCategoryId(series.getGroupKey());
        bill.setExternalId(identity);
        bill.setRecurringSeriesId(series.getId());
        return bill;
    }

    private IncomeDto toIncome(RecurringSeriesDto series,
                               RecurringSeriesMembers.MemberOccurrence latest,
                               LocalDate occurrence, String identity) {
        IncomeDto income = new IncomeDto();
        income.setDescription(series.getDescription());
        income.setAmount(latest.amount());
        income.setTime(occurrence.atStartOfDay().atOffset(ZoneOffset.UTC));
        income.setAccountId(latest.accountId());
        income.setExternalId(identity);
        income.setRecurringSeriesId(series.getId());
        return income;
    }

    private record Candidate(String seriesId, LocalDate occurrence, BigDecimal amount, String identity) {
    }
}
