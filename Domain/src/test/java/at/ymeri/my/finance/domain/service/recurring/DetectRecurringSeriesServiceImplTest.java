package at.ymeri.my.finance.domain.service.recurring;

import at.ymeri.my.finance.domain.api.GetBillService;
import at.ymeri.my.finance.domain.api.GetIncomeService;
import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.data.common.RecurringFrequency;
import at.ymeri.my.finance.domain.data.income.IncomeDto;
import at.ymeri.my.finance.domain.data.income.IncomeSource;
import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesDto;
import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesStatus;
import at.ymeri.my.finance.domain.data.recurring.TransactionType;
import at.ymeri.my.finance.domain.spi.recurring.AddRecurringSeriesPersistencePort;
import at.ymeri.my.finance.domain.spi.recurring.GetRecurringSeriesPersistencePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DetectRecurringSeriesServiceImplTest {

    @Mock
    private GetBillService getBillService;

    @Mock
    private GetIncomeService getIncomeService;

    @Mock
    private GetRecurringSeriesPersistencePort getRecurringSeriesPersistencePort;

    @Mock
    private AddRecurringSeriesPersistencePort addRecurringSeriesPersistencePort;

    @InjectMocks
    private DetectRecurringSeriesServiceImpl service;

    @Test
    void detect_threeMatchingBills_proposesASeries() {
        when(getBillService.getAll()).thenReturn(List.of(
                bill("cat-A", "Netflix", "2026-01-15", "15.99", false),
                bill("cat-A", "Netflix", "2026-02-15", "15.99", false),
                bill("cat-A", "Netflix", "2026-03-15", "15.99", false)));
        when(getIncomeService.getAll()).thenReturn(List.of());
        when(getRecurringSeriesPersistencePort.findByKey(any(), any(), any())).thenReturn(Optional.empty());
        when(getRecurringSeriesPersistencePort.getAll()).thenReturn(List.of(proposed("cat-A", "netflix")));
        stubAdd();

        List<RecurringSeriesDto> result = service.detect();

        verify(addRecurringSeriesPersistencePort).add(argThatMatchesProposal(TransactionType.BILL, "cat-A", "netflix", RecurringFrequency.MONTHLY));
        assertEquals(1, result.size());
    }

    @Test
    void detect_twoMatchingBillsOneFlaggedRecurring_proposesASeries() {
        when(getBillService.getAll()).thenReturn(List.of(
                bill("cat-A", "Netflix", "2026-01-15", "15.99", true),
                bill("cat-A", "Netflix", "2026-02-15", "15.99", false)));
        when(getIncomeService.getAll()).thenReturn(List.of());
        when(getRecurringSeriesPersistencePort.findByKey(any(), any(), any())).thenReturn(Optional.empty());
        when(getRecurringSeriesPersistencePort.getAll()).thenReturn(List.of());
        stubAdd();

        service.detect();

        verify(addRecurringSeriesPersistencePort).add(argThatMatchesProposal(TransactionType.BILL, "cat-A", "netflix", RecurringFrequency.MONTHLY));
    }

    @Test
    void detect_twoMatchingBillsNeitherFlagged_doesNotPropose() {
        when(getBillService.getAll()).thenReturn(List.of(
                bill("cat-A", "Netflix", "2026-01-15", "15.99", false),
                bill("cat-A", "Netflix", "2026-02-15", "15.99", false)));
        when(getIncomeService.getAll()).thenReturn(List.of());

        service.detect();

        verifyNoInteractions(addRecurringSeriesPersistencePort);
    }

    @Test
    void detect_categoryMismatch_neverGrouped() {
        when(getBillService.getAll()).thenReturn(List.of(
                bill("cat-A", "Netflix", "2026-01-15", "15.99", false),
                bill("cat-B", "Netflix", "2026-02-15", "15.99", false),
                bill("cat-A", "Netflix", "2026-03-15", "15.99", false)));
        when(getIncomeService.getAll()).thenReturn(List.of());

        service.detect();

        // only two cat-A occurrences, neither flagged -> below the 3-occurrence threshold
        verifyNoInteractions(addRecurringSeriesPersistencePort);
    }

    @Test
    void detect_amountOutsideTolerance_breaksTheRun() {
        when(getBillService.getAll()).thenReturn(List.of(
                bill("cat-A", "Netflix", "2026-01-15", "15.99", false),
                bill("cat-A", "Netflix", "2026-02-15", "40.00", false), // breaks the run
                bill("cat-A", "Netflix", "2026-03-15", "15.99", false)));
        when(getIncomeService.getAll()).thenReturn(List.of());

        service.detect();

        // scanning backward from March, the Feb->Mar amount jump breaks the run immediately,
        // leaving a run length of 1 — never reaching the 3-occurrence threshold
        verifyNoInteractions(addRecurringSeriesPersistencePort);
    }

    @Test
    void detect_alreadyCoveredKey_neverReProposed() {
        when(getBillService.getAll()).thenReturn(List.of(
                bill("cat-A", "Netflix", "2026-01-15", "15.99", false),
                bill("cat-A", "Netflix", "2026-02-15", "15.99", false),
                bill("cat-A", "Netflix", "2026-03-15", "15.99", false)));
        when(getIncomeService.getAll()).thenReturn(List.of());
        when(getRecurringSeriesPersistencePort.findByKey(TransactionType.BILL, "cat-A", "netflix"))
                .thenReturn(Optional.of(dismissed("cat-A", "netflix")));

        service.detect();

        verifyNoInteractions(addRecurringSeriesPersistencePort);
    }

    @Test
    void detect_billsAndIncomes_neverMergedIntoSameSeries() {
        when(getBillService.getAll()).thenReturn(List.of(
                bill("cat-A", "Acme Corp", "2026-01-15", "500.00", false),
                bill("cat-A", "Acme Corp", "2026-02-15", "500.00", false),
                bill("cat-A", "Acme Corp", "2026-03-15", "500.00", false)));
        when(getIncomeService.getAll()).thenReturn(List.of(
                income(IncomeSource.SALARY, "Acme Corp", "2026-01-31", "500.00", false),
                income(IncomeSource.SALARY, "Acme Corp", "2026-02-28", "500.00", false),
                income(IncomeSource.SALARY, "Acme Corp", "2026-03-31", "500.00", false)));
        when(getRecurringSeriesPersistencePort.findByKey(any(), any(), any())).thenReturn(Optional.empty());
        when(getRecurringSeriesPersistencePort.getAll()).thenReturn(List.of());
        stubAdd();

        service.detect();

        verify(addRecurringSeriesPersistencePort).add(argThatMatchesProposal(TransactionType.BILL, "cat-A", "acme corp", RecurringFrequency.MONTHLY));
        verify(addRecurringSeriesPersistencePort).add(argThatMatchesProposal(TransactionType.INCOME, "SALARY", "acme corp", RecurringFrequency.MONTHLY));
    }

    private void stubAdd() {
        lenient().when(addRecurringSeriesPersistencePort.add(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static RecurringSeriesDto argThatMatchesProposal(TransactionType type, String groupKey, String description, RecurringFrequency frequency) {
        return org.mockito.ArgumentMatchers.argThat(dto ->
                dto.getTransactionType() == type
                        && dto.getGroupKey().equals(groupKey)
                        && dto.getDescription().equals(description)
                        && dto.getFrequency() == frequency
                        && dto.getStatus() == RecurringSeriesStatus.PROPOSED);
    }

    private BillDto bill(String categoryId, String description, String isoDate, String amount, boolean recurring) {
        BillDto dto = new BillDto();
        dto.setCategoryId(categoryId);
        dto.setDescription(description);
        dto.setAmount(new BigDecimal(amount));
        dto.setTime(OffsetDateTime.parse(isoDate + "T00:00:00Z"));
        dto.setRecurring(recurring);
        return dto;
    }

    private IncomeDto income(IncomeSource source, String description, String isoDate, String amount, boolean recurring) {
        IncomeDto dto = new IncomeDto();
        dto.setSource(source);
        dto.setDescription(description);
        dto.setAmount(new BigDecimal(amount));
        dto.setTime(OffsetDateTime.parse(isoDate + "T00:00:00Z"));
        dto.setRecurring(recurring);
        return dto;
    }

    private RecurringSeriesDto proposed(String groupKey, String description) {
        RecurringSeriesDto dto = new RecurringSeriesDto();
        dto.setId("series-1");
        dto.setTransactionType(TransactionType.BILL);
        dto.setGroupKey(groupKey);
        dto.setDescription(description);
        dto.setFrequency(RecurringFrequency.MONTHLY);
        dto.setStatus(RecurringSeriesStatus.PROPOSED);
        dto.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return dto;
    }

    private RecurringSeriesDto dismissed(String groupKey, String description) {
        RecurringSeriesDto dto = proposed(groupKey, description);
        dto.setStatus(RecurringSeriesStatus.DISMISSED);
        return dto;
    }
}
