package at.ymeri.my.finance.controller.statement;

import at.ymeri.my.finance.application.controller.statement.StatementIngestionApi;
import at.ymeri.my.finance.application.data.IngestionResult;
import at.ymeri.my.finance.application.data.RowStatus;
import at.ymeri.my.finance.application.data.StatementPreview;
import at.ymeri.my.finance.application.data.StatementRowOutcome;
import at.ymeri.my.finance.application.data.StatementRowPreview;
import at.ymeri.my.finance.application.data.TransactionDirection;
import at.ymeri.my.finance.domain.api.IngestTransactionsService;
import at.ymeri.my.finance.domain.api.ParseStatementService;
import at.ymeri.my.finance.domain.data.ingestion.RowOutcome;
import at.ymeri.my.finance.domain.data.ingestion.StatementRow;
import at.ymeri.my.finance.domain.service.ingestion.SuggestCategoryService;
import at.ymeri.my.finance.domain.spi.ingestion.IngestTransactionsPersistencePort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Server-side statement import: preview what a file would do, then commit it.
 *
 * <p><strong>Both endpoints take the file, including the commit.</strong> The obvious alternative —
 * preview returns rows carrying their identities, and the client posts those rows back — puts
 * identity values in the client's hands, and a client that echoes an identity is a client that can
 * echo a wrong one. The resulting row would carry an identity that no re-parse of the statement
 * would ever reproduce: permanently invisible to future deduplication, which is the one thing this
 * feature exists to guarantee. Re-uploading a file measured in kilobytes is the cheaper trade
 * (FR-010, 022 research R7).
 *
 * <p>That works only because parsing is deterministic — the same file yields byte-identical
 * identities every time, which {@code ParseStatementServiceImplTest} pins down.
 */
@RestController
public class StatementIngestionController implements StatementIngestionApi {

    private final ParseStatementService parseStatementService;
    private final IngestTransactionsService ingestTransactionsService;
    private final IngestTransactionsPersistencePort persistencePort;
    private final SuggestCategoryService suggestCategoryService;

    public StatementIngestionController(ParseStatementService parseStatementService,
                                        IngestTransactionsService ingestTransactionsService,
                                        IngestTransactionsPersistencePort persistencePort,
                                        SuggestCategoryService suggestCategoryService) {
        this.parseStatementService = parseStatementService;
        this.ingestTransactionsService = ingestTransactionsService;
        this.persistencePort = persistencePort;
        this.suggestCategoryService = suggestCategoryService;
    }

    @Override
    public ResponseEntity<StatementPreview> previewStatement(MultipartFile file, String accountId) {
        List<StatementRow> rows = parseStatementService.parse(readText(file), accountId);

        // Advisory read. These marks can be stale by the time the operator confirms, and nothing
        // depends on them being right: the uniqueness constraint applied during ingest is what
        // actually prevents double-counting (022 research R7).
        Set<String> alreadyPresent = persistencePort.existingIdentities(accountId,
                rows.stream().filter(row -> !row.isRejected()).map(StatementRow::externalId).toList());

        List<String> suggestions = suggestCategoryService.suggestFor(
                rows.stream().map(StatementRow::description).toList());

        StatementPreview preview = new StatementPreview();
        int newCount = 0;
        int alreadyRecordedCount = 0;
        int rejectedCount = 0;

        for (int i = 0; i < rows.size(); i++) {
            StatementRow row = rows.get(i);
            StatementRowPreview item = new StatementRowPreview();
            item.setRowIndex(row.rowIndex());

            if (row.isRejected()) {
                item.setStatus(RowStatus.REJECTED);
                item.setRejectionReason(row.rejectionReason());
                rejectedCount++;
            } else {
                item.setDate(row.date());
                item.setDescription(row.description());
                item.setAmount(row.amount().toPlainString());
                item.setDirection(TransactionDirection.fromValue(row.direction().name()));
                item.setSuggestedCategoryId(suggestions.get(i));
                if (alreadyPresent.contains(row.externalId())) {
                    item.setStatus(RowStatus.ALREADY_RECORDED);
                    alreadyRecordedCount++;
                } else {
                    item.setStatus(RowStatus.RECORDED);
                    newCount++;
                }
            }
            preview.addRowsItem(item);
        }

        preview.setNewCount(newCount);
        preview.setAlreadyRecordedCount(alreadyRecordedCount);
        preview.setRejectedCount(rejectedCount);
        return ResponseEntity.ok(preview);
    }

    @Override
    public ResponseEntity<IngestionResult> ingestStatement(MultipartFile file, String accountId,
                                                           List<Integer> excludedRowIndexes) {
        List<StatementRow> rows = parseStatementService.parse(readText(file), accountId);
        Set<Integer> excluded = excludedRowIndexes == null ? Set.of() : new HashSet<>(excludedRowIndexes);

        at.ymeri.my.finance.domain.data.ingestion.IngestionResult domainResult =
                ingestTransactionsService.ingest(rows, accountId, excluded);

        IngestionResult result = new IngestionResult();
        for (RowOutcome outcome : domainResult.rows()) {
            StatementRowOutcome item = new StatementRowOutcome();
            item.setRowIndex(outcome.rowIndex());
            item.setStatus(RowStatus.fromValue(outcome.status().name()));
            item.setRejectionReason(outcome.rejectionReason());
            item.setTransactionId(outcome.transactionId());
            result.addRowsItem(item);
        }
        result.setRecordedCount((int) domainResult.recordedCount());
        result.setAlreadyRecordedCount((int) domainResult.alreadyRecordedCount());
        result.setRejectedCount((int) domainResult.rejectedCount());
        result.setExcludedCount((int) domainResult.excludedCount());
        return ResponseEntity.ok(result);
    }

    private static String readText(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No statement file was uploaded.");
        }
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * A file that is not a readable statement fails the whole request, so nothing is recorded from
     * it — never a partial import (FR-015). Individual bad <em>rows</em> do not come through here;
     * they are reported per row as REJECTED with a reason.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleUnreadableStatement(IllegalArgumentException e) {
        return e.getMessage();
    }
}
