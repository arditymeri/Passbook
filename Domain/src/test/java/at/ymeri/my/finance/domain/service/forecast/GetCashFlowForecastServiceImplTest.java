package at.ymeri.my.finance.domain.service.forecast;

import at.ymeri.my.finance.domain.api.GetAccountService;
import at.ymeri.my.finance.domain.api.GetBillService;
import at.ymeri.my.finance.domain.api.GetIncomeService;
import at.ymeri.my.finance.domain.api.GetRecurringSeriesService;
import at.ymeri.my.finance.domain.data.account.AccountDto;
import at.ymeri.my.finance.domain.data.account.AccountType;
import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.data.common.RecurringFrequency;
import at.ymeri.my.finance.domain.data.forecast.AccountForecastDto;
import at.ymeri.my.finance.domain.data.forecast.CashFlowForecastResult;
import at.ymeri.my.finance.domain.data.forecast.ForecastEntryDto;
import at.ymeri.my.finance.domain.data.income.IncomeDto;
import at.ymeri.my.finance.domain.data.income.IncomeSource;
import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesDto;
import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesStatus;
import at.ymeri.my.finance.domain.data.recurring.TransactionType;
import at.ymeri.my.finance.domain.service.recurring.RecurringSeriesMembers;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetCashFlowForecastServiceImplTest {

    private static final String ACCOUNT_ID = "acc-1";
    private static final String CATEGORY_ID = "cat-rent";

    @Mock
    private GetAccountService getAccountService;

    @Mock
    private GetRecurringSeriesService getRecurringSeriesService;

    @Mock
    private GetBillService getBillService;

    @Mock
    private GetIncomeService getIncomeService;

    private GetCashFlowForecastServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new GetCashFlowForecastServiceImpl(
                getAccountService,
                getRecurringSeriesService,
                new RecurringSeriesMembers(getBillService, getIncomeService));
        lenient().when(getBillService.getAll()).thenReturn(List.of());
        lenient().when(getIncomeService.getAll()).thenReturn(List.of());
        lenient().when(getRecurringSeriesService.getAll()).thenReturn(List.of());
    }

    @Test
    void forecast_confirmedBillDrivesAccountNegative_isAtRisk() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        when(getAccountService.getAll()).thenReturn(List.of(account("Checking", "100.00")));
        when(getRecurringSeriesService.getAll()).thenReturn(List.of(confirmedBillSeries(RecurringFrequency.WEEKLY)));
        when(getBillService.getAll()).thenReturn(List.of(
                bill(now.minusWeeks(1), "150.00")));

        CashFlowForecastResult result = service.forecast(4);

        AccountForecastDto forecast = result.getAccounts().get(0);
        assertTrue(forecast.isAtRisk());
        assertFalse(forecast.getTimeline().isEmpty());
    }

    @Test
    void forecast_incomeAndBillsStayPositive_isNotAtRisk() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        when(getAccountService.getAll()).thenReturn(List.of(account("Checking", "1000.00")));
        RecurringSeriesDto billSeries = confirmedBillSeries(RecurringFrequency.MONTHLY);
        RecurringSeriesDto incomeSeries = confirmedIncomeSeries(RecurringFrequency.MONTHLY);
        when(getRecurringSeriesService.getAll()).thenReturn(List.of(billSeries, incomeSeries));
        when(getBillService.getAll()).thenReturn(List.of(bill(now.minusDays(20), "150.00")));
        when(getIncomeService.getAll()).thenReturn(List.of(income(now.minusDays(15), "1200.00")));

        CashFlowForecastResult result = service.forecast(4);

        assertFalse(result.getAccounts().get(0).isAtRisk());
    }

    @Test
    void forecast_alreadyNegativeAccount_isAtRiskFromTheStart() {
        when(getAccountService.getAll()).thenReturn(List.of(account("Checking", "-50.00")));

        CashFlowForecastResult result = service.forecast(4);

        AccountForecastDto forecast = result.getAccounts().get(0);
        assertTrue(forecast.isAtRisk());
        assertTrue(forecast.getTimeline().isEmpty());
    }

    @Test
    void forecast_weeklySeriesRecursMultipleTimesInWindow_everyOccurrencePresent() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        when(getAccountService.getAll()).thenReturn(List.of(account("Checking", "1000.00")));
        when(getRecurringSeriesService.getAll()).thenReturn(List.of(confirmedBillSeries(RecurringFrequency.WEEKLY)));
        when(getBillService.getAll()).thenReturn(List.of(bill(now, "20.00")));

        CashFlowForecastResult result = service.forecast(4);

        List<ForecastEntryDto> timeline = result.getAccounts().get(0).getTimeline();
        assertEquals(4, timeline.size());
        for (int i = 1; i < timeline.size(); i++) {
            assertTrue(timeline.get(i).getDate().isAfter(timeline.get(i - 1).getDate()));
        }
    }

    @Test
    void forecast_correctedAmount_usesCorrectedValueNotOriginal() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        when(getAccountService.getAll()).thenReturn(List.of(account("Checking", "1000.00")));
        when(getRecurringSeriesService.getAll()).thenReturn(List.of(confirmedBillSeries(RecurringFrequency.MONTHLY)));
        // GetBillService.getAll() is already correction-aware — simulate it returning the
        // corrected amount as the current value for this occurrence.
        when(getBillService.getAll()).thenReturn(List.of(bill(now.minusDays(20), "199.00")));

        CashFlowForecastResult result = service.forecast(4);

        ForecastEntryDto entry = result.getAccounts().get(0).getTimeline().get(0);
        assertEquals(new BigDecimal("199.00"), entry.getAmount());
    }

    @Test
    void forecast_overdueSeries_firstEntryDueNowNotOmitted() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        when(getAccountService.getAll()).thenReturn(List.of(account("Checking", "1000.00")));
        when(getRecurringSeriesService.getAll()).thenReturn(List.of(confirmedBillSeries(RecurringFrequency.MONTHLY)));
        // Last occurrence 2 months ago — predicted next date (1 month later) has already passed.
        when(getBillService.getAll()).thenReturn(List.of(bill(now.minusMonths(2), "50.00")));

        CashFlowForecastResult result = service.forecast(4);

        assertFalse(result.getAccounts().get(0).getTimeline().isEmpty());
    }

    @Test
    void forecast_twoSeriesSameDate_bothReflected() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        when(getAccountService.getAll()).thenReturn(List.of(account("Checking", "1000.00")));
        RecurringSeriesDto rentSeries = confirmedBillSeries(RecurringFrequency.MONTHLY);
        RecurringSeriesDto internetSeries = confirmedBillSeries(RecurringFrequency.MONTHLY);
        internetSeries.setId("series-2");
        internetSeries.setGroupKey("cat-internet");
        internetSeries.setDescription("internet");
        when(getRecurringSeriesService.getAll()).thenReturn(List.of(rentSeries, internetSeries));

        BillDto rentBill = bill(now.minusDays(20), "100.00");
        BillDto internetBill = bill("cat-internet", "internet", now.minusDays(20), "40.00");
        when(getBillService.getAll()).thenReturn(List.of(rentBill, internetBill));

        CashFlowForecastResult result = service.forecast(4);

        assertEquals(2, result.getAccounts().get(0).getTimeline().size());
    }

    @Test
    void forecast_noConfirmedSeries_flatForecastNoWarning() {
        when(getAccountService.getAll()).thenReturn(List.of(account("Checking", "500.00")));

        CashFlowForecastResult result = service.forecast(4);

        AccountForecastDto forecast = result.getAccounts().get(0);
        assertTrue(forecast.getTimeline().isEmpty());
        assertFalse(forecast.isAtRisk());
        assertEquals(new BigDecimal("500.00"), forecast.getCurrentBalance());
    }

    private AccountDto account(String name, String balance) {
        AccountDto dto = new AccountDto();
        dto.setId(ACCOUNT_ID);
        dto.setName(name);
        dto.setType(AccountType.CHECKING);
        dto.setBalance(new BigDecimal(balance));
        return dto;
    }

    private RecurringSeriesDto confirmedBillSeries(RecurringFrequency frequency) {
        RecurringSeriesDto dto = new RecurringSeriesDto();
        dto.setId("series-1");
        dto.setTransactionType(TransactionType.BILL);
        dto.setGroupKey(CATEGORY_ID);
        dto.setDescription("rent");
        dto.setFrequency(frequency);
        dto.setStatus(RecurringSeriesStatus.CONFIRMED);
        dto.setCreatedAt(OffsetDateTime.now());
        return dto;
    }

    private BillDto bill(OffsetDateTime time, String amount) {
        return bill(CATEGORY_ID, "rent", time, amount);
    }

    private BillDto bill(String categoryId, String description, OffsetDateTime time, String amount) {
        BillDto dto = new BillDto();
        dto.setId(UUID.randomUUID().toString());
        dto.setCategoryId(categoryId);
        dto.setDescription(description);
        dto.setTime(time);
        dto.setAmount(new BigDecimal(amount));
        dto.setAccountId(ACCOUNT_ID);
        return dto;
    }

    private RecurringSeriesDto confirmedIncomeSeries(RecurringFrequency frequency) {
        RecurringSeriesDto dto = new RecurringSeriesDto();
        dto.setId("series-income");
        dto.setTransactionType(TransactionType.INCOME);
        dto.setGroupKey(IncomeSource.SALARY.name());
        dto.setDescription("salary");
        dto.setFrequency(frequency);
        dto.setStatus(RecurringSeriesStatus.CONFIRMED);
        dto.setCreatedAt(OffsetDateTime.now());
        return dto;
    }

    private IncomeDto income(OffsetDateTime time, String amount) {
        IncomeDto dto = new IncomeDto();
        dto.setId(UUID.randomUUID().toString());
        dto.setSource(IncomeSource.SALARY);
        dto.setDescription("salary");
        dto.setTime(time);
        dto.setAmount(new BigDecimal(amount));
        dto.setAccountId(ACCOUNT_ID);
        return dto;
    }
}
