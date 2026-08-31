package at.ymeri.my.finance.domain.service.sync;

import at.ymeri.my.finance.domain.data.sync.ImportSummaryDto;
import at.ymeri.my.finance.domain.data.sync.MergePlanDto;

/**
 * Shared by {@link PreviewSyncImportServiceImpl} and {@link ApplySyncImportServiceImpl} so both
 * summarize a {@link MergePlanDto} into an {@link ImportSummaryDto} identically — they differ only
 * in the {@code applied} flag, which each sets itself after calling {@link #summarize}.
 */
final class ImportSummaryFactory {

    private ImportSummaryFactory() {
    }

    static ImportSummaryDto summarize(MergePlanDto plan) {
        ImportSummaryDto summary = new ImportSummaryDto();
        summary.setAccounts(plan.getAccounts().toCounts());
        summary.setCategories(plan.getCategories().toCounts());
        summary.setBudgets(plan.getBudgets().toCounts());
        summary.setRecurringSeries(plan.getRecurringSeries().toCounts());
        summary.setBills(plan.getBills().toCounts());
        summary.setIncomes(plan.getIncomes().toCounts());
        summary.setSavingsGoals(plan.getSavingsGoals().toCounts());
        summary.setCorrectionConflictsResolved(plan.totalCorrectionConflicts());
        return summary;
    }
}
