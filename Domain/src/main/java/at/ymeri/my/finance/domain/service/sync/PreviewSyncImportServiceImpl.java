package at.ymeri.my.finance.domain.service.sync;

import at.ymeri.my.finance.domain.api.PreviewSyncImportService;
import at.ymeri.my.finance.domain.data.sync.ImportSummaryDto;
import at.ymeri.my.finance.domain.data.sync.MergePlanDto;
import at.ymeri.my.finance.domain.data.sync.SyncSnapshotDto;
import org.springframework.stereotype.Service;

@Service
public class PreviewSyncImportServiceImpl implements PreviewSyncImportService {

    private final ComputeMergePlanService computeMergePlanService;

    public PreviewSyncImportServiceImpl(ComputeMergePlanService computeMergePlanService) {
        this.computeMergePlanService = computeMergePlanService;
    }

    @Override
    public ImportSummaryDto preview(SyncSnapshotDto snapshot) {
        MergePlanDto plan = computeMergePlanService.computeMergePlan(snapshot);
        ImportSummaryDto summary = ImportSummaryFactory.summarize(plan);
        summary.setApplied(false);
        return summary;
    }
}
