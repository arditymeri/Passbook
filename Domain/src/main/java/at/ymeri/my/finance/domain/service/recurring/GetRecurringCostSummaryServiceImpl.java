package at.ymeri.my.finance.domain.service.recurring;

import at.ymeri.my.finance.domain.api.GetRecurringCostSummaryService;
import at.ymeri.my.finance.domain.api.GetRecurringSeriesService;
import at.ymeri.my.finance.domain.data.common.RecurringFrequency;
import at.ymeri.my.finance.domain.data.recurring.RecurringCostSummaryItemDto;
import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesDto;
import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesStatus;
import at.ymeri.my.finance.domain.service.recurring.RecurringSeriesMembers.MemberOccurrence;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Monthly-equivalent cost and cumulative (first-vs-latest) price-creep data for every confirmed
 * recurring series, at read time from current transaction history — nothing is cached or stored
 * (Constitution Principle III). A distinct, additive signal from
 * {@link GetUpcomingRecurringServiceImpl}'s {@code recentPriceChanges}, which only compares the
 * two most recent occurrences rather than the full history.
 */
@Service
public class GetRecurringCostSummaryServiceImpl implements GetRecurringCostSummaryService {

    private final GetRecurringSeriesService getRecurringSeriesService;
    private final RecurringSeriesMembers recurringSeriesMembers;

    public GetRecurringCostSummaryServiceImpl(GetRecurringSeriesService getRecurringSeriesService,
                                               RecurringSeriesMembers recurringSeriesMembers) {
        this.getRecurringSeriesService = getRecurringSeriesService;
        this.recurringSeriesMembers = recurringSeriesMembers;
    }

    @Override
    public List<RecurringCostSummaryItemDto> getSummary() {
        List<RecurringSeriesDto> confirmed = getRecurringSeriesService.getAll().stream()
                .filter(s -> s.getStatus() == RecurringSeriesStatus.CONFIRMED)
                .toList();

        List<RecurringCostSummaryItemDto> summary = new ArrayList<>();
        for (RecurringSeriesDto series : confirmed) {
            List<MemberOccurrence> members = recurringSeriesMembers.membersOf(series);
            if (members.isEmpty()) {
                continue;
            }
            BigDecimal originalAmount = members.get(0).amount();
            BigDecimal currentAmount = members.get(members.size() - 1).amount();
            boolean priceIncreased = !RecurringMatching.isWithinAmountTolerance(originalAmount, currentAmount)
                    && currentAmount.compareTo(originalAmount) > 0;

            RecurringCostSummaryItemDto item = new RecurringCostSummaryItemDto();
            item.setSeriesId(series.getId());
            item.setDescription(series.getDescription());
            item.setMonthlyEquivalentAmount(monthlyEquivalent(currentAmount, series.getFrequency()));
            item.setOriginalAmount(originalAmount);
            item.setPriceIncreased(priceIncreased);
            item.setIncreaseAmount(priceIncreased ? currentAmount.subtract(originalAmount) : null);
            summary.add(item);
        }
        return summary;
    }

    private static BigDecimal monthlyEquivalent(BigDecimal amount, RecurringFrequency frequency) {
        double multiplier = RecurringMatching.occurrencesPerMonth(frequency);
        return amount.multiply(BigDecimal.valueOf(multiplier)).setScale(2, RoundingMode.HALF_EVEN);
    }
}
