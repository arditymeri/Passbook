package at.ymeri.my.finance.integration.tests;

import at.ymeri.my.finance.MyFinanceApplication;
import at.ymeri.my.finance.application.data.Bill;
import at.ymeri.my.finance.application.data.CreateIncomeRequest;
import at.ymeri.my.finance.application.data.MonthlySummaryListResponse;
import at.ymeri.my.finance.application.data.MonthlySummaryResponse;
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
public class AnalysisGetControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private static final OffsetDateTime MAY_2026 = OffsetDateTime.of(2026, 5, 15, 12, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void monthlySummary_withSeedData_returnsCorrectTotals() {
        Bill bill = new Bill();
        bill.setAmount(200.0);
        bill.setTime(MAY_2026);
        restTemplate.postForEntity("/bills", bill, Object.class);

        CreateIncomeRequest income = new CreateIncomeRequest();
        income.setAmount(1000.0);
        income.setTime(MAY_2026);
        restTemplate.postForEntity("/incomes", income, Object.class);

        ResponseEntity<MonthlySummaryResponse> response = restTemplate
                .getForEntity("/analysis/monthly?year=2026&month=5", MonthlySummaryResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getSummary()).isNotNull();
        assertThat(response.getBody().getSummary().getYear()).isEqualTo(2026);
        assertThat(response.getBody().getSummary().getMonth()).isEqualTo(5);
        assertThat(response.getBody().getSummary().getTotalExpenses()).isNotNull();
        assertThat(response.getBody().getSummary().getTotalIncome()).isNotNull();
    }

    @Test
    void monthlySummary_emptyMonth_returnsZeroTotals() {
        ResponseEntity<MonthlySummaryResponse> response = restTemplate
                .getForEntity("/analysis/monthly?year=2000&month=1", MonthlySummaryResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getSummary().getTotalExpenses().doubleValue()).isEqualTo(0.0);
        assertThat(response.getBody().getSummary().getTotalIncome().doubleValue()).isEqualTo(0.0);
        assertThat(response.getBody().getSummary().getNetBalance().doubleValue()).isEqualTo(0.0);
    }

    @Test
    void monthlySummary_invalidMonth_returns400() {
        ResponseEntity<String> response = restTemplate
                .getForEntity("/analysis/monthly?year=2026&month=13", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void periodSummary_multiMonthRange_returnsOneEntryPerMonth() {
        ResponseEntity<MonthlySummaryListResponse> response = restTemplate
                .getForEntity("/analysis/period?from=2000-01-01&to=2000-03-31", MonthlySummaryListResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getSummaries()).hasSize(3);
        assertThat(response.getBody().getSummaries().get(0).getMonth()).isEqualTo(1);
        assertThat(response.getBody().getSummaries().get(1).getMonth()).isEqualTo(2);
        assertThat(response.getBody().getSummaries().get(2).getMonth()).isEqualTo(3);
    }

    @Test
    void periodSummary_fromAfterTo_returns400() {
        ResponseEntity<String> response = restTemplate
                .getForEntity("/analysis/period?from=2026-06-01&to=2026-01-01", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
