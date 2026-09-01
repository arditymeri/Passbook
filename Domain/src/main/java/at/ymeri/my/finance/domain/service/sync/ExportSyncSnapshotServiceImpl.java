package at.ymeri.my.finance.domain.service.sync;

import at.ymeri.my.finance.domain.api.ExportSyncSnapshotService;
import at.ymeri.my.finance.domain.api.GetAccountService;
import at.ymeri.my.finance.domain.api.GetBudgetService;
import at.ymeri.my.finance.domain.api.GetCategoryService;
import at.ymeri.my.finance.domain.api.GetRecurringSeriesService;
import at.ymeri.my.finance.domain.data.sync.SyncSnapshotDto;
import at.ymeri.my.finance.domain.spi.bill.GetBillPersistencePort;
import at.ymeri.my.finance.domain.spi.goal.GetSavingsGoalPersistencePort;
import at.ymeri.my.finance.domain.spi.income.GetIncomePersistencePort;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

/**
 * Assembles a full-state snapshot at read time — nothing is cached or stored (Constitution
 * Principle III applied to sync as a whole, not just balances).
 *
 * <p>Bills, incomes, and savings goals are read through their persistence ports directly rather
 * than the higher {@code GetBillService}/{@code GetIncomeService}/{@code GetSavingsGoalService}:
 * the bill/income services' {@code getAll()} deliberately hides reversal rows and rows superseded
 * by a correction (the human-facing "current value only" view), but a faithful snapshot needs
 * every row so an importing device can reconstruct the full correction history — and
 * {@code GetSavingsGoalService.getAll()} returns a derived, computed status view
 * ({@code SavingsGoalStatusDto}), not the raw stored fields this snapshot needs.
 */
@Service
public class ExportSyncSnapshotServiceImpl implements ExportSyncSnapshotService {

    /** Bumped only if the snapshot's shape changes in a way old readers couldn't handle. */
    public static final int SCHEMA_VERSION = 1;

    private final GetAccountService getAccountService;
    private final GetCategoryService getCategoryService;
    private final GetBudgetService getBudgetService;
    private final GetRecurringSeriesService getRecurringSeriesService;
    private final GetBillPersistencePort getBillPersistencePort;
    private final GetIncomePersistencePort getIncomePersistencePort;
    private final GetSavingsGoalPersistencePort getSavingsGoalPersistencePort;

    public ExportSyncSnapshotServiceImpl(GetAccountService getAccountService,
                                          GetCategoryService getCategoryService,
                                          GetBudgetService getBudgetService,
                                          GetRecurringSeriesService getRecurringSeriesService,
                                          GetBillPersistencePort getBillPersistencePort,
                                          GetIncomePersistencePort getIncomePersistencePort,
                                          GetSavingsGoalPersistencePort getSavingsGoalPersistencePort) {
        this.getAccountService = getAccountService;
        this.getCategoryService = getCategoryService;
        this.getBudgetService = getBudgetService;
        this.getRecurringSeriesService = getRecurringSeriesService;
        this.getBillPersistencePort = getBillPersistencePort;
        this.getIncomePersistencePort = getIncomePersistencePort;
        this.getSavingsGoalPersistencePort = getSavingsGoalPersistencePort;
    }

    @Override
    public SyncSnapshotDto export() {
        SyncSnapshotDto snapshot = new SyncSnapshotDto();
        snapshot.setSchemaVersion(SCHEMA_VERSION);
        snapshot.setExportedAt(OffsetDateTime.now());
        snapshot.setAccounts(getAccountService.getAll());
        snapshot.setCategories(getCategoryService.getAll());
        snapshot.setBudgets(getBudgetService.getAll());
        snapshot.setRecurringSeries(getRecurringSeriesService.getAll());
        snapshot.setBills(getBillPersistencePort.getAll());
        snapshot.setIncomes(getIncomePersistencePort.getAll());
        snapshot.setSavingsGoals(getSavingsGoalPersistencePort.getAll());
        return snapshot;
    }
}
