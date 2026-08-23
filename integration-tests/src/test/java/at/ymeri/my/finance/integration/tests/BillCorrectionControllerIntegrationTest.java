package at.ymeri.my.finance.integration.tests;

import at.ymeri.my.finance.MyFinanceApplication;
import at.ymeri.my.finance.application.data.Bill;
import at.ymeri.my.finance.application.data.BillHistoryResponse;
import at.ymeri.my.finance.application.data.BillListResponseModel;
import at.ymeri.my.finance.application.data.BillResponseModel;
import at.ymeri.my.finance.application.data.CorrectBillRequest;
import at.ymeri.my.finance.application.data.MonthlySummaryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = {MyFinanceApplication.class, TestConfig.class, TestDataSourceConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@EmbeddedKafka(partitions = 1, topics = {"booking.topic", "transaction.topic"}, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
public class BillCorrectionControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    // --- correction ---

    @Test
    void correctBill_leavesTheOriginalRowUntouched() {
        UUID originalId = createBill("50.00", "2026-03-05T10:00:00Z", "cat-untouched");

        correct(originalId, 65.0, "2026-03-05T10:00:00Z", "cat-untouched");

        Bill original = restTemplate
                .getForEntity("/bill/" + originalId, BillResponseModel.class).getBody().getBill();
        assertThat(original.getAmount()).isEqualTo(50.0);
        assertThat(original.getReversal()).isNotEqualTo(Boolean.TRUE);
        assertThat(original.getCorrectsTransactionId()).isNull();
    }

    @Test
    void correctBill_listShowsOnlyTheCorrectedValue() {
        UUID originalId = createBill("50.00", "2026-04-05T10:00:00Z", "cat-listcheck");

        UUID correctedId = correct(originalId, 65.0, "2026-04-05T10:00:00Z", "cat-listcheck");

        BillListResponseModel list = restTemplate
                .getForEntity("/bills", BillListResponseModel.class).getBody();
        assertThat(list.getBills()).extracting(Bill::getId)
                .contains(correctedId)
                .doesNotContain(originalId);
    }

    @Test
    void correctBill_monthlySummaryReflectsOnlyTheCorrectedAmount() {
        createBill("50.00", "2026-05-05T10:00:00Z", "cat-summary");
        UUID originalId = lastBillFor("cat-summary");

        correct(originalId, 65.0, "2026-05-05T10:00:00Z", "cat-summary");

        // 50 (original) - 50 (reversal) + 65 (replacement) = 65, not 115
        assertThat(categorySpend(2026, 5, "cat-summary")).isEqualTo(65.0);
    }

    @Test
    void correctBill_twice_chainsOntoTheMostRecentValue() {
        UUID originalId = createBill("50.00", "2026-06-05T10:00:00Z", "cat-chain");

        UUID firstCorrection = correct(originalId, 65.0, "2026-06-05T10:00:00Z", "cat-chain");
        UUID secondCorrection = correct(firstCorrection, 80.0, "2026-06-05T10:00:00Z", "cat-chain");

        assertThat(categorySpend(2026, 6, "cat-chain")).isEqualTo(80.0);

        BillListResponseModel list = restTemplate
                .getForEntity("/bills", BillListResponseModel.class).getBody();
        assertThat(list.getBills()).extracting(Bill::getId)
                .contains(secondCorrection)
                .doesNotContain(originalId, firstCorrection);
    }

    @Test
    void correctBill_movingTheDateIntoAnotherMonth_movesTheAmountBetweenMonths() {
        UUID originalId = createBill("50.00", "2026-07-05T10:00:00Z", "cat-crossmonth");
        assertThat(categorySpend(2026, 7, "cat-crossmonth")).isEqualTo(50.0);

        correct(originalId, 50.0, "2026-08-05T10:00:00Z", "cat-crossmonth");

        assertThat(categorySpend(2026, 7, "cat-crossmonth")).isEqualTo(0.0);
        assertThat(categorySpend(2026, 8, "cat-crossmonth")).isEqualTo(50.0);
    }

    @Test
    void correctBill_unknownId_returns404() {
        CorrectBillRequest request = new CorrectBillRequest(65.0, OffsetDateTime.parse("2026-03-05T10:00:00Z"));
        ResponseEntity<String> response = restTemplate.exchange(
                "/bills/" + UUID.randomUUID(), HttpMethod.PUT, new HttpEntity<>(request), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void correctBill_alreadyCorrectedRow_returns409() {
        UUID originalId = createBill("50.00", "2026-09-05T10:00:00Z", "cat-conflict");
        correct(originalId, 65.0, "2026-09-05T10:00:00Z", "cat-conflict");

        CorrectBillRequest request = new CorrectBillRequest(80.0, OffsetDateTime.parse("2026-09-05T10:00:00Z"));
        ResponseEntity<String> response = restTemplate.exchange(
                "/bills/" + originalId, HttpMethod.PUT, new HttpEntity<>(request), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void correctBill_amountZero_returns400() {
        UUID originalId = createBill("50.00", "2026-10-05T10:00:00Z", "cat-invalid");

        CorrectBillRequest request = new CorrectBillRequest(0.0, OffsetDateTime.parse("2026-10-05T10:00:00Z"));
        ResponseEntity<String> response = restTemplate.exchange(
                "/bills/" + originalId, HttpMethod.PUT, new HttpEntity<>(request), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // --- removal ---

    @Test
    void removeBill_returns204AndLeavesTheOriginalRowUntouched() {
        UUID originalId = createBill("30.00", "2026-11-05T10:00:00Z", "cat-remove");

        ResponseEntity<Void> response = restTemplate.exchange(
                "/bills/" + originalId, HttpMethod.DELETE, null, Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        Bill original = restTemplate
                .getForEntity("/bill/" + originalId, BillResponseModel.class).getBody().getBill();
        assertThat(original.getAmount()).isEqualTo(30.0);
    }

    @Test
    void removeBill_dropsItFromTheListAndFromTheMonthlySummary() {
        UUID originalId = createBill("30.00", "2026-12-05T10:00:00Z", "cat-removesummary");
        assertThat(categorySpend(2026, 12, "cat-removesummary")).isEqualTo(30.0);

        restTemplate.exchange("/bills/" + originalId, HttpMethod.DELETE, null, Void.class);

        assertThat(categorySpend(2026, 12, "cat-removesummary")).isEqualTo(0.0);

        BillListResponseModel list = restTemplate
                .getForEntity("/bills", BillListResponseModel.class).getBody();
        assertThat(list.getBills()).extracting(Bill::getId).doesNotContain(originalId);
    }

    @Test
    void removeBill_unknownId_returns404() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/bills/" + UUID.randomUUID(), HttpMethod.DELETE, null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // --- history ---

    @Test
    void getBillHistory_afterTwoCorrections_returnsBothPriorValuesNewestFirst() {
        UUID originalId = createBill("50.00", "2027-01-05T10:00:00Z", "cat-history");
        UUID firstCorrection = correct(originalId, 65.0, "2027-01-05T10:00:00Z", "cat-history");
        UUID secondCorrection = correct(firstCorrection, 80.0, "2027-01-05T10:00:00Z", "cat-history");

        BillHistoryResponse history = restTemplate
                .getForEntity("/bills/" + secondCorrection + "/history", BillHistoryResponse.class).getBody();

        assertThat(history.getHistory()).extracting(Bill::getId)
                .containsExactly(firstCorrection, originalId);
        assertThat(history.getHistory()).extracting(Bill::getAmount)
                .containsExactly(65.0, 50.0);
    }

    @Test
    void getBillHistory_forNeverCorrectedBill_isEmpty() {
        UUID originalId = createBill("50.00", "2027-02-05T10:00:00Z", "cat-nohistory");

        BillHistoryResponse history = restTemplate
                .getForEntity("/bills/" + originalId + "/history", BillHistoryResponse.class).getBody();

        assertThat(history.getHistory()).isEmpty();
    }

    // --- helpers ---

    private UUID createBill(String amount, String time, String categoryId) {
        Bill bill = new Bill()
                .amount(Double.valueOf(amount))
                .time(OffsetDateTime.parse(time))
                .categoryId(categoryId);
        return restTemplate.postForEntity("/createBill", bill, BillResponseModel.class)
                .getBody().getBill().getId();
    }

    private UUID correct(UUID id, Double amount, String time, String categoryId) {
        CorrectBillRequest request = new CorrectBillRequest(amount, OffsetDateTime.parse(time))
                .categoryId(categoryId);
        return restTemplate.exchange("/bills/" + id, HttpMethod.PUT,
                        new HttpEntity<>(request), BillResponseModel.class)
                .getBody().getBill().getId();
    }

    private UUID lastBillFor(String categoryId) {
        BillListResponseModel list = restTemplate
                .getForEntity("/bills", BillListResponseModel.class).getBody();
        return list.getBills().stream()
                .filter(b -> categoryId.equals(b.getCategoryId()))
                .reduce((a, b) -> b)
                .orElseThrow()
                .getId();
    }

    private Double categorySpend(int year, int month, String categoryId) {
        MonthlySummaryResponse summary = restTemplate.getForEntity(
                "/analysis/monthly?year=" + year + "&month=" + month,
                MonthlySummaryResponse.class).getBody();
        return summary.getSummary().getSpendingByCategory()
                .getOrDefault(categoryId, java.math.BigDecimal.ZERO).doubleValue();
    }
}
