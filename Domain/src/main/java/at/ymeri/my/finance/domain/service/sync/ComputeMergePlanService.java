package at.ymeri.my.finance.domain.service.sync;

import at.ymeri.my.finance.domain.api.GetAccountService;
import at.ymeri.my.finance.domain.api.GetBudgetService;
import at.ymeri.my.finance.domain.api.GetCategoryService;
import at.ymeri.my.finance.domain.api.GetRecurringSeriesService;
import at.ymeri.my.finance.domain.data.account.AccountDto;
import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.data.budget.BudgetDto;
import at.ymeri.my.finance.domain.data.category.CategoryDto;
import at.ymeri.my.finance.domain.data.goal.SavingsGoalDto;
import at.ymeri.my.finance.domain.data.income.IncomeDto;
import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesDto;
import at.ymeri.my.finance.domain.data.sync.CorrectionConflict;
import at.ymeri.my.finance.domain.data.sync.EntityMergePlan;
import at.ymeri.my.finance.domain.data.sync.MergePlanDto;
import at.ymeri.my.finance.domain.data.sync.MergeUpdate;
import at.ymeri.my.finance.domain.data.sync.SyncSnapshotDto;
import at.ymeri.my.finance.domain.spi.bill.GetBillPersistencePort;
import at.ymeri.my.finance.domain.spi.goal.GetSavingsGoalPersistencePort;
import at.ymeri.my.finance.domain.spi.income.GetIncomePersistencePort;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Pure, read-only planner: loads current local state, compares it against an incoming
 * {@link SyncSnapshotDto}, and decides what an import would do — without writing anything.
 * {@code PreviewSyncImportServiceImpl} and {@code ApplySyncImportServiceImpl} both call this;
 * only the latter goes on to hand the resulting plan to {@code ApplyMergePlanService}. Entity
 * types are processed in dependency order (research.md R6) so that later types can assume any
 * account/category id they reference either already exists locally or was itself just planned
 * for insertion in this same snapshot.
 */
@Service
public class ComputeMergePlanService {

    private final GetAccountService getAccountService;
    private final GetCategoryService getCategoryService;
    private final GetBudgetService getBudgetService;
    private final GetRecurringSeriesService getRecurringSeriesService;
    private final GetBillPersistencePort getBillPersistencePort;
    private final GetIncomePersistencePort getIncomePersistencePort;
    private final GetSavingsGoalPersistencePort getSavingsGoalPersistencePort;
    private final SyncEntityMatching matching;

    public ComputeMergePlanService(GetAccountService getAccountService,
                                    GetCategoryService getCategoryService,
                                    GetBudgetService getBudgetService,
                                    GetRecurringSeriesService getRecurringSeriesService,
                                    GetBillPersistencePort getBillPersistencePort,
                                    GetIncomePersistencePort getIncomePersistencePort,
                                    GetSavingsGoalPersistencePort getSavingsGoalPersistencePort,
                                    SyncEntityMatching matching) {
        this.getAccountService = getAccountService;
        this.getCategoryService = getCategoryService;
        this.getBudgetService = getBudgetService;
        this.getRecurringSeriesService = getRecurringSeriesService;
        this.getBillPersistencePort = getBillPersistencePort;
        this.getIncomePersistencePort = getIncomePersistencePort;
        this.getSavingsGoalPersistencePort = getSavingsGoalPersistencePort;
        this.matching = matching;
    }

    public MergePlanDto computeMergePlan(SyncSnapshotDto incoming) {
        if (incoming.getSchemaVersion() != ExportSyncSnapshotServiceImpl.SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unrecognized sync snapshot schemaVersion: " + incoming.getSchemaVersion()
                            + " (this build recognizes " + ExportSyncSnapshotServiceImpl.SCHEMA_VERSION + ")");
        }

        MergePlanDto plan = new MergePlanDto();

        List<AccountDto> localAccounts = getAccountService.getAll();
        plan.setAccounts(planMutableEntities(localAccounts, incoming.getAccounts(),
                matching::matchAccount, AccountDto::getId, AccountDto::getUpdatedAt));

        List<CategoryDto> localCategories = getCategoryService.getAll();
        plan.setCategories(planMutableEntities(localCategories, incoming.getCategories(),
                matching::matchCategory, CategoryDto::getId, CategoryDto::getUpdatedAt));

