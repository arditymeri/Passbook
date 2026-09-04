package at.ymeri.my.finance.domain.service.recurring;

import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.data.common.RecurringFrequency;
import at.ymeri.my.finance.domain.data.recurring.TransactionType;
import at.ymeri.my.finance.domain.data.income.IncomeDto;
import at.ymeri.my.finance.domain.data.recurring.PostingRunResult;
import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesDto;
import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesStatus;
import at.ymeri.my.finance.domain.spi.ingestion.IngestTransactionsPersistencePort;
import at.ymeri.my.finance.domain.spi.recurring.GetRecurringSeriesPersistencePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Posting behaviour under plain JUnit — no context, no database, no clock.
 *
 * <p>Fakes rather than mocks: the persistence port here behaves like the real one, refusing an
 * identity it has already seen. That is what makes the repeat-run assertions mean something rather
 * than only asserting that a mock was called.
 */
class PostDueOccurrencesServiceImplTest {

    private static final String ACCOUNT = "acct-1";
    private static final String CATEGORY = "cat-rent";

    private FakeSeriesPort seriesPort;
    private FakeMembers members;
    private FakeIngestPort ingestPort;
    private PostDueOccurrencesServiceImpl service;

    @BeforeEach
    void setUp() {
        seriesPort = new FakeSeriesPort();
        members = new FakeMembers();
        ingestPort = new FakeIngestPort();
        service = new PostDueOccurrencesServiceImpl(seriesPort, members, ingestPort);
    }

    // --- US1: a due occurrence is posted --------------------------------------------------------

    @Test
    void aDueOccurrenceIsPostedWithValuesFromTheLatestRealOccurrence() {
        RecurringSeriesDto rent = confirmedSeries("s1", RecurringFrequency.MONTHLY);
        members.setReal("s1", occurrence("2026-02-01", "1250.00"));

        PostingRunResult result = service.postDueOccurrences(LocalDate.of(2026, 3, 1));

        assertThat(result.postedCount()).isEqualTo(1);
        assertThat(ingestPort.bills).hasSize(1);
        BillDto posted = ingestPort.bills.get(0);
        assertThat(posted.getAmount()).isEqualByComparingTo("1250.00");
        assertThat(posted.getAccountId()).isEqualTo(ACCOUNT);
        assertThat(posted.getCategoryId())
                .as("for a bill series the group key IS the category — no derivation")
                .isEqualTo(CATEGORY);
        assertThat(posted.getDescription()).isEqualTo(rent.getDescription());
        assertThat(posted.getTime().toLocalDate()).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    void aPostedTransactionCarriesItsProvenance() {
        confirmedSeries("s1", RecurringFrequency.MONTHLY);
        members.setReal("s1", occurrence("2026-02-01", "1250.00"));

        service.postDueOccurrences(LocalDate.of(2026, 3, 1));

        BillDto posted = ingestPort.bills.get(0);
        assertThat(posted.getRecurringSeriesId())
                .as("Principle V: why is this row here, for a row nobody typed")
                .isEqualTo("s1");
        assertThat(posted.getExternalId()).isEqualTo("recurring:s1:2026-03-01");
    }

    @Test
    void runningAgainPostsNothingFurther() {
        confirmedSeries("s1", RecurringFrequency.MONTHLY);
        members.setReal("s1", occurrence("2026-02-01", "1250.00"));

        assertThat(service.postDueOccurrences(LocalDate.of(2026, 3, 1)).postedCount()).isEqualTo(1);

        PostingRunResult second = service.postDueOccurrences(LocalDate.of(2026, 3, 1));
        assertThat(second.postedCount()).isZero();
        assertThat(second.alreadyPostedCount())
                .as("re-offered and refused, which is reported rather than hidden")
                .isEqualTo(1);
    }

    @Test
    void anIncomeSeriesPostsAnIncome() {
        RecurringSeriesDto salary = confirmedSeries("s2", RecurringFrequency.MONTHLY);
        salary.setTransactionType(TransactionType.INCOME);
        members.setReal("s2", occurrence("2026-02-28", "2400.00"));

        service.postDueOccurrences(LocalDate.of(2026, 3, 28));

        assertThat(ingestPort.bills).isEmpty();
        assertThat(ingestPort.incomes).hasSize(1);
        assertThat(ingestPort.incomes.get(0).getAmount()).isEqualByComparingTo("2400.00");
    }

    // --- The feedback loop (research R2) --------------------------------------------------------

    @Test
    void theNextPostIsDerivedFromTheLatestRealOccurrenceNotFromThePreviousPost() {
        // quickstart scenario 6, and the highest-value assertion in the feature.
        //
        // If auto-posted rows were allowed to anchor the series, March would copy February's
        // prediction — the app averaging its own output. The date would drift onto something nobody
        // observed and a rent increase would never be picked up, all while looking correct.
        confirmedSeries("s1", RecurringFrequency.MONTHLY);
        members.setReal("s1", occurrence("2026-01-01", "1000.00"));

        service.postDueOccurrences(LocalDate.of(2026, 2, 1));
        // The February post exists now. realOccurrencesOf deliberately does not return it, so the
        // anchor stays on the January fact.
        service.postDueOccurrences(LocalDate.of(2026, 3, 1));

        assertThat(ingestPort.bills)
                .as("both posts derive from the January real occurrence")
                .extracting(BillDto::getAmount)
                .allSatisfy(amount -> assertThat(amount).isEqualByComparingTo("1000.00"));
        assertThat(ingestPort.bills).extracting(b -> b.getTime().toLocalDate())
                .containsExactly(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1));
    }

