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
