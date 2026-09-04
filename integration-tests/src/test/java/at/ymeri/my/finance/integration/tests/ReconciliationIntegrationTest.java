package at.ymeri.my.finance.integration.tests;

import at.ymeri.my.finance.MyFinanceApplication;
import at.ymeri.my.finance.application.data.AccountResponse;
import at.ymeri.my.finance.application.data.AccountType;
import at.ymeri.my.finance.application.data.Bill;
import at.ymeri.my.finance.application.data.BillListResponseModel;
import at.ymeri.my.finance.application.data.BillResponseModel;
import at.ymeri.my.finance.application.data.CategoryResponse;
import at.ymeri.my.finance.application.data.CategoryType;
import at.ymeri.my.finance.application.data.CreateAccountRequest;
import at.ymeri.my.finance.application.data.CreateCategoryRequest;
import at.ymeri.my.finance.application.data.IngestionResult;
import at.ymeri.my.finance.application.data.PostingRunResult;
import at.ymeri.my.finance.application.data.RecurringSeriesListResponse;
import at.ymeri.my.finance.application.data.RecurringSeriesResponse;
import at.ymeri.my.finance.application.data.RecurringSeriesStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What happens when the bank's own version of a predicted transaction arrives.
 *
 * <p><strong>The balance is the assertion that matters, not the row count.</strong> Three rows —
 * the prediction, its reversal, and the imported booking — is the correct outcome of a design that
 * never deletes anything (Principle I). Counting rows would call that a failure; counting money
 * says whether the operator's rent was charged once, which is the thing that would actually be
 * wrong.
 *
 * <p>Weekly rather than monthly for the same reason as {@link AutoPostIntegrationTest}: a weekly
 * step never clamps, so "the next occurrence is today" holds on every calendar date.
 */