    @Test
    void aNewRealOccurrenceMovesTheAnchorForward() {
        // The other half of R2: the app converges on fact. Once the bank reports the higher rent,
        // subsequent posts use it.
        confirmedSeries("s1", RecurringFrequency.MONTHLY);
        members.setReal("s1", occurrence("2026-01-01", "1000.00"));
        service.postDueOccurrences(LocalDate.of(2026, 2, 1));

        members.setReal("s1", occurrence("2026-01-01", "1000.00"), occurrence("2026-03-01", "1100.00"));
        service.postDueOccurrences(LocalDate.of(2026, 4, 1));

        assertThat(ingestPort.bills).last()
                .satisfies(bill -> {
                    assertThat(bill.getAmount()).isEqualByComparingTo("1100.00");
                    assertThat(bill.getTime().toLocalDate()).isEqualTo(LocalDate.of(2026, 4, 1));
                });
    }

    // --- Which series post ----------------------------------------------------------------------

    @Test
    void onlyConfirmedSeriesPost() {
        for (RecurringSeriesStatus status : List.of(RecurringSeriesStatus.PROPOSED,
                RecurringSeriesStatus.DISMISSED, RecurringSeriesStatus.STOPPED)) {
            setUp();
            RecurringSeriesDto series = confirmedSeries("s1", RecurringFrequency.MONTHLY);
            series.setStatus(status);
            members.setReal("s1", occurrence("2026-02-01", "1250.00"));

            assertThat(service.postDueOccurrences(LocalDate.of(2026, 3, 1)).postedCount())
                    .as("a %s series must post nothing", status)
                    .isZero();
        }
    }

    @Test
    void aSeriesWithNoRealOccurrenceIsSkippedNotGuessedAt() {
        // FR-006. A series carries no amount and no account of its own, so with no history there is
        // nothing to derive them from — and inventing them would fabricate financial records.
        confirmedSeries("s1", RecurringFrequency.MONTHLY);
        members.setReal("s1");

        PostingRunResult result = service.postDueOccurrences(LocalDate.of(2026, 3, 1));

        assertThat(result.postedCount()).isZero();
        assertThat(result.skippedSeriesCount()).isEqualTo(1);
        assertThat(ingestPort.bills).isEmpty();
    }

