package at.ymeri.my.finance.controller.sync;

import at.ymeri.my.finance.application.controller.sync.SyncExportApi;
import at.ymeri.my.finance.application.data.SyncSnapshot;
import at.ymeri.my.finance.application.mapper.SyncMapper;
import at.ymeri.my.finance.domain.api.ExportSyncSnapshotService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SyncExportController implements SyncExportApi {

    private final ExportSyncSnapshotService exportSyncSnapshotService;

    public SyncExportController(ExportSyncSnapshotService exportSyncSnapshotService) {
        this.exportSyncSnapshotService = exportSyncSnapshotService;
    }

    @Override
    public ResponseEntity<SyncSnapshot> exportSyncSnapshot() {
        return ResponseEntity.ok(SyncMapper.INSTANCE.map(exportSyncSnapshotService.export()));
    }
}
