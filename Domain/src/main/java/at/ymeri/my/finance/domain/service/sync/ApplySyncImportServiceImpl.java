package at.ymeri.my.finance.domain.service.sync;

import at.ymeri.my.finance.domain.api.ApplySyncImportService;
import at.ymeri.my.finance.domain.data.sync.ImportSummaryDto;
import at.ymeri.my.finance.domain.data.sync.MergePlanDto;
import at.ymeri.my.finance.domain.data.sync.SyncSnapshotDto;
import org.springframework.stereotype.Service;

@Service
public class ApplySyncImportServiceImpl implements ApplySyncImportService {

    private final ComputeMergePlanService computeMergePlanService;
    private final ApplyMergePlanService applyMergePlanService;

    public ApplySyncImportServiceImpl(ComputeMergePlanService computeMergePlanService,
                                       ApplyMergePlanService applyMergePlanService) {
        this.computeMergePlanService = computeMergePlanService;
        this.applyMergePlanService = applyMergePlanService;
    }

    @Override
    public ImportSummaryDto apply(SyncSnapshotDto snapshot) {
        MergePlanDto plan = computeMergePlanService.computeMergePlan(snapshot);
        applyMergePlanService.apply(plan);
        ImportSummaryDto summary = ImportSummaryFactory.summarize(plan);
        summary.setApplied(true);
        return summary;
    }
}
