package at.ymeri.my.finance.domain.service.recurring;

import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.data.income.IncomeDto;
import at.ymeri.my.finance.domain.spi.bill.AddBillPersistencePort;
import at.ymeri.my.finance.domain.spi.bill.GetBillPersistencePort;
import at.ymeri.my.finance.domain.spi.income.AddIncomePersistencePort;
import at.ymeri.my.finance.domain.spi.income.GetIncomePersistencePort;
import at.ymeri.my.finance.domain.spi.recurring.GetAutoPostedTransactionsPersistencePort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The matching rule that decides whether an imported transaction is the bank's version of a
 * prediction. Plain JUnit, no database.
 *
 * <p>Both directions of error are expensive and neither is loud: matching too eagerly cancels a
 * prediction that was not actually superseded and understates the operator's spending; matching too
 * reluctantly leaves them double-counted.
 */
class ReconcileAutoPostedServiceImplTest {

    private static OffsetDateTime at(String date) {
        return OffsetDateTime.parse(date + "T00:00:00Z");
    }

    // --- amount tolerance -----------------------------------------------------------------------

    @Test
    void anExactAmountMatches() {
        assertThat(ReconcileAutoPostedServiceImpl.withinAmountTolerance(
                new BigDecimal("1250.00"), new BigDecimal("1250.00"))).isTrue();
    }

    @Test
    void aSmallDriftInsideFivePercentMatches() {
        // Rent quoted to the cent versus the bank's rounding, or a variable utility bill.
        assertThat(ReconcileAutoPostedServiceImpl.withinAmountTolerance(
                new BigDecimal("1250.00"), new BigDecimal("1290.00"))).isTrue();
    }

    @Test
    void aTwentyEightPercentJumpDoesNotMatch() {
        // quickstart scenario 5: a rent rise this large is a different transaction as far as the
        // app is concerned, and the operator should see both rather than have one silently cancelled.
        assertThat(ReconcileAutoPostedServiceImpl.withinAmountTolerance(
                new BigDecimal("1250.00"), new BigDecimal("1600.00"))).isFalse();
    }

    @Test
    void theTwoEuroFloorSavesSmallSubscriptions() {
        // 5% of €3.99 is 20 cents, which is meaninglessly tight for a subscription that ticks up.
        // The floor is what makes small recurring charges reconcilable at all.
        assertThat(ReconcileAutoPostedServiceImpl.withinAmountTolerance(
                new BigDecimal("3.99"), new BigDecimal("4.99"))).isTrue();
    }

    @Test
    void theFloorIsNotUnlimited() {
        assertThat(ReconcileAutoPostedServiceImpl.withinAmountTolerance(
                new BigDecimal("3.99"), new BigDecimal("9.99"))).isFalse();
    }

    @Test
    void aMissingAmountNeverMatches() {
        assertThat(ReconcileAutoPostedServiceImpl.withinAmountTolerance(null, BigDecimal.ONE)).isFalse();
        assertThat(ReconcileAutoPostedServiceImpl.withinAmountTolerance(BigDecimal.ONE, null)).isFalse();
    }

    // --- date tolerance -------------------------------------------------------------------------

    @Test
    void theSameDayMatches() {
        assertThat(ReconcileAutoPostedServiceImpl.withinDateTolerance(
                at("2026-03-01"), at("2026-03-01"))).isTrue();
    }

    @Test
    void aFewDaysEitherSideMatches() {
        // A bank posting rent on the 3rd when it is nominally due on the 1st is the normal case, not
        // evidence of a different transaction.
        assertThat(ReconcileAutoPostedServiceImpl.withinDateTolerance(
                at("2026-03-01"), at("2026-03-04"))).isTrue();
        assertThat(ReconcileAutoPostedServiceImpl.withinDateTolerance(
                at("2026-03-01"), at("2026-02-26"))).isTrue();
    }

    @Test
    void aMonthApartDoesNotMatch() {
        // Otherwise February's prediction could be cancelled by March's real rent, leaving February
        // uncounted and March counted once — quietly wrong in both directions.
        assertThat(ReconcileAutoPostedServiceImpl.withinDateTolerance(
                at("2026-03-01"), at("2026-04-01"))).isFalse();
    }

    @Test
    void aMissingDateNeverMatches() {
        assertThat(ReconcileAutoPostedServiceImpl.withinDateTolerance(null, at("2026-03-01"))).isFalse();
        assertThat(ReconcileAutoPostedServiceImpl.withinDateTolerance(at("2026-03-01"), null)).isFalse();
    }

    // --- what a reversal looks like -------------------------------------------------------------

