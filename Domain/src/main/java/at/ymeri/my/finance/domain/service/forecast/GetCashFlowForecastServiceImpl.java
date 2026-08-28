package at.ymeri.my.finance.domain.service.forecast;

import at.ymeri.my.finance.domain.api.GetAccountService;
import at.ymeri.my.finance.domain.api.GetCashFlowForecastService;
import at.ymeri.my.finance.domain.api.GetRecurringSeriesService;
import at.ymeri.my.finance.domain.data.account.AccountDto;
import at.ymeri.my.finance.domain.data.forecast.AccountForecastDto;
import at.ymeri.my.finance.domain.data.forecast.CashFlowForecastResult;
import at.ymeri.my.finance.domain.data.forecast.ForecastEntryDto;
import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesDto;
import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesStatus;
import at.ymeri.my.finance.domain.data.recurring.TransactionType;
import at.ymeri.my.finance.domain.service.recurring.RecurringMatching;
import at.ymeri.my.finance.domain.service.recurring.RecurringSeriesMembers;
import at.ymeri.my.finance.domain.service.recurring.RecurringSeriesMembers.MemberOccurrence;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Projects each account's balance forward through a near-future window using every CONFIRMED
 * recurring series' predicted occurrences within that window — read-only, nothing is cached or
 * stored (Constitution Principle III); recomputed fresh from current account balances and
 * correction-aware transaction history on every call.
 */
@Service
public class GetCashFlowForecastServiceImpl implements GetCashFlowForecastService {

    private final GetAccountService getAccountService;
    private final GetRecurringSeriesService getRecurringSeriesService;
    private final RecurringSeriesMembers recurringSeriesMembers;

    public GetCashFlowForecastServiceImpl(GetAccountService getAccountService,
                                           GetRecurringSeriesService getRecurringSeriesService,
                                           RecurringSeriesMembers recurringSeriesMembers) {
        this.getAccountService = getAccountService;
        this.getRecurringSeriesService = getRecurringSeriesService;
        this.recurringSeriesMembers = recurringSeriesMembers;
    }

    @Override
    public CashFlowForecastResult forecast(int windowWeeks) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime windowEnd = now.plusWeeks(windowWeeks);

        Map<String, List<ForecastEntryDto>> entriesByAccountId = entriesByAccountId(now, windowEnd);

        List<AccountForecastDto> accountForecasts = new ArrayList<>();
        for (AccountDto account : getAccountService.getAll()) {
            accountForecasts.add(forecastFor(account, windowWeeks, entriesByAccountId));
        }

        CashFlowForecastResult result = new CashFlowForecastResult();
        result.setAccounts(accountForecasts);
        return result;
    }

    private Map<String, List<ForecastEntryDto>> entriesByAccountId(OffsetDateTime now, OffsetDateTime windowEnd) {
        List<RecurringSeriesDto> confirmedSeries = getRecurringSeriesService.getAll().stream()
                .filter(s -> s.getStatus() == RecurringSeriesStatus.CONFIRMED)
                .toList();

        Map<String, List<ForecastEntryDto>> entriesByAccountId = new HashMap<>();
        for (RecurringSeriesDto series : confirmedSeries) {
            List<MemberOccurrence> members = recurringSeriesMembers.membersOf(series);
            if (members.isEmpty()) {
                continue;
            }
            MemberOccurrence latest = members.get(members.size() - 1);
            if (latest.accountId() == null) {
                continue;
            }

            OffsetDateTime predictedNext = RecurringMatching.predictNextDate(latest.time(), series.getFrequency());
            boolean overdue = predictedNext.isBefore(now);
            List<OffsetDateTime> occurrenceDates = RecurringMatching.predictOccurrencesWithinWindow(
                    now, latest.time(), overdue, series.getFrequency(), windowEnd);

            List<ForecastEntryDto> accountEntries = entriesByAccountId.computeIfAbsent(
                    latest.accountId(), id -> new ArrayList<>());
            for (OffsetDateTime date : occurrenceDates) {
                accountEntries.add(toEntry(series, date, latest.amount()));
            }
        }
        return entriesByAccountId;
    }

    private ForecastEntryDto toEntry(RecurringSeriesDto series, OffsetDateTime date, BigDecimal amount) {
        ForecastEntryDto entry = new ForecastEntryDto();
        entry.setDate(date);
        entry.setSeriesId(series.getId());
        entry.setTransactionType(series.getTransactionType());
        entry.setDescription(series.getDescription());
        entry.setAmount(amount);
        return entry;
    }

    private AccountForecastDto forecastFor(AccountDto account, int windowWeeks,
                                            Map<String, List<ForecastEntryDto>> entriesByAccountId) {
        List<ForecastEntryDto> timeline = new ArrayList<>(
                entriesByAccountId.getOrDefault(account.getId(), List.of()));
        timeline.sort(Comparator.comparing(ForecastEntryDto::getDate));

        BigDecimal currentBalance = account.getBalance();
        BigDecimal running = currentBalance;
        boolean atRisk = running.signum() < 0;
        for (ForecastEntryDto entry : timeline) {
            running = entry.getTransactionType() == TransactionType.BILL
                    ? running.subtract(entry.getAmount())
                    : running.add(entry.getAmount());
            entry.setProjectedBalance(running);
            if (running.signum() < 0) {
                atRisk = true;
            }
        }

        AccountForecastDto forecast = new AccountForecastDto();
        forecast.setAccountId(account.getId());
        forecast.setAccountName(account.getName());
        forecast.setAccountType(account.getType());
        forecast.setCurrentBalance(currentBalance);
        forecast.setWindowWeeks(windowWeeks);
        forecast.setAtRisk(atRisk);
        forecast.setTimeline(timeline);
        return forecast;
    }
}
