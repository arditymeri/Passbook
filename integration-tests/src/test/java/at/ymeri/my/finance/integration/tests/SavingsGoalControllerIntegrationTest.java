package at.ymeri.my.finance.integration.tests;

import at.ymeri.my.finance.MyFinanceApplication;
import at.ymeri.my.finance.application.data.AccountResponse;
import at.ymeri.my.finance.application.data.AccountType;
import at.ymeri.my.finance.application.data.CreateAccountRequest;
import at.ymeri.my.finance.application.data.CreateSavingsGoalRequest;
import at.ymeri.my.finance.application.data.SavingsGoalResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

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
public class SavingsGoalControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    // ── Helpers ──────────────────────────────────────────────────────────────

    private UUID createAccount(String name) {
        CreateAccountRequest req = new CreateAccountRequest(name, AccountType.SAVINGS, List.of("EUR"), "EUR");
        ResponseEntity<AccountResponse> resp = restTemplate.postForEntity("/accounts", req, AccountResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resp.getBody().getId();
    }

    // ── US1 (011): Create a Savings Goal ────────────────────────────────────

    @Test
    void createSavingsGoal_validRequest_returns201WithCorrectFields() {
        UUID accountId = createAccount("Vacation-IT-011-US1a");
        CreateSavingsGoalRequest req = new CreateSavingsGoalRequest("Vacation Fund", 2000.0, accountId);

        ResponseEntity<SavingsGoalResponse> response = restTemplate
                .postForEntity("/savings-goals", req, SavingsGoalResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getId()).isNotNull();
        assertThat(response.getBody().getName()).isEqualTo("Vacation Fund");
        assertThat(response.getBody().getTargetAmount()).isEqualByComparingTo(BigDecimal.valueOf(2000.0));
        assertThat(response.getBody().getAccountId()).isEqualTo(accountId);
    }

    @Test
    void createSavingsGoal_secondGoalOnSameAccount_returns400() {
        UUID accountId = createAccount("House-IT-011-US1b");
        CreateSavingsGoalRequest first = new CreateSavingsGoalRequest("House Deposit", 10000.0, accountId);
        restTemplate.postForEntity("/savings-goals", first, SavingsGoalResponse.class);
        CreateSavingsGoalRequest second = new CreateSavingsGoalRequest("Second Goal", 500.0, accountId);

        ResponseEntity<String> response = restTemplate.postForEntity("/savings-goals", second, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createSavingsGoal_unknownAccount_returns404() {
        CreateSavingsGoalRequest req = new CreateSavingsGoalRequest("Ghost Goal", 500.0, UUID.randomUUID());

        ResponseEntity<String> response = restTemplate.postForEntity("/savings-goals", req, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createSavingsGoal_zeroTargetAmount_returns400() {
        UUID accountId = createAccount("Zero-IT-011-US1c");
        CreateSavingsGoalRequest req = new CreateSavingsGoalRequest("Zero Goal", 0.0, accountId);

        ResponseEntity<String> response = restTemplate.postForEntity("/savings-goals", req, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