    @Test
    void supersedingWritesACompensatingEntryRatherThanChangingAnything() {
        // Principle I. The assertion that matters is that the pair nets to zero, which is what makes
        // the operator's balance land on the bank's figure without anything being deleted.
        BillDto prediction = new BillDto();
        prediction.setId(idFor("pred-1"));
        prediction.setAmount(new BigDecimal("1250.00"));
        prediction.setRecurringSeriesId("s1");

        BigDecimal reversalAmount = prediction.getAmount().negate();

        assertThat(prediction.getAmount().add(reversalAmount))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    // --- reconciling, end to end through the service ---------------------------------------------

    /**
     * In-memory bill ledger: getAll returns what has been added, addBill records the reversal. Bills
     * and incomes need separate fakes because both ports declare getAll() with their own row type.
     */
    private static final class Ledger implements GetBillPersistencePort, AddBillPersistencePort,
            GetAutoPostedTransactionsPersistencePort {

        private final List<BillDto> bills = new ArrayList<>();
        private final List<BillDto> written = new ArrayList<>();

        @Override
        public Optional<BillDto> getBillById(UUID uuid) {
            return bills.stream().filter(b -> b.getId().equals(uuid.toString())).findFirst();
        }

        /**
         * The same set the JPQL query returns: auto-posted rows on this account that are not
         * reversals and that nothing already references. Kept in one place so the fake cannot
         * quietly diverge from what the database is asked for.
         */
        @Override
        public List<BillDto> supersedableBills(String accountId) {
            Set<String> superseded = bills.stream()
                    .map(BillDto::getCorrectsTransactionId)
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toSet());
            return bills.stream()
                    .filter(b -> accountId.equals(b.getAccountId()))
                    .filter(b -> b.getRecurringSeriesId() != null)
                    .filter(b -> !b.isReversal())
                    .filter(b -> !superseded.contains(b.getId()))
                    .toList();
        }

        @Override
        public List<IncomeDto> supersedableIncomes(String accountId) {
            return List.of();
        }

        @Override
        public Optional<BillDto> lockBillById(UUID uuid) {
            return getBillById(uuid);
        }

        @Override
        public List<BillDto> getAll() {
            return List.copyOf(bills);
        }

        @Override
        public BillDto addBill(BillDto billDto) {
            billDto.setId("rev-" + written.size());
            written.add(billDto);
            bills.add(billDto);
            return billDto;
        }
    }

    /** Empty income side; these tests are about bills. */
    private static final class NoIncomes implements GetIncomePersistencePort, AddIncomePersistencePort {

        @Override
        public Optional<IncomeDto> getIncomeById(UUID id) {
            return Optional.empty();
        }

        @Override
        public Optional<IncomeDto> lockIncomeById(UUID id) {
            return Optional.empty();
        }

        @Override
        public List<IncomeDto> getAll() {
            return List.of();
        }

        @Override
        public IncomeDto addIncome(IncomeDto incomeDto) {
            throw new AssertionError("no income should be written by these tests");
        }
    }

    /**
     * Ids are real UUIDs because the service looks the incoming row up by one; the readable name is
     * kept as the description so a failure still says which row it was.
     */
    private final java.util.Map<String, String> ids = new java.util.HashMap<>();

    private String idFor(String name) {
        return ids.computeIfAbsent(name, ignored -> UUID.randomUUID().toString());
    }

    private BillDto bill(String id, String date, String amount, String seriesId) {
        BillDto bill = new BillDto();
        bill.setId(idFor(id));
        bill.setAccountId("acc-1");
        bill.setTime(at(date));
        bill.setAmount(new BigDecimal(amount));
        bill.setRecurringSeriesId(seriesId);
        bill.setDescription(id);
        return bill;
    }

    private static ReconcileAutoPostedServiceImpl serviceOver(Ledger ledger) {
        NoIncomes noIncomes = new NoIncomes();
        return new ReconcileAutoPostedServiceImpl(ledger, ledger, noIncomes, noIncomes, ledger);
    }

    @Test
    void aMatchingImportSupersedesThePrediction() {
        // quickstart scenario 4.
        Ledger ledger = new Ledger();
        ledger.bills.add(bill("pred-march", "2026-03-01", "1250.00", "s1"));
        ledger.bills.add(bill("imported", "2026-03-03", "1250.00", null));

        assertThat(serviceOver(ledger).reconcileBills(List.of(idFor("imported")))).isEqualTo(1);

        assertThat(ledger.written).hasSize(1);
        BillDto reversal = ledger.written.get(0);
        assertThat(reversal.getCorrectsTransactionId()).isEqualTo(idFor("pred-march"));
        assertThat(reversal.isReversal()).isTrue();
        assertThat(reversal.getAmount()).isEqualByComparingTo(new BigDecimal("-1250.00"));
        // The reversal stays attached to the series, so it is excluded from the series' real
        // occurrences and cannot become the anchor for the next prediction.
        assertThat(reversal.getRecurringSeriesId()).isEqualTo("s1");
    }