        List<BudgetDto> localBudgets = getBudgetService.getAll();
        plan.setBudgets(planMutableEntities(localBudgets, incoming.getBudgets(),
                matching::matchBudget, BudgetDto::getId, BudgetDto::getUpdatedAt));

        List<RecurringSeriesDto> localSeries = getRecurringSeriesService.getAll();
        plan.setRecurringSeries(planMutableEntities(localSeries, incoming.getRecurringSeries(),
                matching::matchRecurringSeries, RecurringSeriesDto::getId, RecurringSeriesDto::getUpdatedAt));

        List<BillDto> localBills = getBillPersistencePort.getAll();
        EntityMergePlan<BillDto> billPlan = planBills(localBills, incoming.getBills());
        plan.setBills(billPlan);
        plan.setBillCorrectionConflicts(computeCorrectionConflicts(
                mergedById(localBills, billPlan, BillDto::getId),
                BillDto::getId, BillDto::getCorrectsTransactionId, BillDto::isReversal, BillDto::getRecordedAt));

        List<IncomeDto> localIncomes = getIncomePersistencePort.getAll();
        EntityMergePlan<IncomeDto> incomePlan = planIncomes(localIncomes, incoming.getIncomes());
        plan.setIncomes(incomePlan);
        plan.setIncomeCorrectionConflicts(computeCorrectionConflicts(
                mergedById(localIncomes, incomePlan, IncomeDto::getId),
                IncomeDto::getId, IncomeDto::getCorrectsTransactionId, IncomeDto::isReversal, IncomeDto::getRecordedAt));

        List<SavingsGoalDto> localGoals = getSavingsGoalPersistencePort.getAll();
        plan.setSavingsGoals(planMutableEntities(localGoals, incoming.getSavingsGoals(),
                (local, item) -> matchById(local, item, SavingsGoalDto::getId),
                SavingsGoalDto::getId, SavingsGoalDto::getUpdatedAt));