    @Test
    void downtimeIsCaughtUpInOneRun() {
        confirmedSeries("s1", RecurringFrequency.WEEKLY);
        members.setReal("s1", occurrence("2026-03-01", "20.00"));

        PostingRunResult result = service.postDueOccurrences(LocalDate.of(2026, 3, 25));

        assertThat(result.postedCount()).isEqualTo(3);
        assertThat(ingestPort.bills).extracting(b -> b.getTime().toLocalDate())
                .containsExactly(LocalDate.of(2026, 3, 8), LocalDate.of(2026, 3, 15),
                        LocalDate.of(2026, 3, 22));
    }

    // --- fakes ----------------------------------------------------------------------------------

    private RecurringSeriesDto confirmedSeries(String id, RecurringFrequency frequency) {
        RecurringSeriesDto series = new RecurringSeriesDto();
        series.setId(id);
        series.setTransactionType(TransactionType.BILL);
        series.setGroupKey(CATEGORY);
        series.setDescription("rent");
        series.setFrequency(frequency);
        series.setStatus(RecurringSeriesStatus.CONFIRMED);
        series.setCreatedAt(OffsetDateTime.parse("2020-01-01T00:00:00Z"));
        series.setUpdatedAt(OffsetDateTime.parse("2020-01-01T00:00:00Z"));
        seriesPort.series.add(series);
        return series;
    }

    private static RecurringSeriesMembers.MemberOccurrence occurrence(String date, String amount) {
        return new RecurringSeriesMembers.MemberOccurrence(
                UUID.randomUUID().toString(),
                LocalDate.parse(date).atStartOfDay().atOffset(ZoneOffset.UTC),
                new BigDecimal(amount),
                ACCOUNT);
    }

    private static final class FakeSeriesPort implements GetRecurringSeriesPersistencePort {
        private final List<RecurringSeriesDto> series = new ArrayList<>();

        @Override public List<RecurringSeriesDto> getAll() { return List.copyOf(series); }
        @Override public Optional<RecurringSeriesDto> findById(String id) {
            return series.stream().filter(s -> s.getId().equals(id)).findFirst();
        }
        @Override public Optional<RecurringSeriesDto> findByKey(TransactionType type, String groupKey,
                                                                String description) {
            return Optional.empty();
        }
    }

    /** Stands in for the real-occurrence view; the point is that it never returns posted rows. */
    private static final class FakeMembers extends RecurringSeriesMembers {
        private final Map<String, List<MemberOccurrence>> real = new HashMap<>();

        private FakeMembers() {
            super(null, null);
        }

        void setReal(String seriesId, MemberOccurrence... occurrences) {
            real.put(seriesId, List.of(occurrences));
        }

        @Override
        public List<MemberOccurrence> realOccurrencesOf(RecurringSeriesDto series) {
            return real.getOrDefault(series.getId(), List.of());
        }
    }

    /** Behaves like the real port: an identity already seen is refused. */
    private static final class FakeIngestPort implements IngestTransactionsPersistencePort {
        private final List<BillDto> bills = new ArrayList<>();
        private final List<IncomeDto> incomes = new ArrayList<>();
        private final Map<String, String> byIdentity = new HashMap<>();

        @Override
        public Map<String, String> insertNew(Collection<BillDto> newBills, Collection<IncomeDto> newIncomes) {
            Map<String, String> inserted = new HashMap<>();
            for (BillDto bill : newBills) {
                if (!byIdentity.containsKey(bill.getExternalId())) {
                    String id = UUID.randomUUID().toString();
                    byIdentity.put(bill.getExternalId(), id);
                    bills.add(bill);
                    inserted.put(bill.getExternalId(), id);
                }
            }
            for (IncomeDto income : newIncomes) {
                if (!byIdentity.containsKey(income.getExternalId())) {
                    String id = UUID.randomUUID().toString();
                    byIdentity.put(income.getExternalId(), id);
                    incomes.add(income);
                    inserted.put(income.getExternalId(), id);
                }
            }
            return inserted;
        }

        @Override
        public java.util.Set<String> existingIdentities(String accountId, Collection<String> candidates) {
            return java.util.Set.of();
        }
    }
}