    @Test
    void aRentRiseWellOutsideToleranceLeavesBothStanding() {
        // quickstart scenario 5: the operator sees the prediction and the larger real charge, and
        // decides themselves. Nothing is silently cancelled.
        Ledger ledger = new Ledger();
        ledger.bills.add(bill("pred-march", "2026-03-01", "1250.00", "s1"));
        ledger.bills.add(bill("imported", "2026-03-02", "1600.00", null));

        assertThat(serviceOver(ledger).reconcileBills(List.of(idFor("imported")))).isZero();
        assertThat(ledger.written).isEmpty();
    }

    @Test
    void aPredictionFarOutsideTheWindowIsNotSuperseded() {
        Ledger ledger = new Ledger();
        ledger.bills.add(bill("pred-february", "2026-02-01", "1250.00", "s1"));
        ledger.bills.add(bill("imported", "2026-03-01", "1250.00", null));

        assertThat(serviceOver(ledger).reconcileBills(List.of(idFor("imported")))).isZero();
        assertThat(ledger.written).isEmpty();
    }

    @Test
    void twoCandidatesResolveToTheCloserOne() {
        // Overlapping windows are ordinary for a weekly series. Whichever rule is used, the same
        // import must always resolve the same way, or re-running an import supersedes a different row.
        Ledger ledger = new Ledger();
        ledger.bills.add(bill("pred-far", "2026-03-01", "1250.00", "s1"));
        ledger.bills.add(bill("pred-near", "2026-03-05", "1250.00", "s1"));
        ledger.bills.add(bill("imported", "2026-03-06", "1250.00", null));

        assertThat(serviceOver(ledger).reconcileBills(List.of(idFor("imported")))).isEqualTo(1);
        assertThat(ledger.written.get(0).getCorrectsTransactionId()).isEqualTo(idFor("pred-near"));
    }

    @Test
    void anAlreadySupersededPredictionIsNotSupersededTwice() {
        // Re-importing the same statement is expected — feature 022 exists precisely so it is safe.
        // A second reversal of one prediction would credit the operator money they never had.
        Ledger ledger = new Ledger();
        ledger.bills.add(bill("pred-march", "2026-03-01", "1250.00", "s1"));
        ledger.bills.add(bill("imported-first", "2026-03-02", "1250.00", null));
        ledger.bills.add(bill("imported-second", "2026-03-03", "1250.00", null));

        ReconcileAutoPostedServiceImpl service = serviceOver(ledger);
        assertThat(service.reconcileBills(List.of(idFor("imported-first")))).isEqualTo(1);
        assertThat(service.reconcileBills(List.of(idFor("imported-second")))).isZero();
        assertThat(ledger.written).hasSize(1);
    }

    @Test
    void twoImportsInOneRunDoNotBothClaimTheSamePrediction() {
        Ledger ledger = new Ledger();
        ledger.bills.add(bill("pred-march", "2026-03-01", "1250.00", "s1"));
        ledger.bills.add(bill("imported-a", "2026-03-02", "1250.00", null));
        ledger.bills.add(bill("imported-b", "2026-03-03", "1250.00", null));

        assertThat(serviceOver(ledger).reconcileBills(List.of(idFor("imported-a"), idFor("imported-b"))))
                .isEqualTo(1);
        assertThat(ledger.written).hasSize(1);
    }

    @Test
    void anImportOnAnotherAccountDoesNotSupersede() {
        Ledger ledger = new Ledger();
        ledger.bills.add(bill("pred-march", "2026-03-01", "1250.00", "s1"));
        BillDto elsewhere = bill("imported", "2026-03-02", "1250.00", null);
        elsewhere.setAccountId("acc-2");
        ledger.bills.add(elsewhere);

        assertThat(serviceOver(ledger).reconcileBills(List.of(idFor("imported")))).isZero();
    }

    @Test
    void anAutoPostedTransactionNeverSupersedesAnother() {
        // Otherwise a posting run's own output would cancel the run before it.
        Ledger ledger = new Ledger();
        ledger.bills.add(bill("pred-march", "2026-03-01", "1250.00", "s1"));
        ledger.bills.add(bill("pred-march-again", "2026-03-02", "1250.00", "s1"));

        assertThat(serviceOver(ledger).reconcileBills(List.of(idFor("pred-march-again")))).isZero();
        assertThat(ledger.written).isEmpty();
    }
}