        return plan;
    }

    /**
     * Shared logic for every entity type whose merge decision is purely "matched, and is the
     * incoming last-modified timestamp later than the local one" (accounts, categories, budgets,
     * recurring series, savings goals). Bills and incomes have their own methods below: they match
     * by id only (no natural-key fallback — Principle II) and their "is there anything new here"
     * signal isn't a single {@code updatedAt} field.
     */
    private <T> EntityMergePlan<T> planMutableEntities(List<T> local, List<T> incoming,
                                                         BiFunction<List<T>, T, Optional<T>> matcher,
                                                         Function<T, String> idOf,
                                                         Function<T, OffsetDateTime> updatedAtOf) {
        List<T> toInsert = new ArrayList<>();
        List<MergeUpdate<T>> toUpdate = new ArrayList<>();
        int unchanged = 0;
        for (T item : incoming) {
            Optional<T> match = matcher.apply(local, item);
            if (match.isEmpty()) {
                toInsert.add(item);
            } else if (isLater(updatedAtOf.apply(item), updatedAtOf.apply(match.get()))) {
                toUpdate.add(new MergeUpdate<>(idOf.apply(match.get()), item));
            } else {
                unchanged++;
            }
        }
        return buildPlan(toInsert, toUpdate, unchanged);
    }

    private EntityMergePlan<BillDto> planBills(List<BillDto> local, List<BillDto> incoming) {
        List<BillDto> toInsert = new ArrayList<>();
        List<MergeUpdate<BillDto>> toUpdate = new ArrayList<>();
        int unchanged = 0;
        for (BillDto item : incoming) {
            Optional<BillDto> match = matchById(local, item, BillDto::getId);
            if (match.isEmpty()) {
                toInsert.add(item);
            } else if (isLater(item.getNecessityTagUpdatedAt(), match.get().getNecessityTagUpdatedAt())) {
                toUpdate.add(new MergeUpdate<>(match.get().getId(), item));
            } else {
                unchanged++;
            }
        }
        return buildPlan(toInsert, toUpdate, unchanged);
    }

    /**
     * Incomes carry no per-device-mutable field the way a bill's necessity tag is (Principle I —
     * an income's financial facts only ever change via a correction, which is itself a new row
     * matched by its own id) so a same-id match is always {@code unchanged}.
     */
    private EntityMergePlan<IncomeDto> planIncomes(List<IncomeDto> local, List<IncomeDto> incoming) {
        List<IncomeDto> toInsert = new ArrayList<>();
        int unchanged = 0;
        for (IncomeDto item : incoming) {
            if (matchById(local, item, IncomeDto::getId).isEmpty()) {
                toInsert.add(item);
            } else {
                unchanged++;
            }
        }
        return buildPlan(toInsert, List.of(), unchanged);
    }

    private <T> Optional<T> matchById(List<T> local, T incoming, Function<T, String> idOf) {
        String incomingId = idOf.apply(incoming);
        if (incomingId == null) {
            return Optional.empty();
        }
        return local.stream().filter(l -> incomingId.equals(idOf.apply(l))).findFirst();
    }

    private <T> EntityMergePlan<T> buildPlan(List<T> toInsert, List<MergeUpdate<T>> toUpdate, int unchanged) {
        EntityMergePlan<T> plan = new EntityMergePlan<>();
        plan.setToInsert(toInsert);
        plan.setToUpdate(toUpdate);
        plan.setUnchangedCount(unchanged);
        return plan;
    }

    /**
     * {@code true} if {@code candidate} represents strictly newer information than {@code current}
     * — a null timestamp never beats anything (data-model.md: "older than anything with a real
     * timestamp"), and a real timestamp always beats a null one.
     */
    private boolean isLater(OffsetDateTime candidate, OffsetDateTime current) {
        if (candidate == null) {
            return false;
        }
        return current == null || candidate.isAfter(current);
    }

    /**
     * The post-merge row set for one bill/income type: every local row, overlaid with this plan's
     * inserts and updates, keyed by id. What correction-conflict grouping runs against, since a
     * conflict can be created or resolved by rows arriving only in this import.
     */
    private <T> Collection<T> mergedById(List<T> local, EntityMergePlan<T> plan, Function<T, String> idOf) {
        Map<String, T> merged = new LinkedHashMap<>();
        for (T item : local) {
            merged.put(idOf.apply(item), item);
        }
        for (T item : plan.getToInsert()) {
            merged.put(idOf.apply(item), item);
        }
        for (MergeUpdate<T> update : plan.getToUpdate()) {
            merged.put(update.localId(), update.incoming());
        }
        return merged.values();
    }

    /**
     * research.md R3: a correction conflict is two or more non-reversal rows sharing one non-null
     * {@code correctsTransactionId} — only possible once sync lets two devices each correct the
     * same original independently. The row with the latest {@code recordedAt} wins (ties broken by
     * id, for determinism); every other sibling is reported, never silently dropped.
     */
    private <T> List<CorrectionConflict> computeCorrectionConflicts(Collection<T> merged,
                                                                      Function<T, String> idOf,
                                                                      Function<T, String> correctsIdOf,
                                                                      Function<T, Boolean> reversalOf,
                                                                      Function<T, OffsetDateTime> recordedAtOf) {
        Map<String, List<T>> byCorrectedId = merged.stream()
                .filter(row -> !reversalOf.apply(row) && correctsIdOf.apply(row) != null)
                .collect(Collectors.groupingBy(correctsIdOf, LinkedHashMap::new, Collectors.toList()));

        List<CorrectionConflict> conflicts = new ArrayList<>();
        for (Map.Entry<String, List<T>> entry : byCorrectedId.entrySet()) {
            List<T> siblings = entry.getValue();
            if (siblings.size() < 2) {
                continue;
            }
            List<T> ranked = siblings.stream()
                    .sorted(Comparator.<T, OffsetDateTime>comparing(recordedAtOf,
                                    Comparator.nullsFirst(Comparator.naturalOrder()))
                            .reversed()
                            .thenComparing(idOf))
                    .toList();
            String winningId = idOf.apply(ranked.get(0));
            for (int i = 1; i < ranked.size(); i++) {
                conflicts.add(new CorrectionConflict(entry.getKey(), winningId, idOf.apply(ranked.get(i))));
            }
        }
        return conflicts;
    }
}
