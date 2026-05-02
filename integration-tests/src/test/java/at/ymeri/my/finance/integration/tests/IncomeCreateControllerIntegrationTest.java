package at.ymeri.my.finance.integration.tests;

import at.ymeri.my.finance.MyFinanceApplication;
import at.ymeri.my.finance.application.data.CreateIncomeRequest;
import at.ymeri.my.finance.application.data.IncomeResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = {MyFinanceApplication.class, TestConfig.class, TestDataSourceConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@EmbeddedKafka(
        partitions = 1,
        topics = {"booking.topic", "transaction.topic"}
)
public class IncomeCreateControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void createIncome_validRequest_returns201WithId() {
        CreateIncomeRequest request = new CreateIncomeRequest();
        request.setAmount(2500.0);
        request.setTime(OffsetDateTime.now());
        request.setDescription("Monthly salary");

        ResponseEntity<IncomeResponse> response = restTemplate
                .postForEntity("/api/v1/incomes", request, IncomeResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isNotNull();
        assertThat(response.getBody().getAmount()).isEqualTo(2500.0);
    }

    @Test
    void createIncome_missingAmount_returns400() {
        CreateIncomeRequest request = new CreateIncomeRequest();
        request.setTime(OffsetDateTime.now());
        request.setDescription("No amount");

        ResponseEntity<String> response = restTemplate
                .postForEntity("/api/v1/incomes", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createIncome_negativeAmount_returns400() {
        CreateIncomeRequest request = new CreateIncomeRequest();
        request.setAmount(-100.0);
        request.setTime(OffsetDateTime.now());

        ResponseEntity<String> response = restTemplate
                .postForEntity("/api/v1/incomes", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createIncome_missingTime_returns400() {
        CreateIncomeRequest request = new CreateIncomeRequest();
        request.setAmount(1000.0);

        ResponseEntity<String> response = restTemplate
                .postForEntity("/api/v1/incomes", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
