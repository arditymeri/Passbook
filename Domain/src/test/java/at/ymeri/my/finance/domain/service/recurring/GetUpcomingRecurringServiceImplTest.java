package at.ymeri.my.finance.domain.service.recurring;

import at.ymeri.my.finance.domain.api.GetBillService;
import at.ymeri.my.finance.domain.api.GetIncomeService;
import at.ymeri.my.finance.domain.api.GetRecurringSeriesService;
import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.data.common.RecurringFrequency;
import at.ymeri.my.finance.domain.data.recurring.RecurringDashboardResult;
import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesDto;
import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesStatus;
import at.ymeri.my.finance.domain.data.recurring.TransactionType;
import at.ymeri.my.finance.domain.data.recurring.UpcomingRecurringItemDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetUpcomingRecurringServiceImplTest {

    @Mock
    private GetRecurringSeriesService getRecurringSeriesService;

    @Mock
    private GetBillService getBillService;

    @Mock
    private GetIncomeService getIncomeService;

    @InjectMocks
    private GetUpcomingRecurringServiceImpl service;

    @Test
    void getDashboard_confirmedSeriesWithRecentOccurrence_predictsNextDateAndAmount() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime lastOccurrence = now.minusDays(5);
        when(getRecurringSeriesService.getAll()).thenReturn(List.of(confirmedSeries("cat-A", "netflix")));
        when(getBillService.getAll()).thenReturn(List.of(
                bill("cat-A", "Netflix", lastOccurrence.minusMonths(1), "15.99"),
                bill("cat-A", "Netflix", lastOccurrence, "15.99")));
        lenient().when(getIncomeService.getAll()).thenReturn(List.of());

        RecurringDashboardResult result = service.getDashboard();

        assertEquals(1, result.getUpcoming().size());
        UpcomingRecurringItemDto item = result.getUpcoming().get(0);
        assertEquals(lastOccurrence.plusMonths(1), item.getPredictedDate());
        assertEquals(new BigDecimal("15.99"), item.getPredictedAmount());
        assertFalse(item.isOverdue());
    }

    @Test
    void getDashboard_predictedDateInPastWithNoNewOccurrence_isOverdue() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime lastOccurrence = now.minusMonths(2);
        when(getRecurringSeriesService.getAll()).thenReturn(List.of(confirmedSeries("cat-A", "netflix")));
        when(getBillService.getAll()).thenReturn(List.of(bill("cat-A", "Netflix", lastOccurrence, "15.99")));
        lenient().when(getIncomeService.getAll()).thenReturn(List.of());

        RecurringDashboardResult result = service.getDashboard();

        assertTrue(result.getUpcoming().get(0).isOverdue());
    }

    @Test
    void getDashboard_recordingNewOccurrence_advancesPredictionAndClearsOverdue() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime newOccurrence = now.minusDays(2);
        when(getRecurringSeriesService.getAll()).thenReturn(List.of(confirmedSeries("cat-A", "netflix")));
        when(getBillService.getAll()).thenReturn(List.of(
                bill("cat-A", "Netflix", now.minusMonths(2), "15.99"),
                bill("cat-A", "Netflix", newOccurrence, "15.99")));
        lenient().when(getIncomeService.getAll()).thenReturn(List.of());

        RecurringDashboardResult result = service.getDashboard();

        UpcomingRecurringItemDto item = result.getUpcoming().get(0);
        assertEquals(newOccurrence.plusMonths(1), item.getPredictedDate());
        assertFalse(item.isOverdue());
    }

    @Test
    void getDashboard_proposedAndDismissedSeries_neverProduceUpcomingItems() {
        RecurringSeriesDto proposed = confirmedSeries("cat-A", "netflix");
        proposed.setStatus(RecurringSeriesStatus.PROPOSED);
        RecurringSeriesDto dismissed = confirmedSeries("cat-B", "spotify");
        dismissed.setStatus(RecurringSeriesStatus.DISMISSED);
        when(getRecurringSeriesService.getAll()).thenReturn(List.of(proposed, dismissed));
        lenient().when(getBillService.getAll()).thenReturn(List.of());
        lenient().when(getIncomeService.getAll()).thenReturn(List.of());

        RecurringDashboardResult result = service.getDashboard();

        assertTrue(result.getUpcoming().isEmpty());
    }

    @Test
    void getDashboard_noConfirmedSeries_returnsEmptyList() {
        when(getRecurringSeriesService.getAll()).thenReturn(List.of());

        RecurringDashboardResult result = service.getDashboard();

        assertTrue(result.getUpcoming().isEmpty());
    }

    private RecurringSeriesDto confirmedSeries(String groupKey, String description) {
        RecurringSeriesDto dto = new RecurringSeriesDto();
        dto.setId("series-1");
        dto.setTransactionType(TransactionType.BILL);
        dto.setGroupKey(groupKey);
        dto.setDescription(description);
        dto.setFrequency(RecurringFrequency.MONTHLY);
        dto.setStatus(RecurringSeriesStatus.CONFIRMED);
        dto.setCreatedAt(OffsetDateTime.now());
        return dto;
    }

    private BillDto bill(String categoryId, String description, OffsetDateTime time, String amount) {
        BillDto dto = new BillDto();
        dto.setId(java.util.UUID.randomUUID().toString());
        dto.setCategoryId(categoryId);
        dto.setDescription(description);
        dto.setTime(time);
        dto.setAmount(new BigDecimal(amount));
        return dto;
    }
}
