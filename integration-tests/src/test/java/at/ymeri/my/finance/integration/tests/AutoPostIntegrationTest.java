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
import at.ymeri.my.finance.application.data.PostingRunResult;
import at.ymeri.my.finance.application.data.RecurringSeriesListResponse;
import at.ymeri.my.finance.application.data.RecurringSeriesResponse;
import at.ymeri.my.finance.application.data.RecurringSeriesState;
import at.ymeri.my.finance.application.data.RecurringSeriesStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature 023 against a real PostgreSQL. The calendar arithmetic is covered by fast Domain tests;
 * what needs a database is the guarantee that makes catch-up safe — an occurrence already recorded
 * is refused by the unique index built in feature 022, not by a check in application code.
 *
 * <p><strong>Every test uses a weekly series anchored exactly seven days back.</strong> Weekly steps
 * never clamp, so "the next occurrence is today" holds on every calendar date; a monthly series
 * anchored on the 31st would make these tests pass or fail depending on the day CI happened to run.
 *
 * <p>Each test creates its own account, category and description, so the shared container's
 * accumulated state cannot make one test's assertions depend on another's.
 */
@SpringBootTest(
        classes = {MyFinanceApplication.class, TestConfig.class, TestDataSourceConfig.class, TestSecurityConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@EmbeddedKafka(partitions = 1, topics = {"booking.topic", "transaction.topic"}, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
public class AutoPostIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    // --- US1: a confirmed series posts what it is due ------------------------------------------

    @Test
    void aConfirmedSeriesPostsItsDueOccurrence() {
        String description = "AutoPost-IT-023-A-" + UUID.randomUUID();
        String account = anAccount();
        String category = aCategory(description);
        aWeeklyHistory(category, account, description, 12.50);
        String seriesId = detectAndConfirm(description);

        PostingRunResult result = postDue();

        assertThat(result.getPostedCount()).isGreaterThanOrEqualTo(1);
        List<Bill> posted = billsOn(account, description).stream()
                .filter(bill -> seriesId.equals(bill.getRecurringSeriesId()))
                .toList();
        assertThat(posted).hasSize(1);
        assertThat(posted.get(0).getAmount()).isEqualTo(12.50);
        assertThat(posted.get(0).getTime().atZoneSameInstant(ZoneOffset.UTC).toLocalDate())
                .isEqualTo(LocalDate.now());
        assertThat(posted.get(0).getCategoryId()).isEqualTo(category);
    }

    @Test
    void aSecondRunPostsNothingFurther() {
        // The guarantee only a database can demonstrate: the second attempt writes the identical
        // identity and is refused by the unique index, rather than being skipped by a lookup that
        // could be stale. Without it, an operator running catch-up twice would pay their rent twice.
        String description = "AutoPost-IT-023-B-" + UUID.randomUUID();
        String account = anAccount();
        String category = aCategory(description);
        aWeeklyHistory(category, account, description, 30.00);
        detectAndConfirm(description);

        postDue();
        long afterFirst = billsOn(account, description).size();
        postDue();

        assertThat(billsOn(account, description)).hasSize((int) afterFirst);
        assertThat(afterFirst)
                .as("three real occurrences plus the one this run posted")
                .isEqualTo(4);
    }

    @Test
    void aProposedSeriesIsLeftAlone() {
        // Detection is a suggestion. Posting for something the operator never confirmed would put
        // transactions in their ledger on the strength of the app's own guess.
        String description = "AutoPost-IT-023-C-" + UUID.randomUUID();
        String account = anAccount();
        String category = aCategory(description);
        aWeeklyHistory(category, account, description, 8.00);
        restTemplate.postForEntity("/recurring-series/detect", null, RecurringSeriesListResponse.class);

        postDue();

        assertThat(billsOn(account, description))
                .as("nothing may be posted for a series that is only proposed")
                .hasSize(3);
    }

    // --- US4: stopping ---------------------------------------------------------------------------

    @Test
    void stoppingASeriesEndsItsPosting() {
        String description = "AutoPost-IT-023-D-" + UUID.randomUUID();
        String account = anAccount();
        String category = aCategory(description);
        aWeeklyHistory(category, account, description, 45.00);
        String seriesId = detectAndConfirm(description);

        ResponseEntity<RecurringSeriesState> stopped = restTemplate.postForEntity(
                "/recurring-series/" + seriesId + "/stop", null, RecurringSeriesState.class);
        assertThat(stopped.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stopped.getBody().getStatus()).isEqualTo(RecurringSeriesStatus.STOPPED);

        postDue();

        assertThat(billsOn(account, description))
                .as("a stopped series posts nothing, even for an occurrence that is due")
                .hasSize(3);
    }

    @Test
    void stoppingLeavesAlreadyPostedTransactionsAndTheSeriesInPlace() {
        // A cancelled subscription is not a mistake to be erased. What was posted really happened as
        // far as the ledger is concerned (Principle I), and the series stays listed so the operator
        // can still see its history — which is what makes stopping different from dismissing.
        String description = "AutoPost-IT-023-E-" + UUID.randomUUID();
        String account = anAccount();
        String category = aCategory(description);
        aWeeklyHistory(category, account, description, 19.99);
        String seriesId = detectAndConfirm(description);
        postDue();
        Bill postedBefore = billsOn(account, description).stream()
                .filter(bill -> seriesId.equals(bill.getRecurringSeriesId()))
                .findFirst().orElseThrow();

        restTemplate.postForEntity("/recurring-series/" + seriesId + "/stop", null, RecurringSeriesState.class);
        postDue();

        List<Bill> after = billsOn(account, description);
        assertThat(after).hasSize(4);
        Bill postedAfter = after.stream()
                .filter(bill -> postedBefore.getId().equals(bill.getId()))
                .findFirst().orElseThrow();
        assertThat(postedAfter.getAmount()).isEqualTo(postedBefore.getAmount());
        assertThat(postedAfter.getTime()).isEqualTo(postedBefore.getTime());
        assertThat(seriesFor(description).getStatus()).isEqualTo(RecurringSeriesStatus.STOPPED);
    }

    @Test
    void stoppingASeriesThatWasNeverConfirmedIsRejected() {
        String description = "AutoPost-IT-023-F-" + UUID.randomUUID();
        String account = anAccount();
        String category = aCategory(description);
        aWeeklyHistory(category, account, description, 6.00);
        restTemplate.postForEntity("/recurring-series/detect", null, RecurringSeriesListResponse.class);
        String seriesId = seriesFor(description).getId().toString();

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/recurring-series/" + seriesId + "/stop", null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void stoppingAnUnknownSeriesIsNotFound() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/recurring-series/" + UUID.randomUUID() + "/stop", null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // --- helpers ---------------------------------------------------------------------------------

    private PostingRunResult postDue() {
        ResponseEntity<PostingRunResult> response = restTemplate
                .postForEntity("/recurring-series/post-due", null, PostingRunResult.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    /** Three occurrences a week apart, the most recent exactly seven days ago. */
    private void aWeeklyHistory(String categoryId, String accountId, String description, double amount) {
        OffsetDateTime last = OffsetDateTime.now(ZoneOffset.UTC).minusDays(7);
        createBillAt(categoryId, accountId, description, last.minusDays(14), amount);
        createBillAt(categoryId, accountId, description, last.minusDays(7), amount);
        createBillAt(categoryId, accountId, description, last, amount);
    }

    private String detectAndConfirm(String description) {
        restTemplate.postForEntity("/recurring-series/detect", null, RecurringSeriesListResponse.class);
        String seriesId = seriesFor(description).getId().toString();
        ResponseEntity<RecurringSeriesResponse> confirmed = restTemplate.postForEntity(
                "/recurring-series/" + seriesId + "/confirm", null, RecurringSeriesResponse.class);
        assertThat(confirmed.getBody().getStatus()).isEqualTo(RecurringSeriesStatus.CONFIRMED);
        return seriesId;
    }

    private RecurringSeriesResponse seriesFor(String description) {
        RecurringSeriesListResponse list = restTemplate
                .getForEntity("/recurring-series", RecurringSeriesListResponse.class).getBody();
        return list.getSeries().stream()
                .filter(series -> description.toLowerCase().equals(series.getDescription()))
                .findFirst().orElseThrow();
    }

    private String anAccount() {
        ResponseEntity<AccountResponse> response = restTemplate.postForEntity("/accounts",
                new CreateAccountRequest("AutoPost " + UUID.randomUUID(), AccountType.CHECKING,
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

    /** Every bill on this account whose description matches, auto-posted ones included. */
    private List<Bill> billsOn(String accountId, String description) {
        BillListResponseModel list = restTemplate
                .getForEntity("/bills", BillListResponseModel.class).getBody();
        if (list == null || list.getBills() == null) {
            return List.of();
        }
        return list.getBills().stream()
                .filter(bill -> accountId.equals(bill.getAccountId()))
                .filter(bill -> description.equalsIgnoreCase(bill.getDescription()))
                .toList();
    }
}
