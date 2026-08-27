package at.ymeri.my.finance.integration.tests;

import at.ymeri.my.finance.MyFinanceApplication;
import at.ymeri.my.finance.application.data.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = {MyFinanceApplication.class, TestConfig.class, TestDataSourceConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@EmbeddedKafka(
        partitions = 1,
        topics = {"booking.topic", "transaction.topic"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
public class RecurringSeriesControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String createCategory(String name) {
        CreateCategoryRequest req = new CreateCategoryRequest();
        req.setName(name);
        req.setType(CategoryType.EXPENSE);
        ResponseEntity<CategoryResponse> resp = restTemplate.postForEntity("/categories", req, CategoryResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resp.getBody().getId().toString();
    }

    private void createBill(String categoryId, String description, int year, int month, int day, double amount) {
        OffsetDateTime time = OffsetDateTime.of(year, month, day, 0, 0, 0, 0, ZoneOffset.UTC);
        Bill bill = new Bill().amount(amount).time(time).categoryId(categoryId).description(description);
        restTemplate.postForEntity("/createBill", bill, BillResponseModel.class);
    }

    // ── US2 (010): Recognize a Recurring Series ────────────────────────────────

    @Test
    void detect_threeMatchingBills_proposesASeries() {
        String catId = createCategory("Netflix-IT-010-US2a");
        createBill(catId, "Netflix-IT-010-US2a", 2035, 1, 15, 15.99);
        createBill(catId, "Netflix-IT-010-US2a", 2035, 2, 15, 15.99);
        createBill(catId, "Netflix-IT-010-US2a", 2035, 3, 15, 15.99);

        ResponseEntity<RecurringSeriesListResponse> response = restTemplate
                .postForEntity("/recurring-series/detect", null, RecurringSeriesListResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getSeries())
                .anyMatch(s -> "netflix-it-010-us2a".equals(s.getDescription())
                        && s.getStatus() == RecurringSeriesStatus.PROPOSED
                        && s.getGroupKey().equals(catId));
    }

    @Test
    void detect_calledTwice_doesNotDuplicateTheProposal() {
        String catId = createCategory("Spotify-IT-010-US2b");
        createBill(catId, "Spotify-IT-010-US2b", 2035, 4, 10, 9.99);
        createBill(catId, "Spotify-IT-010-US2b", 2035, 5, 10, 9.99);
        createBill(catId, "Spotify-IT-010-US2b", 2035, 6, 10, 9.99);

        restTemplate.postForEntity("/recurring-series/detect", null, RecurringSeriesListResponse.class);
        ResponseEntity<RecurringSeriesListResponse> second = restTemplate
                .postForEntity("/recurring-series/detect", null, RecurringSeriesListResponse.class);

        long matches = second.getBody().getSeries().stream()
                .filter(s -> "spotify-it-010-us2b".equals(s.getDescription()))
                .count();
        assertThat(matches).isEqualTo(1);
    }

    @Test
    void confirm_proposedSeries_transitionsToConfirmed() {
        String catId = createCategory("Gym-IT-010-US2c");
        createBill(catId, "Gym-IT-010-US2c", 2035, 1, 5, 40.00);
        createBill(catId, "Gym-IT-010-US2c", 2035, 2, 5, 40.00);
        createBill(catId, "Gym-IT-010-US2c", 2035, 3, 5, 40.00);
        restTemplate.postForEntity("/recurring-series/detect", null, RecurringSeriesListResponse.class);
        String seriesId = findSeriesId("gym-it-010-us2c");

        ResponseEntity<RecurringSeriesResponse> response = restTemplate
                .postForEntity("/recurring-series/" + seriesId + "/confirm", null, RecurringSeriesResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getStatus()).isEqualTo(RecurringSeriesStatus.CONFIRMED);
    }

    @Test
    void confirm_nonProposedSeries_returns400() {
        String catId = createCategory("Rent-IT-010-US2d");
        createBill(catId, "Rent-IT-010-US2d", 2035, 1, 1, 1200.00);
        createBill(catId, "Rent-IT-010-US2d", 2035, 2, 1, 1200.00);
        createBill(catId, "Rent-IT-010-US2d", 2035, 3, 1, 1200.00);
        restTemplate.postForEntity("/recurring-series/detect", null, RecurringSeriesListResponse.class);
        String seriesId = findSeriesId("rent-it-010-us2d");
        restTemplate.postForEntity("/recurring-series/" + seriesId + "/confirm", null, RecurringSeriesResponse.class);

        ResponseEntity<String> response = restTemplate
                .postForEntity("/recurring-series/" + seriesId + "/confirm", null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void dismiss_proposedAndConfirmedSeries_bothTransitionToDismissed() {
        String catId = createCategory("Cloud-IT-010-US2e");
        createBill(catId, "Cloud-IT-010-US2e", 2035, 1, 20, 5.00);
        createBill(catId, "Cloud-IT-010-US2e", 2035, 2, 20, 5.00);
        createBill(catId, "Cloud-IT-010-US2e", 2035, 3, 20, 5.00);
        restTemplate.postForEntity("/recurring-series/detect", null, RecurringSeriesListResponse.class);
        String seriesId = findSeriesId("cloud-it-010-us2e");

        ResponseEntity<RecurringSeriesResponse> dismissed = restTemplate
                .postForEntity("/recurring-series/" + seriesId + "/dismiss", null, RecurringSeriesResponse.class);

        assertThat(dismissed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dismissed.getBody().getStatus()).isEqualTo(RecurringSeriesStatus.DISMISSED);
    }

    @Test
    void dismiss_alreadyDismissedSeries_returns400() {
        String catId = createCategory("Water-IT-010-US2f");
        createBill(catId, "Water-IT-010-US2f", 2035, 1, 8, 30.00);
        createBill(catId, "Water-IT-010-US2f", 2035, 2, 8, 30.00);
        createBill(catId, "Water-IT-010-US2f", 2035, 3, 8, 30.00);
        restTemplate.postForEntity("/recurring-series/detect", null, RecurringSeriesListResponse.class);
        String seriesId = findSeriesId("water-it-010-us2f");
        restTemplate.postForEntity("/recurring-series/" + seriesId + "/dismiss", null, RecurringSeriesResponse.class);

        ResponseEntity<String> response = restTemplate
                .postForEntity("/recurring-series/" + seriesId + "/dismiss", null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── US1 (010): See What's Coming Up ────────────────────────────────────────

    @Test
    void dashboard_confirmedSeriesWithRecentOccurrence_returnsCorrectPrediction() {
        String catId = createCategory("Insurance-IT-010-US1a");
        OffsetDateTime lastOccurrence = OffsetDateTime.now(ZoneOffset.UTC).minusDays(5);
        createBillAt(catId, "Insurance-IT-010-US1a", lastOccurrence.minusMonths(2), 60.00);
        createBillAt(catId, "Insurance-IT-010-US1a", lastOccurrence.minusMonths(1), 60.00);
        createBillAt(catId, "Insurance-IT-010-US1a", lastOccurrence, 60.00);
        restTemplate.postForEntity("/recurring-series/detect", null, RecurringSeriesListResponse.class);
        String seriesId = findSeriesId("insurance-it-010-us1a");
        restTemplate.postForEntity("/recurring-series/" + seriesId + "/confirm", null, RecurringSeriesResponse.class);

        ResponseEntity<RecurringDashboardResponse> response = restTemplate
                .getForEntity("/recurring-series/dashboard", RecurringDashboardResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getUpcoming())
                .anyMatch(u -> "insurance-it-010-us1a".equals(u.getDescription())
                        && !u.getOverdue()
                        && u.getPredictedAmount().compareTo(BigDecimal.valueOf(60.00)) == 0);
    }

    @Test
    void dashboard_pastPredictionWithNoNewOccurrence_isMarkedOverdue() {
        String catId = createCategory("Membership-IT-010-US1b");
        // three occurrences a month apart, the last one two months ago -> predicted next date
        // (last + 1 month) is one month in the past, with nothing recorded since
        OffsetDateTime lastOccurrence = OffsetDateTime.now(ZoneOffset.UTC).minusMonths(2);
        createBillAt(catId, "Membership-IT-010-US1b", lastOccurrence.minusMonths(2), 25.00);
        createBillAt(catId, "Membership-IT-010-US1b", lastOccurrence.minusMonths(1), 25.00);
        createBillAt(catId, "Membership-IT-010-US1b", lastOccurrence, 25.00);
        restTemplate.postForEntity("/recurring-series/detect", null, RecurringSeriesListResponse.class);
        String seriesId = findSeriesId("membership-it-010-us1b");
        restTemplate.postForEntity("/recurring-series/" + seriesId + "/confirm", null, RecurringSeriesResponse.class);

        ResponseEntity<RecurringDashboardResponse> response = restTemplate
                .getForEntity("/recurring-series/dashboard", RecurringDashboardResponse.class);

        assertThat(response.getBody().getUpcoming())
                .anyMatch(u -> "membership-it-010-us1b".equals(u.getDescription()) && u.getOverdue());
    }

    @Test
    void dashboard_recordingNewOccurrence_advancesThePrediction() {
        String catId = createCategory("Storage-IT-010-US1c");
        OffsetDateTime first = OffsetDateTime.now(ZoneOffset.UTC).minusMonths(2);
        createBillAt(catId, "Storage-IT-010-US1c", first, 3.99);
        createBillAt(catId, "Storage-IT-010-US1c", first.plusMonths(1), 3.99);
        createBillAt(catId, "Storage-IT-010-US1c", first.plusMonths(2), 3.99);
        restTemplate.postForEntity("/recurring-series/detect", null, RecurringSeriesListResponse.class);
        String seriesId = findSeriesId("storage-it-010-us1c");
        restTemplate.postForEntity("/recurring-series/" + seriesId + "/confirm", null, RecurringSeriesResponse.class);

        OffsetDateTime newOccurrence = OffsetDateTime.now(ZoneOffset.UTC);
        createBillAt(catId, "Storage-IT-010-US1c", newOccurrence, 3.99);

        ResponseEntity<RecurringDashboardResponse> response = restTemplate
                .getForEntity("/recurring-series/dashboard", RecurringDashboardResponse.class);

        // predicted next date is now derived from the just-recorded occurrence, one month out —
        // strictly after the occurrence before it plus 29 days, confirming the prediction moved
        assertThat(response.getBody().getUpcoming())
                .anyMatch(u -> "storage-it-010-us1c".equals(u.getDescription())
                        && u.getPredictedDate().isAfter(newOccurrence.plusDays(29)));
    }

    // ── US3 (010): Get Warned About a Price Change ──────────────────────────────

    @Test
    void dashboard_lastOccurrenceAmountJumpsBeyondTolerance_reportsAPriceChangeAlert() {
        String catId = createCategory("Broadband-IT-010-US3a");
        // the series is first recognized from three consistent occurrences — a run whose last
        // step already jumped in price is not a series at all, so the price change has to be
        // recorded onto an existing confirmed series (spec 010 US3)
        OffsetDateTime first = OffsetDateTime.now(ZoneOffset.UTC).minusMonths(3);
        createBillAt(catId, "Broadband-IT-010-US3a", first, 29.99);
        createBillAt(catId, "Broadband-IT-010-US3a", first.plusMonths(1), 29.99);
        createBillAt(catId, "Broadband-IT-010-US3a", first.plusMonths(2), 29.99);
        restTemplate.postForEntity("/recurring-series/detect", null, RecurringSeriesListResponse.class);
        String seriesId = findSeriesId("broadband-it-010-us3a");
        restTemplate.postForEntity("/recurring-series/" + seriesId + "/confirm", null, RecurringSeriesResponse.class);
        createBillAt(catId, "Broadband-IT-010-US3a", first.plusMonths(3), 39.99);

        ResponseEntity<RecurringDashboardResponse> response = restTemplate
                .getForEntity("/recurring-series/dashboard", RecurringDashboardResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getRecentPriceChanges())
                .anyMatch(a -> "broadband-it-010-us3a".equals(a.getDescription())
                        && a.getPriorAmount().compareTo(BigDecimal.valueOf(29.99)) == 0
                        && a.getNewAmount().compareTo(BigDecimal.valueOf(39.99)) == 0
                        && a.getDelta().compareTo(BigDecimal.valueOf(10.00)) == 0);
    }

    @Test
    void dashboard_lastOccurrenceAmountUnchanged_reportsNoPriceChangeAlert() {
        String catId = createCategory("Housing-IT-010-US3b");
        OffsetDateTime first = OffsetDateTime.now(ZoneOffset.UTC).minusMonths(2);
        createBillAt(catId, "Housing-IT-010-US3b", first, 900.00);
        createBillAt(catId, "Housing-IT-010-US3b", first.plusMonths(1), 900.00);
        createBillAt(catId, "Housing-IT-010-US3b", first.plusMonths(2), 900.00);
        restTemplate.postForEntity("/recurring-series/detect", null, RecurringSeriesListResponse.class);
        String seriesId = findSeriesId("housing-it-010-us3b");
        restTemplate.postForEntity("/recurring-series/" + seriesId + "/confirm", null, RecurringSeriesResponse.class);

        ResponseEntity<RecurringDashboardResponse> response = restTemplate
                .getForEntity("/recurring-series/dashboard", RecurringDashboardResponse.class);

        assertThat(response.getBody().getRecentPriceChanges())
                .noneMatch(a -> "housing-it-010-us3b".equals(a.getDescription()));
    }

    private void createBillAt(String categoryId, String description, OffsetDateTime time, double amount) {
        Bill bill = new Bill().amount(amount).time(time).categoryId(categoryId).description(description);
        restTemplate.postForEntity("/createBill", bill, BillResponseModel.class);
    }

    private String findSeriesId(String description) {
        ResponseEntity<RecurringSeriesListResponse> list = restTemplate
                .getForEntity("/recurring-series", RecurringSeriesListResponse.class);
        return list.getBody().getSeries().stream()
                .filter(s -> description.equals(s.getDescription()))
                .findFirst()
                .map(s -> s.getId().toString())
                .orElseThrow();
    }
}
