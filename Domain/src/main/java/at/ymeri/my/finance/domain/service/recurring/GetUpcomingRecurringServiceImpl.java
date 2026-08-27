package at.ymeri.my.finance.domain.service.recurring;

import at.ymeri.my.finance.domain.api.GetBillService;
import at.ymeri.my.finance.domain.api.GetIncomeService;
import at.ymeri.my.finance.domain.api.GetRecurringSeriesService;
import at.ymeri.my.finance.domain.api.GetUpcomingRecurringService;
import at.ymeri.my.finance.domain.data.recurring.PriceChangeAlertDto;
import at.ymeri.my.finance.domain.data.recurring.RecurringDashboardResult;
import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesDto;
import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesStatus;
import at.ymeri.my.finance.domain.data.recurring.TransactionType;
import at.ymeri.my.finance.domain.data.recurring.UpcomingRecurringItemDto;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Predicts each confirmed series' next occurrence, and flags when its most recently recorded
 * occurrence's amount differs from the one before it, at read time from current transaction
 * history — nothing is cached or stored (Constitution Principle III).
 */
@Service
public class GetUpcomingRecurringServiceImpl implements GetUpcomingRecurringService {

    private final GetRecurringSeriesService getRecurringSeriesService;
    private final GetBillService getBillService;
    private final GetIncomeService getIncomeService;

    public GetUpcomingRecurringServiceImpl(GetRecurringSeriesService getRecurringSeriesService,
                                            GetBillService getBillService,
                                            GetIncomeService getIncomeService) {
        this.getRecurringSeriesService = getRecurringSeriesService;
        this.getBillService = getBillService;
        this.getIncomeService = getIncomeService;
    }

    @Override
    public RecurringDashboardResult getDashboard() {
        List<RecurringSeriesDto> confirmed = getRecurringSeriesService.getAll().stream()
                .filter(s -> s.getStatus() == RecurringSeriesStatus.CONFIRMED)
                .toList();

        List<UpcomingRecurringItemDto> upcoming = new ArrayList<>();
        List<PriceChangeAlertDto> priceChanges = new ArrayList<>();
        for (RecurringSeriesDto series : confirmed) {
            List<MemberOccurrence> members = membersOf(series);
            if (members.isEmpty()) {
                continue;
            }
            MemberOccurrence latest = members.get(members.size() - 1);
            OffsetDateTime predictedDate = RecurringMatching.predictNextDate(latest.time(), series.getFrequency());

            UpcomingRecurringItemDto item = new UpcomingRecurringItemDto();
            item.setSeriesId(series.getId());
            item.setTransactionType(series.getTransactionType());
            item.setGroupKey(series.getGroupKey());
            item.setDescription(series.getDescription());
            item.setPredictedDate(predictedDate);
            item.setPredictedAmount(latest.amount());
            // predictedDate is always derived fresh from the current latest member, so it is
            // always strictly after every member's time by construction — "overdue" therefore
            // reduces to whether that freshly-derived date has already passed.
            item.setOverdue(predictedDate.isBefore(OffsetDateTime.now()));
            upcoming.add(item);

            if (members.size() >= 2) {
                MemberOccurrence prior = members.get(members.size() - 2);
                if (!RecurringMatching.isWithinAmountTolerance(prior.amount(), latest.amount())) {
                    PriceChangeAlertDto alert = new PriceChangeAlertDto();
                    alert.setTransactionId(latest.id());
                    alert.setTransactionType(series.getTransactionType());
                    alert.setGroupKey(series.getGroupKey());
                    alert.setDescription(series.getDescription());
                    alert.setPriorAmount(prior.amount());
                    alert.setNewAmount(latest.amount());
                    alert.setDelta(latest.amount().subtract(prior.amount()));
                    priceChanges.add(alert);
                }
            }
        }

        RecurringDashboardResult result = new RecurringDashboardResult();
        result.setUpcoming(upcoming);
        result.setRecentPriceChanges(priceChanges);
        return result;
    }

    /**
     * A confirmed series' matching transactions, sorted oldest first — a bill matched on
     * {@code categoryId}, or an income matched on {@code source.name()}, plus a shared
     * normalized-description match, mirroring {@link DetectRecurringSeriesServiceImpl}'s grouping.
     */
    List<MemberOccurrence> membersOf(RecurringSeriesDto series) {
        if (series.getTransactionType() == TransactionType.BILL) {
            return getBillService.getAll().stream()
                    .filter(b -> series.getGroupKey().equals(b.getCategoryId()))
                    .filter(b -> series.getDescription().equals(RecurringMatching.normalizeDescription(b.getDescription())))
                    .map(b -> new MemberOccurrence(b.getId(), b.getTime(), b.getAmount()))
                    .sorted(Comparator.comparing(MemberOccurrence::time))
                    .toList();
        }
        return getIncomeService.getAll().stream()
                .filter(i -> i.getSource() != null && series.getGroupKey().equals(i.getSource().name()))
                .filter(i -> series.getDescription().equals(RecurringMatching.normalizeDescription(i.getDescription())))
                .map(i -> new MemberOccurrence(i.getId(), i.getTime(), i.getAmount()))
                .sorted(Comparator.comparing(MemberOccurrence::time))
                .toList();
    }

    record MemberOccurrence(String id, OffsetDateTime time, BigDecimal amount) {
    }
}
