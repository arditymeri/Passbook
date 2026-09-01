package at.ymeri.my.finance.domain.api;

import at.ymeri.my.finance.domain.data.sync.ImportSummaryDto;
import at.ymeri.my.finance.domain.data.sync.SyncSnapshotDto;

public interface PreviewSyncImportService {

    /**
     * Computes what importing {@code snapshot} would do without writing anything —
     * {@link ImportSummaryDto#isApplied()} is always {@code false} on the result.
     */
    ImportSummaryDto preview(SyncSnapshotDto snapshot);
}
