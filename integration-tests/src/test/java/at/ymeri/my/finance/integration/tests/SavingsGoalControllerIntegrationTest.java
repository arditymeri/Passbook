package at.ymeri.my.finance.integration.tests;

import at.ymeri.my.finance.MyFinanceApplication;
import at.ymeri.my.finance.application.data.AccountResponse;
import at.ymeri.my.finance.application.data.AccountType;
import at.ymeri.my.finance.application.data.CreateAccountRequest;
import at.ymeri.my.finance.application.data.CreateIncomeRequest;
import at.ymeri.my.finance.application.data.CreateSavingsGoalRequest;
import at.ymeri.my.finance.application.data.IncomeResponse;
import at.ymeri.my.finance.application.data.SavingsGoalListResponse;
import at.ymeri.my.finance.application.data.SavingsGoalResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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

    // ── US2 (011): See Goal Progress at a Glance ────────────────────────────

    @Test
    void listSavingsGoals_reflectsLinkedAccountBalance() {
        UUID accountId = createAccount("Emergency-IT-011-US2a");
        restTemplate.postForEntity("/savings-goals",
                new CreateSavingsGoalRequest("Emergency Fund", 1000.0, accountId), SavingsGoalResponse.class);
        recordIncome(accountId, 300.0);

        ResponseEntity<SavingsGoalListResponse> response = restTemplate
                .getForEntity("/savings-goals", SavingsGoalListResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getGoals())
                .anyMatch(g -> "Emergency Fund".equals(g.getName())
                        && g.getSavedAmount().compareTo(BigDecimal.valueOf(300.0)) == 0
                        && g.getPercentComplete().compareTo(BigDecimal.valueOf(30.00)) == 0
                        && g.getRemainingAmount().compareTo(BigDecimal.valueOf(700.0)) == 0
                        && !g.getAchieved());
    }

    @Test
    void listSavingsGoals_newTransactionOnLinkedAccount_changesValuesOnNextCall() {
        UUID accountId = createAccount("Bike-IT-011-US2b");
        restTemplate.postForEntity("/savings-goals",
                new CreateSavingsGoalRequest("New Bike", 500.0, accountId), SavingsGoalResponse.class);
        recordIncome(accountId, 100.0);

        SavingsGoalResponse before = findGoal("New Bike");
        recordIncome(accountId, 150.0);
        SavingsGoalResponse after = findGoal("New Bike");

        assertThat(before.getSavedAmount()).isEqualByComparingTo(BigDecimal.valueOf(100.0));
        assertThat(after.getSavedAmount()).isEqualByComparingTo(BigDecimal.valueOf(250.0));
    }

    @Test
    void listSavingsGoals_balanceAtOrAboveTarget_marksAchieved() {
        UUID accountId = createAccount("Camera-IT-011-US2c");
        restTemplate.postForEntity("/savings-goals",
                new CreateSavingsGoalRequest("Camera Fund", 200.0, accountId), SavingsGoalResponse.class);
        recordIncome(accountId, 250.0);

        SavingsGoalResponse goal = findGoal("Camera Fund");

        assertThat(goal.getAchieved()).isTrue();
    }

    @Test
    void getSavingsGoal_unknownId_returns404() {
        ResponseEntity<String> response = restTemplate
                .getForEntity("/savings-goals/" + UUID.randomUUID(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private void recordIncome(UUID accountId, double amount) {
        CreateIncomeRequest income = new CreateIncomeRequest(amount, OffsetDateTime.now())
                .accountId(accountId.toString());
        restTemplate.postForEntity("/incomes", income, IncomeResponse.class);
    }

    private SavingsGoalResponse findGoal(String name) {
        ResponseEntity<SavingsGoalListResponse> list = restTemplate
                .getForEntity("/savings-goals", SavingsGoalListResponse.class);
        return list.getBody().getGoals().stream()
                .filter(g -> name.equals(g.getName()))
                .findFirst()
                .orElseThrow();
    }
}
