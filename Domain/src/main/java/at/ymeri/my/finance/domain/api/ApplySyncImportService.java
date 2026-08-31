package at.ymeri.my.finance.domain.api;

import at.ymeri.my.finance.domain.data.sync.ImportSummaryDto;
import at.ymeri.my.finance.domain.data.sync.SyncSnapshotDto;

public interface ApplySyncImportService {

    /**
     * Computes what importing {@code snapshot} would do and persists it —
     * {@link ImportSummaryDto#isApplied()} is always {@code true} on the result.
     */
    ImportSummaryDto apply(SyncSnapshotDto snapshot);
}