@SpringBootTest(
        classes = {MyFinanceApplication.class, TestConfig.class, TestDataSourceConfig.class, TestSecurityConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@EmbeddedKafka(partitions = 1, topics = {"booking.topic", "transaction.topic"}, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
public class ReconciliationIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void anImportedTransactionSupersedesTheMatchingPredictionAndTheBalanceCountsItOnce() {
        String description = "Reconcile-IT-023-A-" + UUID.randomUUID();
        String account = anAccount();
        String category = aCategory(description);
        aWeeklyHistory(category, account, description, 1250.00);
        String seriesId = detectAndConfirm(description);

        postDue();
        Bill prediction = billsOn(account).stream()
                .filter(bill -> seriesId.equals(bill.getRecurringSeriesId()))
                .filter(bill -> !Boolean.TRUE.equals(bill.getReversal()))
                .findFirst().orElseThrow();
        double afterPosting = balanceOf(account);
        assertThat(afterPosting)
                .as("three real occurrences plus the one just posted")
                .isEqualTo(-5000.00);

        IngestionResult ingestion = ingest(account, """
                date,description,amount
                %s,DAUERAUFTRAG MIETE,-1250.00
                """.formatted(LocalDate.now()));
        assertThat(ingestion.getRecordedCount())
                .as("superseding a prediction is an additional effect, not a different outcome")
                .isEqualTo(1);

        assertThat(balanceOf(account))
                .as("the bank's booking replaces the prediction; the rent is charged once, not twice")
                .isEqualTo(afterPosting);

        List<Bill> bills = billsOn(account);
        Bill predictionAfterwards = bills.stream()
                .filter(bill -> prediction.getId().equals(bill.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the prediction must never be deleted"));
        assertThat(predictionAfterwards.getAmount()).isEqualTo(prediction.getAmount());
        assertThat(predictionAfterwards.getTime()).isEqualTo(prediction.getTime());
        assertThat(bills)
                .as("the supersession is a compensating entry referencing the prediction")
                .anyMatch(bill -> prediction.getId().toString().equals(bill.getCorrectsTransactionId())
                        && Boolean.TRUE.equals(bill.getReversal())
                        && bill.getAmount() == -1250.00);
    }

    @Test
    void anAmountWellOutsideToleranceLeavesThePredictionStanding() {
        // A rent rise this large is a different transaction as far as the app is concerned. The
        // operator sees both and decides; nothing is silently cancelled on their behalf.
        String description = "Reconcile-IT-023-B-" + UUID.randomUUID();
        String account = anAccount();
        String category = aCategory(description);
        aWeeklyHistory(category, account, description, 1250.00);
        detectAndConfirm(description);

        postDue();
        double afterPosting = balanceOf(account);

        ingest(account, """
                date,description,amount
                %s,DAUERAUFTRAG MIETE,-1600.00
                """.formatted(LocalDate.now()));

        assertThat(balanceOf(account))
                .as("no supersession, so the imported charge lands on top of the prediction")
                .isEqualTo(afterPosting - 1600.00);
        assertThat(billsOn(account))
                .noneMatch(bill -> Boolean.TRUE.equals(bill.getReversal()));
    }

    @Test
    void reImportingTheSameStatementDoesNotSupersedeTwice() {
        // Re-importing is expected — feature 022 exists precisely so it is safe. A second reversal
        // of one prediction would credit the operator money they never had.
        String description = "Reconcile-IT-023-C-" + UUID.randomUUID();
        String account = anAccount();
        String category = aCategory(description);
        aWeeklyHistory(category, account, description, 80.00);
        detectAndConfirm(description);
        postDue();

        String csv = """
                date,description,amount
                %s,GYM MEMBERSHIP,-80.00
                """.formatted(LocalDate.now());
        ingest(account, csv);
        double afterFirstImport = balanceOf(account);
        ingest(account, csv);

        assertThat(balanceOf(account)).isEqualTo(afterFirstImport);
        assertThat(billsOn(account).stream().filter(bill -> Boolean.TRUE.equals(bill.getReversal())))
                .hasSize(1);
    }

    // --- helpers ---------------------------------------------------------------------------------

    private void postDue() {
        ResponseEntity<PostingRunResult> response = restTemplate
                .postForEntity("/recurring-series/post-due", null, PostingRunResult.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void aWeeklyHistory(String categoryId, String accountId, String description, double amount) {
        OffsetDateTime last = OffsetDateTime.now(ZoneOffset.UTC).minusDays(7);
        createBillAt(categoryId, accountId, description, last.minusDays(14), amount);
        createBillAt(categoryId, accountId, description, last.minusDays(7), amount);
        createBillAt(categoryId, accountId, description, last, amount);
    }

    private String detectAndConfirm(String description) {
        restTemplate.postForEntity("/recurring-series/detect", null, RecurringSeriesListResponse.class);
        RecurringSeriesListResponse list = restTemplate
                .getForEntity("/recurring-series", RecurringSeriesListResponse.class).getBody();
        String seriesId = list.getSeries().stream()
                .filter(series -> description.toLowerCase().equals(series.getDescription()))
                .findFirst().orElseThrow().getId().toString();
        ResponseEntity<RecurringSeriesResponse> confirmed = restTemplate.postForEntity(
                "/recurring-series/" + seriesId + "/confirm", null, RecurringSeriesResponse.class);
        assertThat(confirmed.getBody().getStatus()).isEqualTo(RecurringSeriesStatus.CONFIRMED);
        return seriesId;
    }

    private IngestionResult ingest(String accountId, String csv) {
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

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<IngestionResult> response = restTemplate.exchange(
                "/statements/ingest", HttpMethod.POST, new HttpEntity<>(body, headers), IngestionResult.class);
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("ingest failed: %s", response)
                .isTrue();
        return response.getBody();
    }

    private double balanceOf(String accountId) {
        AccountResponse account = restTemplate
                .getForEntity("/accounts/" + accountId, AccountResponse.class).getBody();
        return account.getBalance();
    }

    private String anAccount() {
        ResponseEntity<AccountResponse> response = restTemplate.postForEntity("/accounts",
                new CreateAccountRequest("Reconcile " + UUID.randomUUID(), AccountType.CHECKING,
                        List.of("EUR"), "EUR"),
                AccountResponse.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        return response.getBody().getId().toString();
    }

    private String aCategory(String name) {
        CreateCategoryRequest request = new CreateCategoryRequest();
        request.setName(name);
        request.setType(CategoryType.EXPENSE);
        ResponseEntity<CategoryResponse> response = restTemplate
                .postForEntity("/categories", request, CategoryResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().getId().toString();
    }

    private void createBillAt(String categoryId, String accountId, String description,
                              OffsetDateTime time, double amount) {
        Bill bill = new Bill().amount(amount).time(time).categoryId(categoryId)
                .accountId(accountId).description(description);
        assertThat(restTemplate.postForEntity("/createBill", bill, BillResponseModel.class)
                .getStatusCode().is2xxSuccessful()).isTrue();
    }

    private List<Bill> billsOn(String accountId) {
        BillListResponseModel list = restTemplate
                .getForEntity("/bills", BillListResponseModel.class).getBody();
        if (list == null || list.getBills() == null) {
            return List.of();
        }
        return list.getBills().stream()
                .filter(bill -> accountId.equals(bill.getAccountId()))
                .toList();
    }
}
