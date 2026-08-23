package at.ymeri.my.finance.integration.tests;

import at.ymeri.my.finance.MyFinanceApplication;
import at.ymeri.my.finance.application.data.CorrectIncomeRequest;
import at.ymeri.my.finance.application.data.CreateIncomeRequest;
import at.ymeri.my.finance.application.data.IncomeHistoryResponse;
import at.ymeri.my.finance.application.data.IncomeListResponse;
import at.ymeri.my.finance.application.data.IncomeResponse;
import at.ymeri.my.finance.application.data.IncomeSource;
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
public class IncomeCorrectionControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    // --- correction ---

    @Test
    void correctIncome_leavesTheOriginalRowUntouched() {
        UUID originalId = createIncome(2000.0, "2026-03-05T09:00:00Z");

        correct(originalId, 2500.0, "2026-03-05T09:00:00Z");

        IncomeResponse original = restTemplate
                .getForEntity("/incomes/" + originalId, IncomeResponse.class).getBody();
        assertThat(original.getAmount()).isEqualTo(2000.0);
        assertThat(original.getReversal()).isNotEqualTo(Boolean.TRUE);
        assertThat(original.getCorrectsTransactionId()).isNull();
    }

    @Test
    void correctIncome_listShowsOnlyTheCorrectedValue() {
        UUID originalId = createIncome(2100.0, "2026-04-05T09:00:00Z");

        UUID correctedId = correct(originalId, 2600.0, "2026-04-05T09:00:00Z");

        IncomeListResponse list = restTemplate
                .getForEntity("/incomes", IncomeListResponse.class).getBody();
        assertThat(list.getIncomes()).extracting(IncomeResponse::getId)
                .contains(correctedId)
                .doesNotContain(originalId);
    }

    @Test
    void correctIncome_monthlySummaryReflectsOnlyTheCorrectedAmount() {
        Double before = totalIncome(2026, 5);
        UUID originalId = createIncome(2200.0, "2026-05-05T09:00:00Z");

        correct(originalId, 2700.0, "2026-05-05T09:00:00Z");

        // 2200 (original) - 2200 (reversal) + 2700 (replacement) = +2700, not +4900
        assertThat(totalIncome(2026, 5)).isEqualTo(before + 2700.0);
    }

    @Test
    void correctIncome_twice_chainsOntoTheMostRecentValue() {
        Double before = totalIncome(2026, 6);
        UUID originalId = createIncome(2300.0, "2026-06-05T09:00:00Z");

        UUID firstCorrection = correct(originalId, 2800.0, "2026-06-05T09:00:00Z");
        UUID secondCorrection = correct(firstCorrection, 3300.0, "2026-06-05T09:00:00Z");

        assertThat(totalIncome(2026, 6)).isEqualTo(before + 3300.0);

        IncomeListResponse list = restTemplate
                .getForEntity("/incomes", IncomeListResponse.class).getBody();
        assertThat(list.getIncomes()).extracting(IncomeResponse::getId)
                .contains(secondCorrection)
                .doesNotContain(originalId, firstCorrection);
    }

    @Test
    void correctIncome_movingTheDateIntoAnotherMonth_movesTheAmountBetweenMonths() {
        Double julyBefore = totalIncome(2026, 7);
        Double augustBefore = totalIncome(2026, 8);

        UUID originalId = createIncome(1500.0, "2026-07-05T09:00:00Z");
        assertThat(totalIncome(2026, 7)).isEqualTo(julyBefore + 1500.0);

        correct(originalId, 1500.0, "2026-08-05T09:00:00Z");

        assertThat(totalIncome(2026, 7)).isEqualTo(julyBefore);
        assertThat(totalIncome(2026, 8)).isEqualTo(augustBefore + 1500.0);
    }

    @Test
    void correctIncome_unknownId_returns404() {
        CorrectIncomeRequest request = new CorrectIncomeRequest(2500.0, OffsetDateTime.parse("2026-03-05T09:00:00Z"));
        ResponseEntity<String> response = restTemplate.exchange(
                "/incomes/" + UUID.randomUUID(), HttpMethod.PUT, new HttpEntity<>(request), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void correctIncome_alreadyCorrectedRow_returns409() {
        UUID originalId = createIncome(2400.0, "2026-09-05T09:00:00Z");
        correct(originalId, 2900.0, "2026-09-05T09:00:00Z");

        CorrectIncomeRequest request = new CorrectIncomeRequest(3400.0, OffsetDateTime.parse("2026-09-05T09:00:00Z"));
        ResponseEntity<String> response = restTemplate.exchange(
                "/incomes/" + originalId, HttpMethod.PUT, new HttpEntity<>(request), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void correctIncome_amountZero_returns400() {
        UUID originalId = createIncome(2500.0, "2026-10-05T09:00:00Z");

        CorrectIncomeRequest request = new CorrectIncomeRequest(0.0, OffsetDateTime.parse("2026-10-05T09:00:00Z"));
        ResponseEntity<String> response = restTemplate.exchange(
                "/incomes/" + originalId, HttpMethod.PUT, new HttpEntity<>(request), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // --- removal ---

    @Test
    void removeIncome_returns204AndLeavesTheOriginalRowUntouched() {
        UUID originalId = createIncome(1200.0, "2026-11-05T09:00:00Z");

        ResponseEntity<Void> response = restTemplate.exchange(
                "/incomes/" + originalId, HttpMethod.DELETE, null, Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        IncomeResponse original = restTemplate
                .getForEntity("/incomes/" + originalId, IncomeResponse.class).getBody();
        assertThat(original.getAmount()).isEqualTo(1200.0);
    }

    @Test
    void removeIncome_dropsItFromTheListAndFromTheMonthlySummary() {
        Double before = totalIncome(2026, 12);
        UUID originalId = createIncome(1300.0, "2026-12-05T09:00:00Z");
        assertThat(totalIncome(2026, 12)).isEqualTo(before + 1300.0);

        restTemplate.exchange("/incomes/" + originalId, HttpMethod.DELETE, null, Void.class);

        assertThat(totalIncome(2026, 12)).isEqualTo(before);

        IncomeListResponse list = restTemplate
                .getForEntity("/incomes", IncomeListResponse.class).getBody();
        assertThat(list.getIncomes()).extracting(IncomeResponse::getId).doesNotContain(originalId);
    }

    @Test
    void removeIncome_unknownId_returns404() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/incomes/" + UUID.randomUUID(), HttpMethod.DELETE, null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // --- history ---

    @Test
    void getIncomeHistory_afterTwoCorrections_returnsBothPriorValuesNewestFirst() {
        UUID originalId = createIncome(2000.0, "2027-01-05T09:00:00Z");
        UUID firstCorrection = correct(originalId, 2500.0, "2027-01-05T09:00:00Z");
        UUID secondCorrection = correct(firstCorrection, 3000.0, "2027-01-05T09:00:00Z");

        IncomeHistoryResponse history = restTemplate
                .getForEntity("/incomes/" + secondCorrection + "/history", IncomeHistoryResponse.class).getBody();

        assertThat(history.getHistory()).extracting(IncomeResponse::getId)
                .containsExactly(firstCorrection, originalId);
        assertThat(history.getHistory()).extracting(IncomeResponse::getAmount)
                .containsExactly(2500.0, 2000.0);
    }

    @Test
    void getIncomeHistory_forNeverCorrectedIncome_isEmpty() {
        UUID originalId = createIncome(2000.0, "2027-02-05T09:00:00Z");

        IncomeHistoryResponse history = restTemplate
                .getForEntity("/incomes/" + originalId + "/history", IncomeHistoryResponse.class).getBody();

        assertThat(history.getHistory()).isEmpty();
    }

    // --- helpers ---

    private UUID createIncome(Double amount, String time) {
        CreateIncomeRequest request = new CreateIncomeRequest(amount, OffsetDateTime.parse(time))
                .source(IncomeSource.SALARY);
        return restTemplate.postForEntity("/incomes", request, IncomeResponse.class).getBody().getId();
    }

    private UUID correct(UUID id, Double amount, String time) {
        CorrectIncomeRequest request = new CorrectIncomeRequest(amount, OffsetDateTime.parse(time))
                .source(IncomeSource.SALARY);
        return restTemplate.exchange("/incomes/" + id, HttpMethod.PUT,
                        new HttpEntity<>(request), IncomeResponse.class)
                .getBody().getId();
    }

    private Double totalIncome(int year, int month) {
        MonthlySummaryResponse summary = restTemplate.getForEntity(
                "/analysis/monthly?year=" + year + "&month=" + month,
                MonthlySummaryResponse.class).getBody();
        return summary.getSummary().getTotalIncome().doubleValue();
    }
}
