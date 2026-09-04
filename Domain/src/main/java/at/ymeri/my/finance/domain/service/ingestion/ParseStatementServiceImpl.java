package at.ymeri.my.finance.domain.service.ingestion;

import at.ymeri.my.finance.domain.api.ParseStatementService;
import at.ymeri.my.finance.domain.data.ingestion.StatementRow;
import at.ymeri.my.finance.domain.data.ingestion.TransactionDirection;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses a CSV bank statement into rows, assigning each its external identity.
 *
 * <p><strong>Occurrence indices are assigned here, over the whole file, before anything is excluded
 * or written.</strong> That ordering is load-bearing (022 research R8): if exclusion renumbered,
 * then excluding the first of two identical rows and re-importing later would offer the row the
 * operator <em>kept</em> and hide the one they <em>rejected</em> — precisely inverted, and quiet.
 *
 * <p>A row that cannot be used is returned rejected with a reason rather than dropped, and does not
 * stop the rows around it (FR-011). Only a file that is not a statement at all fails outright, in
 * which case nothing is recorded (FR-015).
 *
 * <p>Uses commons-csv rather than splitting on commas: quoted fields containing commas, escaped
 * quotes, and newlines inside quoted fields are exactly what a hand-rolled parser gets wrong, and
 * the failure mode is a silently mis-parsed financial record.
 */
@Service
public class ParseStatementServiceImpl implements ParseStatementService {

    private static final String DATE_HEADER = "date";
    private static final String DESCRIPTION_HEADER = "description";
    private static final String AMOUNT_HEADER = "amount";
    /** Optional. When the bank supplies its own identifier it is preferred over the derived hash. */
    private static final String TRANSACTION_ID_HEADER = "transactionid";

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreHeaderCase(true)
            .setTrim(true)
            .setIgnoreEmptyLines(true)
            .build();

    @Override
    public List<StatementRow> parse(String csvText, String accountId) {
        if (csvText == null || csvText.isBlank()) {
            throw new IllegalArgumentException("The statement file is empty.");
        }

        List<CSVRecord> records;
        List<String> headers;
        try (CSVParser parser = CSVParser.parse(new StringReader(csvText), FORMAT)) {
            headers = parser.getHeaderNames().stream().map(h -> h.toLowerCase().replace(" ", "")).toList();
            records = parser.getRecords();
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "This file could not be read as a CSV statement. Expected a header row with "
                            + "'date', 'description' and 'amount' columns.", e);
        }

        if (!headers.contains(DATE_HEADER) || !headers.contains(AMOUNT_HEADER)) {
            throw new IllegalArgumentException(
                    "This file is missing the columns a statement needs. Expected a header row "
                            + "containing at least 'date' and 'amount'; found: " + String.join(", ", headers));
        }

        // Occurrence counters, keyed by identity group. Deliberately assigned over every parsed row,
        // including ones the operator later excludes — see the class comment.
        Map<String, Integer> occurrences = new HashMap<>();
        List<StatementRow> rows = new ArrayList<>(records.size());

        for (int index = 0; index < records.size(); index++) {
            rows.add(toRow(records.get(index), index, accountId, occurrences));
        }
        return rows;
    }

    private StatementRow toRow(CSVRecord record, int rowIndex, String accountId,
                               Map<String, Integer> occurrences) {
        String rawDate = valueOf(record, DATE_HEADER);
        String description = valueOf(record, DESCRIPTION_HEADER);
        String rawAmount = valueOf(record, AMOUNT_HEADER);
        String sourceTransactionId = valueOf(record, TRANSACTION_ID_HEADER);

        LocalDate date;
        try {
            date = LocalDate.parse(rawDate);
        } catch (DateTimeParseException | NullPointerException e) {
            return rejected(rowIndex, "Could not read the date '" + rawDate + "'. Expected YYYY-MM-DD.");
        }

        BigDecimal signedAmount;
        try {
            signedAmount = new BigDecimal(rawAmount.replace(" ", ""));
        } catch (NumberFormatException | NullPointerException e) {
            return rejected(rowIndex, "Could not read the amount '" + rawAmount + "'.");
        }

        if (signedAmount.signum() == 0) {
            return rejected(rowIndex, "The amount is zero, which is not a transaction.");
        }

        // The sign says which side of the ledger; the stored amount is always positive, because that
        // is how both tables model it (Principle IV keeps it BigDecimal throughout).
        TransactionDirection direction =
                signedAmount.signum() < 0 ? TransactionDirection.BILL : TransactionDirection.INCOME;
        BigDecimal amount = signedAmount.abs();

        String identity = ExternalIdentityFactory.identityFor(
                accountId, date, amount, description, direction, sourceTransactionId);

        if (sourceTransactionId == null || sourceTransactionId.isBlank()) {
            // Derived identities need the occurrence suffix that keeps genuinely repeated
            // transactions apart. A bank-supplied identifier is already unique and must not be
            // altered.
            int occurrence = occurrences.merge(identity, 1, Integer::sum) - 1;
            identity = identity + ":" + occurrence;
        }

        return new StatementRow(rowIndex, date, description, amount, direction,
                sourceTransactionId, identity, null);
    }

    private static StatementRow rejected(int rowIndex, String reason) {
        return new StatementRow(rowIndex, null, null, null, null, null, null, reason);
    }

    /** Missing optional columns read as null rather than throwing. */
    private static String valueOf(CSVRecord record, String header) {
        try {
            return record.isSet(header) ? record.get(header) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
