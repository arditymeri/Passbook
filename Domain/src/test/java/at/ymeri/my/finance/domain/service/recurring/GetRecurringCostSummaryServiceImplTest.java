package at.ymeri.my.finance.domain.service.recurring;

import at.ymeri.my.finance.domain.api.GetBillService;
import at.ymeri.my.finance.domain.api.GetIncomeService;
import at.ymeri.my.finance.domain.api.GetRecurringSeriesService;
import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.data.common.RecurringFrequency;
import at.ymeri.my.finance.domain.data.recurring.RecurringCostSummaryItemDto;
import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesDto;
import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesStatus;
import at.ymeri.my.finance.domain.data.recurring.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetRecurringCostSummaryServiceImplTest {

    @Mock
    private GetRecurringSeriesService getRecurringSeriesService;

    @Mock
    private GetBillService getBillService;

    @Mock
    private GetIncomeService getIncomeService;

    private GetRecurringCostSummaryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new GetRecurringCostSummaryServiceImpl(
                getRecurringSeriesService,
                new RecurringSeriesMembers(getBillService, getIncomeService));
        lenient().when(getIncomeService.getAll()).thenReturn(List.of());
    }

    @Test
    void getSummary_monthlySeries_monthlyEquivalentEqualsRawAmount() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        when(getRecurringSeriesService.getAll()).thenReturn(List.of(confirmedSeries("cat-A", "rent", RecurringFrequency.MONTHLY)));
        when(getBillService.getAll()).thenReturn(List.of(bill("cat-A", "Rent", now, "900.00")));

        RecurringCostSummaryItemDto item = onlyItem();

        assertEquals(new BigDecimal("900.00"), item.getMonthlyEquivalentAmount());
    }

    @Test
    void getSummary_weeklySeries_monthlyEquivalentScalesUp() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        when(getRecurringSeriesService.getAll()).thenReturn(List.of(confirmedSeries("cat-A", "cleaner", RecurringFrequency.WEEKLY)));
        when(getBillService.getAll()).thenReturn(List.of(bill("cat-A", "Cleaner", now, "20.00")));

        RecurringCostSummaryItemDto item = onlyItem();

        // 20.00 * 4.348 = 86.96
        assertEquals(new BigDecimal("86.96"), item.getMonthlyEquivalentAmount());
    }

    @Test
    void getSummary_dailySeries_monthlyEquivalentScalesUp() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        when(getRecurringSeriesService.getAll()).thenReturn(List.of(confirmedSeries("cat-A", "parking", RecurringFrequency.DAILY)));
        when(getBillService.getAll()).thenReturn(List.of(bill("cat-A", "Parking", now, "5.00")));

        RecurringCostSummaryItemDto item = onlyItem();

        // 5.00 * 30.44 = 152.20
        assertEquals(new BigDecimal("152.20"), item.getMonthlyEquivalentAmount());
    }

    @Test
    void getSummary_yearlySeries_monthlyEquivalentScalesDown() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        when(getRecurringSeriesService.getAll()).thenReturn(List.of(confirmedSeries("cat-A", "insurance", RecurringFrequency.YEARLY)));
        when(getBillService.getAll()).thenReturn(List.of(bill("cat-A", "Insurance", now, "600.00")));

        RecurringCostSummaryItemDto item = onlyItem();

        // 600.00 / 12 = 50.00
        assertEquals(new BigDecimal("50.00"), item.getMonthlyEquivalentAmount());
    }

    @Test
    void getSummary_latestAmountHigherThanOriginalBeyondTolerance_flagsPriceIncrease() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        when(getRecurringSeriesService.getAll()).thenReturn(List.of(confirmedSeries("cat-A", "gym", RecurringFrequency.MONTHLY)));
        when(getBillService.getAll()).thenReturn(List.of(
                bill("cat-A", "Gym", now.minusMonths(2), "25.00"),
                bill("cat-A", "Gym", now.minusMonths(1), "25.00"),
                bill("cat-A", "Gym", now, "32.00")));

        RecurringCostSummaryItemDto item = onlyItem();

        assertTrue(item.isPriceIncreased());
        assertEquals(new BigDecimal("25.00"), item.getOriginalAmount());
        assertEquals(new BigDecimal("7.00"), item.getIncreaseAmount());
    }

    @Test
    void getSummary_latestAmountWithinTolerance_doesNotFlagPriceIncrease() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        when(getRecurringSeriesService.getAll()).thenReturn(List.of(confirmedSeries("cat-A", "netflix", RecurringFrequency.MONTHLY)));
        when(getBillService.getAll()).thenReturn(List.of(
                bill("cat-A", "Netflix", now.minusMonths(1), "15.99"),
                bill("cat-A", "Netflix", now, "16.50")));

        RecurringCostSummaryItemDto item = onlyItem();

        assertFalse(item.isPriceIncreased());
        assertNull(item.getIncreaseAmount());
    }

    @Test
    void getSummary_latestAmountLowerThanOriginal_doesNotFlagPriceIncrease() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        when(getRecurringSeriesService.getAll()).thenReturn(List.of(confirmedSeries("cat-A", "gym", RecurringFrequency.MONTHLY)));
        when(getBillService.getAll()).thenReturn(List.of(
                bill("cat-A", "Gym", now.minusMonths(1), "40.00"),
                bill("cat-A", "Gym", now, "25.00")));

        RecurringCostSummaryItemDto item = onlyItem();

        assertFalse(item.isPriceIncreased());
        assertNull(item.getIncreaseAmount());
    }

    @Test
    void getSummary_proposedSeries_excludedEntirely() {
        RecurringSeriesDto proposed = confirmedSeries("cat-A", "netflix", RecurringFrequency.MONTHLY);
        proposed.setStatus(RecurringSeriesStatus.PROPOSED);
        when(getRecurringSeriesService.getAll()).thenReturn(List.of(proposed));
        lenient().when(getBillService.getAll()).thenReturn(List.of());

        List<RecurringCostSummaryItemDto> summary = service.getSummary();

        assertTrue(summary.isEmpty());
    }

    @Test
    void getSummary_confirmedSeriesWithNoMembers_skippedDefensively() {
        when(getRecurringSeriesService.getAll()).thenReturn(List.of(confirmedSeries("cat-A", "netflix", RecurringFrequency.MONTHLY)));
        when(getBillService.getAll()).thenReturn(List.of());

        List<RecurringCostSummaryItemDto> summary = service.getSummary();

        assertTrue(summary.isEmpty());
    }

    private RecurringCostSummaryItemDto onlyItem() {
        List<RecurringCostSummaryItemDto> summary = service.getSummary();
        assertEquals(1, summary.size());
        return summary.get(0);
    }

    private RecurringSeriesDto confirmedSeries(String groupKey, String description, RecurringFrequency frequency) {
        RecurringSeriesDto dto = new RecurringSeriesDto();
        dto.setId("series-1");
        dto.setTransactionType(TransactionType.BILL);
        dto.setGroupKey(groupKey);
        dto.setDescription(description);
        dto.setFrequency(frequency);
        dto.setStatus(RecurringSeriesStatus.CONFIRMED);
        dto.setCreatedAt(OffsetDateTime.now());
        return dto;
    }

    private BillDto bill(String categoryId, String description, OffsetDateTime time, String amount) {
        BillDto dto = new BillDto();
        dto.setId(UUID.randomUUID().toString());
        dto.setCategoryId(categoryId);
        dto.setDescription(description);
        dto.setTime(time);
        dto.setAmount(new BigDecimal(amount));
        return dto;
    }
}
