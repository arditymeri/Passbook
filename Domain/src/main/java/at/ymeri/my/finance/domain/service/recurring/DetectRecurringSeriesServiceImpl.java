package at.ymeri.my.finance.domain.service.recurring;

import at.ymeri.my.finance.domain.api.DetectRecurringSeriesService;
import at.ymeri.my.finance.domain.api.GetBillService;
import at.ymeri.my.finance.domain.api.GetIncomeService;
import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.data.common.RecurringFrequency;
import at.ymeri.my.finance.domain.data.income.IncomeDto;
import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesDto;
import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesStatus;
import at.ymeri.my.finance.domain.data.recurring.TransactionType;
import at.ymeri.my.finance.domain.spi.recurring.AddRecurringSeriesPersistencePort;
import at.ymeri.my.finance.domain.spi.recurring.GetRecurringSeriesPersistencePort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Scans bill/income history for groups of occurrences that look recurring and persists a new
 * PROPOSED {@link RecurringSeriesDto} for each group not already covered by an existing series
 * (in any status). Never mutates a bill or income row — reads only through {@link GetBillService}
 * / {@link GetIncomeService}, the correction-aware "one row per logical transaction" view (see
 * research.md), never the raw reversal-inclusive SPI ports used for summing.
 */
@Service
public class DetectRecurringSeriesServiceImpl implements DetectRecurringSeriesService {

    private static final int DEFAULT_THRESHOLD = 3;
    private static final int FLAGGED_THRESHOLD = 2;

    private final GetBillService getBillService;
    private final GetIncomeService getIncomeService;
    private final GetRecurringSeriesPersistencePort getRecurringSeriesPersistencePort;
    private final AddRecurringSeriesPersistencePort addRecurringSeriesPersistencePort;

    public DetectRecurringSeriesServiceImpl(GetBillService getBillService,
                                             GetIncomeService getIncomeService,
                                             GetRecurringSeriesPersistencePort getRecurringSeriesPersistencePort,
                                             AddRecurringSeriesPersistencePort addRecurringSeriesPersistencePort) {
        this.getBillService = getBillService;
        this.getIncomeService = getIncomeService;
        this.getRecurringSeriesPersistencePort = getRecurringSeriesPersistencePort;
        this.addRecurringSeriesPersistencePort = addRecurringSeriesPersistencePort;
    }

    @Override
    public List<RecurringSeriesDto> detect() {
        detect(TransactionType.BILL, billOccurrences());
        detect(TransactionType.INCOME, incomeOccurrences());
        return getRecurringSeriesPersistencePort.getAll();
    }

    private List<Occurrence> billOccurrences() {
        return getBillService.getAll().stream()
                .filter(b -> b.getCategoryId() != null)
                .filter(b -> hasText(b.getDescription()))
                .map(b -> new Occurrence(b.getCategoryId(), b.getDescription(), b.getTime(), b.getAmount(), b.isRecurring()))
                .toList();
    }

    private List<Occurrence> incomeOccurrences() {
        return getIncomeService.getAll().stream()
                .filter(i -> i.getSource() != null)
                .filter(i -> hasText(i.getDescription()))
                .map(i -> new Occurrence(i.getSource().name(), i.getDescription(), i.getTime(), i.getAmount(), i.isRecurring()))
                .toList();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void detect(TransactionType type, List<Occurrence> occurrences) {
        Map<GroupKey, List<Occurrence>> groups = occurrences.stream()
                .collect(Collectors.groupingBy(o -> new GroupKey(o.groupKey(), RecurringMatching.normalizeDescription(o.description()))));

        for (Map.Entry<GroupKey, List<Occurrence>> entry : groups.entrySet()) {
            List<Occurrence> sorted = entry.getValue().stream()
                    .sorted(Comparator.comparing(Occurrence::time))
                    .toList();

            for (RecurringFrequency frequency : RecurringFrequency.values()) {
                List<Occurrence> run = mostRecentMatchingRun(sorted, frequency);
                boolean anyFlagged = run.stream().anyMatch(Occurrence::recurringFlag);
                int threshold = anyFlagged ? FLAGGED_THRESHOLD : DEFAULT_THRESHOLD;
                if (run.size() >= threshold) {
                    proposeIfNotCovered(type, entry.getKey(), frequency);
                    break;
                }
            }
        }
    }

    /**
     * The longest run of consecutive occurrences ending at the most recent one, where each
     * consecutive pair satisfies both the cadence's date tolerance and the amount tolerance for
     * the given frequency.
     */
    private static List<Occurrence> mostRecentMatchingRun(List<Occurrence> sorted, RecurringFrequency frequency) {
        int n = sorted.size();
        if (n == 0) {
            return List.of();
        }
        int start = n - 1;
        for (int i = n - 1; i > 0; i--) {
            Occurrence current = sorted.get(i);
            Occurrence previous = sorted.get(i - 1);
            Duration gap = Duration.between(previous.time(), current.time());
            boolean matches = RecurringMatching.isWithinCadenceTolerance(frequency, gap)
                    && RecurringMatching.isWithinAmountTolerance(previous.amount(), current.amount());
            if (!matches) {
                break;
            }
            start = i - 1;
        }
        return sorted.subList(start, n);
    }

    private void proposeIfNotCovered(TransactionType type, GroupKey key, RecurringFrequency frequency) {
        boolean alreadyCovered = getRecurringSeriesPersistencePort.findByKey(type, key.groupKey(), key.description()).isPresent();
        if (alreadyCovered) {
            return;
        }
        RecurringSeriesDto dto = new RecurringSeriesDto();
        dto.setTransactionType(type);
        dto.setGroupKey(key.groupKey());
        dto.setDescription(key.description());
        dto.setFrequency(frequency);
        dto.setStatus(RecurringSeriesStatus.PROPOSED);
        dto.setCreatedAt(OffsetDateTime.now());
        addRecurringSeriesPersistencePort.add(dto);
    }

    private record GroupKey(String groupKey, String description) {
    }

    private record Occurrence(String groupKey, String description, OffsetDateTime time, BigDecimal amount, boolean recurringFlag) {
    }
}
