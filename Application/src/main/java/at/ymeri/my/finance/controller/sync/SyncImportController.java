package at.ymeri.my.finance.controller.sync;

import at.ymeri.my.finance.application.controller.sync.SyncImportApi;
import at.ymeri.my.finance.application.data.ImportSummary;
import at.ymeri.my.finance.application.data.SyncSnapshot;
import at.ymeri.my.finance.application.mapper.SyncMapper;
import at.ymeri.my.finance.domain.api.ApplySyncImportService;
import at.ymeri.my.finance.domain.api.PreviewSyncImportService;
import at.ymeri.my.finance.domain.data.sync.SyncSnapshotDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SyncImportController implements SyncImportApi {

    private final PreviewSyncImportService previewSyncImportService;
    private final ApplySyncImportService applySyncImportService;

    public SyncImportController(PreviewSyncImportService previewSyncImportService,
                                 ApplySyncImportService applySyncImportService) {
        this.previewSyncImportService = previewSyncImportService;
        this.applySyncImportService = applySyncImportService;
    }

    @Override
    public ResponseEntity<ImportSummary> previewSyncImport(SyncSnapshot syncSnapshot) {
        SyncSnapshotDto snapshotDto = SyncMapper.INSTANCE.map(syncSnapshot);
        return ResponseEntity.ok(SyncMapper.INSTANCE.map(previewSyncImportService.preview(snapshotDto)));
    }

    @Override
    public ResponseEntity<ImportSummary> applySyncImport(SyncSnapshot syncSnapshot) {
        SyncSnapshotDto snapshotDto = SyncMapper.INSTANCE.map(syncSnapshot);
        return ResponseEntity.ok(SyncMapper.INSTANCE.map(applySyncImportService.apply(snapshotDto)));
    }

    /**
     * Thrown by {@code ComputeMergePlanService} when the snapshot is malformed or its
     * {@code schemaVersion} isn't one this build recognizes (FR-010) — no partial import.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleMalformedSnapshot(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }
}
