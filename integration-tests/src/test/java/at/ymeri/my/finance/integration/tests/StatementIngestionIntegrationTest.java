package at.ymeri.my.finance.integration.tests;

import at.ymeri.my.finance.MyFinanceApplication;
import at.ymeri.my.finance.application.data.AccountResponse;
import at.ymeri.my.finance.application.data.AccountType;
import at.ymeri.my.finance.application.data.CreateAccountRequest;
import at.ymeri.my.finance.application.data.Bill;
import at.ymeri.my.finance.application.data.BillListResponseModel;
import at.ymeri.my.finance.application.data.IngestionResult;
import at.ymeri.my.finance.application.data.RowStatus;
import at.ymeri.my.finance.application.data.StatementPreview;
import at.ymeri.my.finance.application.data.StatementRowPreview;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The guarantees of feature 022, against a real PostgreSQL — which is the only place they can be
 * checked. The identity algorithm underneath is covered by fast Domain tests; what needs a database
 * is the partial unique index and the {@code ON CONFLICT} behaviour built on it.
 *
 * <p>Every test uses its own account, so the shared container's accumulated state cannot make one
 * test's assertions depend on another's.
 */
@SpringBootTest(
        classes = {MyFinanceApplication.class, TestConfig.class, TestDataSourceConfig.class, TestSecurityConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@EmbeddedKafka(partitions = 1, topics = {"booking.topic", "transaction.topic"}, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
public class StatementIngestionIntegrationTest {

    /** 1–31 January: one coffee on the 15th. */
    private static final String OVERLAP_A = """
            date,description,amount
            2026-01-10,SUPERMARKET,-54.20
            2026-01-15,COFFEE BAR,-3.40
            2026-01-28,SALARY,2400.00
            """;

    /** 15 January – 15 February: TWO coffees on the 15th, and the same salary. */
    private static final String OVERLAP_B = """
            date,description,amount
            2026-01-15,COFFEE BAR,-3.40
            2026-01-15,COFFEE BAR,-3.40
            2026-01-28,SALARY,2400.00
            2026-02-03,PHARMACY,-18.90
            """;

    @Autowired
    private TestRestTemplate restTemplate;

    // --- US1: re-import is a no-op -------------------------------------------------------------

    @Test
    void reImportingTheIdenticalStatementRecordsNothing() {
        String account = anAccount();

        IngestionResult first = ingest(account, OVERLAP_A);
        assertThat(first.getRecordedCount()).isEqualTo(3);
        assertThat(first.getAlreadyRecordedCount()).isZero();

        IngestionResult second = ingest(account, OVERLAP_A);
        assertThat(second.getRecordedCount())
                .as("a second import of the same file must write nothing")
                .isZero();
        assertThat(second.getAlreadyRecordedCount()).isEqualTo(3);
    }

    // --- US1 + US2: overlapping statements converge, in either order ---------------------------

    @Test
    void overlappingStatementsConvergeWhenTheSmallerArrivesFirst() {
        String account = anAccount();

        ingest(account, OVERLAP_A);
        ingest(account, OVERLAP_B);

        assertThat(billCount(account, "COFFEE BAR")).isEqualTo(2);
        assertThat(billCount(account, "SUPERMARKET")).isEqualTo(1);
        assertThat(billCount(account, "PHARMACY")).isEqualTo(1);
    }

    @Test
    void overlappingStatementsConvergeWhenTheLargerArrivesFirst() {
        // The order-independence SC-002 actually claims: an operator importing a year of monthly
        // statements in whatever order they downloaded them must land on one history.
        String account = anAccount();

        ingest(account, OVERLAP_B);
        ingest(account, OVERLAP_A);

        assertThat(billCount(account, "COFFEE BAR")).isEqualTo(2);
        assertThat(billCount(account, "SUPERMARKET")).isEqualTo(1);
        assertThat(billCount(account, "PHARMACY")).isEqualTo(1);
    }

    // --- US2: repeated genuine transactions both survive ---------------------------------------

    @Test
    void twoIdenticalRowsBothBecomeTransactionsAndStayTwoOnReImport() {
        // The quietest possible failure: a history that dropped the second coffee still looks
        // entirely plausible, and is €3.40 wrong forever.
        String account = anAccount();

        IngestionResult first = ingest(account, OVERLAP_B);
        assertThat(first.getRecordedCount()).isEqualTo(4);
        assertThat(billCount(account, "COFFEE BAR")).isEqualTo(2);

        IngestionResult second = ingest(account, OVERLAP_B);
        assertThat(second.getRecordedCount()).isZero();
        assertThat(billCount(account, "COFFEE BAR")).isEqualTo(2);
    }

    // --- US3: history that predates the feature is untouched -----------------------------------

    @Test
    void handEnteredTransactionsCarryNoIdentityAndNeverCollide() {
        // Two hand-entered transactions identical in every field must both persist: they have no
        // external identity, and the index is partial precisely so nulls never collide.
        String account = anAccount();

        for (int i = 0; i < 2; i++) {
            createBillByHand(account, "HAND ENTERED", 9.99, "2026-03-01T10:00:00Z");
        }

        assertThat(billCount(account, "HAND ENTERED")).isEqualTo(2);
    }

    @Test
    void aHandEnteredTransactionIsNotRecognisedByAnImportCoveringTheSamePeriod() {
        // FR-008 / US3 scenario 3: the system must not claim to recognise history it never ingested.
        String account = anAccount();
        createBillByHand(account, "SUPERMARKET", 54.20, "2026-01-10T00:00:00Z");

        IngestionResult result = ingest(account, OVERLAP_A);

        assertThat(result.getRecordedCount())
                .as("the hand-entered row has no identity, so the imported one is genuinely new")
                .isEqualTo(3);
        assertThat(billCount(account, "SUPERMARKET")).isEqualTo(2);
    }

    // --- US4: preview, and exclusions that do not renumber -------------------------------------

    @Test
    void previewReportsWhatWouldHappenAndWritesNothing() {
        String account = anAccount();

        StatementPreview preview = preview(account, OVERLAP_A);
        assertThat(preview.getNewCount()).isEqualTo(3);
        assertThat(preview.getAlreadyRecordedCount()).isZero();
        assertThat(billCount(account, "SUPERMARKET"))
                .as("preview must not write")
                .isZero();

        ingest(account, OVERLAP_A);

        StatementPreview afterwards = preview(account, OVERLAP_A);
        assertThat(afterwards.getNewCount()).isZero();
        assertThat(afterwards.getAlreadyRecordedCount()).isEqualTo(3);
    }

    @Test
    void anExcludedRowIsOfferedAgainAsNewAndTheKeptOneIsNot() {
        // The observable consequence of assigning occurrence indices before exclusions. If exclusion
        // renumbered, this would come back exactly inverted: the kept row offered as new, the
        // rejected one hidden.
        String account = anAccount();

        StatementPreview before = preview(account, OVERLAP_B);
        assertThat(rowsWithStatus(before, RowStatus.RECORDED)).hasSize(4);

        IngestionResult result = ingest(account, OVERLAP_B, List.of(0));
        assertThat(result.getExcludedCount()).isEqualTo(1);
        assertThat(result.getRecordedCount()).isEqualTo(3);
        assertThat(billCount(account, "COFFEE BAR")).isEqualTo(1);

        StatementPreview after = preview(account, OVERLAP_B);
        assertThat(statusOfRow(after, 0))
                .as("the excluded row must be offered again")
                .isEqualTo(RowStatus.RECORDED);
        assertThat(statusOfRow(after, 1))
                .as("the row that was kept must not be offered again")
                .isEqualTo(RowStatus.ALREADY_RECORDED);
    }

    // --- What an imported row must look like once it is in the database -------------------------

    @Test
    void anImportedIncomeCanStillBeReadBackAfterwards() {
        // Regression. The ingest path writes with hand-written SQL, and its income column list once
        // omitted `recurring` — which maps to a primitive boolean, so every imported income landed
        // as NULL and every LATER read of the income table threw. The import itself succeeded; what
        // broke was account balances, budgets, savings goals and the next import, days later, with
        // nothing to connect them to the statement that caused it.
        //
        // Reading the balance back is the assertion because that is where an operator would meet it.
        String account = anAccount();

        ingest(account, OVERLAP_A);

        AccountResponse afterwards = restTemplate
                .getForEntity("/accounts/" + account, AccountResponse.class).getBody();
        assertThat(afterwards).isNotNull();
        assertThat(afterwards.getBalance())
                .as("2400.00 salary less 54.20 and 3.40 of spending")
                .isEqualTo(2342.40);
    }

    // --- Rejected rows and unusable files -------------------------------------------------------

    @Test
    void oneUnusableRowIsReportedAndDoesNotBlockItsNeighbours() {
        String account = anAccount();

        IngestionResult result = ingest(account, """
                date,description,amount
                2026-04-10,SUPERMARKET,-54.20
                not-a-date,BROKEN ROW,-1.00
                2026-04-28,SALARY,2400.00
                """);

        assertThat(result.getRecordedCount()).isEqualTo(2);
        assertThat(result.getRejectedCount()).isEqualTo(1);
        assertThat(result.getRows().stream()
                .filter(row -> row.getStatus() == RowStatus.REJECTED)
                .findFirst().orElseThrow().getRejectionReason())
                .contains("not-a-date");
    }

    @Test
    void aFileThatIsNotAStatementFailsAndRecordsNothing() {
        String account = anAccount();

        ResponseEntity<String> response = restTemplate.exchange(
                "/statements/ingest", org.springframework.http.HttpMethod.POST,
                multipart("name,favourite_colour\nalice,blue\n", account, null), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(billCount(account, "alice")).isZero();
    }

    // --- helpers --------------------------------------------------------------------------------

    private String anAccount() {
        ResponseEntity<AccountResponse> response = restTemplate.postForEntity("/accounts",
                new CreateAccountRequest("Statement " + UUID.randomUUID(), AccountType.CHECKING,
                        List.of("EUR"), "EUR"),
                AccountResponse.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        return response.getBody().getId().toString();
    }

    private IngestionResult ingest(String accountId, String csv) {
        return ingest(accountId, csv, null);
    }

    private IngestionResult ingest(String accountId, String csv, List<Integer> excluded) {
        ResponseEntity<IngestionResult> response = restTemplate.exchange(
                "/statements/ingest", org.springframework.http.HttpMethod.POST,
                multipart(csv, accountId, excluded), IngestionResult.class);
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("ingest failed: %s", response)
                .isTrue();
        return response.getBody();
    }

    private StatementPreview preview(String accountId, String csv) {
        ResponseEntity<StatementPreview> response = restTemplate.exchange(
                "/statements/preview", org.springframework.http.HttpMethod.POST,
                multipart(csv, accountId, null), StatementPreview.class);
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("preview failed: %s", response)
                .isTrue();
        return response.getBody();
    }

    private HttpEntity<MultiValueMap<String, Object>> multipart(String csv, String accountId,
                                                                List<Integer> excluded) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.TEXT_PLAIN);
        body.add("file", new HttpEntity<>(new ByteArrayResource(csv.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "statement.csv";
            }
        }, fileHeaders));

        body.add("accountId", accountId);

        if (excluded != null) {
            // The generated delegate takes this as a @RequestPart, so it travels as a JSON part
            // rather than as repeated form fields.
            HttpHeaders jsonHeaders = new HttpHeaders();
            jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
            body.add("excludedRowIndexes", new HttpEntity<>(excluded, jsonHeaders));
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return new HttpEntity<>(body, headers);
    }

    private void createBillByHand(String accountId, String description, double amount, String time) {
        Bill bill = new Bill()
                .description(description)
                .amount(amount)
                .time(java.time.OffsetDateTime.parse(time))
                .accountId(accountId);
        assertThat(restTemplate.postForEntity("/createBill", bill, Object.class)
                .getStatusCode().is2xxSuccessful()).isTrue();
    }

    /** Counts bills on an account whose description matches, read back through the API. */
    private long billCount(String accountId, String description) {
        BillListResponseModel list = restTemplate
                .getForEntity("/bills", BillListResponseModel.class).getBody();
        if (list == null || list.getBills() == null) {
            return 0;
        }
        return list.getBills().stream()
                .filter(bill -> accountId.equals(bill.getAccountId()))
                .filter(bill -> description.equals(bill.getDescription()))
                .count();
    }

    private static List<StatementRowPreview> rowsWithStatus(StatementPreview preview, RowStatus status) {
        return preview.getRows().stream().filter(row -> row.getStatus() == status).toList();
    }

    private static RowStatus statusOfRow(StatementPreview preview, int rowIndex) {
        return preview.getRows().stream()
                .filter(row -> row.getRowIndex() == rowIndex)
                .findFirst().orElseThrow().getStatus();
    }
}
