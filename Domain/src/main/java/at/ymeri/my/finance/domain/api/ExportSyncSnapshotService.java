package at.ymeri.my.finance.domain.api;

import at.ymeri.my.finance.domain.data.sync.SyncSnapshotDto;

public interface ExportSyncSnapshotService {

    SyncSnapshotDto export();
}
