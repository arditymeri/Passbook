package at.ymeri.my.finance.domain.data.sync;

import lombok.Data;

/**
 * The user-facing view of a {@link MergePlanDto} — what {@code PreviewSyncImportServiceImpl} and
 * {@code ApplySyncImportServiceImpl} both return, differing only in {@code applied}.
 */
@Data
public class ImportSummaryDto {

    private boolean applied;
    private EntityMergeCounts accounts;
    private EntityMergeCounts categories;
    private EntityMergeCounts budgets;
    private EntityMergeCounts recurringSeries;
    private EntityMergeCounts bills;
    private EntityMergeCounts incomes;
    private EntityMergeCounts savingsGoals;
    private int correctionConflictsResolved;
}
