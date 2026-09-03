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
import at.ymeri.my.finance.application.data.UpdateSavingsGoalRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = {MyFinanceApplication.class, TestConfig.class, TestDataSourceConfig.class, TestSecurityConfig.class},
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

    // ── US3 (011): Get Warned About Pace ────────────────────────────────────

    @Test
    void goalWithFutureTargetDate_progressAheadOfPace_isOnPace() {
        UUID accountId = createAccount("Laptop-IT-011-US3a");
        CreateSavingsGoalRequest req = new CreateSavingsGoalRequest("Laptop Fund", 1000.0, accountId)
                .targetDate(OffsetDateTime.now().plusDays(10));
        restTemplate.postForEntity("/savings-goals", req, SavingsGoalResponse.class);
        recordIncome(accountId, 900.0);

        SavingsGoalResponse goal = findGoal("Laptop Fund");

        assertThat(goal.getPaceStatus()).isEqualTo(at.ymeri.my.finance.application.data.PaceStatus.ON_PACE);
    }

    @Test
    void goalWithFutureTargetDate_progressBehindPace_isBehindPace() throws InterruptedException {
        // A short target window (3s) with a mid-window pause makes elapsed time a large, reliable
        // fraction of the total without depending on exact wall-clock timing (unlike a multi-day
        // window, where the test's own runtime would never be a meaningful fraction of the total).
        UUID accountId = createAccount("Piano-IT-011-US3b");
        CreateSavingsGoalRequest req = new CreateSavingsGoalRequest("Piano Fund", 1000.0, accountId)
                .targetDate(OffsetDateTime.now().plusSeconds(3));
        restTemplate.postForEntity("/savings-goals", req, SavingsGoalResponse.class);
        recordIncome(accountId, 1.0);
        Thread.sleep(1500);

        SavingsGoalResponse goal = findGoal("Piano Fund");

        assertThat(goal.getPaceStatus()).isEqualTo(at.ymeri.my.finance.application.data.PaceStatus.BEHIND_PACE);
    }

    @Test
    void goalWithPastTargetDate_unmet_isOverdue() {
        UUID accountId = createAccount("Sofa-IT-011-US3c");
        CreateSavingsGoalRequest req = new CreateSavingsGoalRequest("Sofa Fund", 1000.0, accountId)
                .targetDate(OffsetDateTime.now().minusDays(1));
        restTemplate.postForEntity("/savings-goals", req, SavingsGoalResponse.class);
        recordIncome(accountId, 50.0);

        SavingsGoalResponse goal = findGoal("Sofa Fund");

        assertThat(goal.getPaceStatus()).isEqualTo(at.ymeri.my.finance.application.data.PaceStatus.OVERDUE);
    }

    @Test
    void goalWithNoTargetDate_neverHasPaceStatus() {
        UUID accountId = createAccount("Rug-IT-011-US3d");
        restTemplate.postForEntity("/savings-goals",
                new CreateSavingsGoalRequest("Rug Fund", 500.0, accountId), SavingsGoalResponse.class);

        SavingsGoalResponse goal = findGoal("Rug Fund");

        assertThat(goal.getPaceStatus()).isNull();
    }

    // ── US4 (011): Manage a Goal ────────────────────────────────────────────

    @Test
    void updateSavingsGoal_validRequest_reflectedOnNextGet() {
        UUID accountId = createAccount("Wedding-IT-011-US4a");
        ResponseEntity<SavingsGoalResponse> created = restTemplate.postForEntity("/savings-goals",
                new CreateSavingsGoalRequest("Wedding Fund", 5000.0, accountId), SavingsGoalResponse.class);
        UUID goalId = created.getBody().getId();

        UpdateSavingsGoalRequest update = new UpdateSavingsGoalRequest("Dream Wedding Fund", 7500.0);
        ResponseEntity<SavingsGoalResponse> updateResponse = restTemplate.exchange(
                "/savings-goals/" + goalId, HttpMethod.PUT, new HttpEntity<>(update), SavingsGoalResponse.class);

        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody().getName()).isEqualTo("Dream Wedding Fund");
        assertThat(updateResponse.getBody().getTargetAmount()).isEqualByComparingTo(BigDecimal.valueOf(7500.0));

        SavingsGoalResponse fetched = findGoal("Dream Wedding Fund");
        assertThat(fetched.getTargetAmount()).isEqualByComparingTo(BigDecimal.valueOf(7500.0));
    }

    @Test
    void updateSavingsGoal_unknownId_returns404() {
        UpdateSavingsGoalRequest update = new UpdateSavingsGoalRequest("Name", 100.0);

        ResponseEntity<String> response = restTemplate.exchange(
                "/savings-goals/" + UUID.randomUUID(), HttpMethod.PUT, new HttpEntity<>(update), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteSavingsGoal_unknownId_returns404() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/savings-goals/" + UUID.randomUUID(), HttpMethod.DELETE, null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteSavingsGoal_existingGoal_removesItWithoutTouchingAccount() {
        UUID accountId = createAccount("Boat-IT-011-US4b");
        ResponseEntity<SavingsGoalResponse> created = restTemplate.postForEntity("/savings-goals",
                new CreateSavingsGoalRequest("Boat Fund", 20000.0, accountId), SavingsGoalResponse.class);
        UUID goalId = created.getBody().getId();
        recordIncome(accountId, 500.0);

        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                "/savings-goals/" + goalId, HttpMethod.DELETE, null, Void.class);

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        ResponseEntity<SavingsGoalListResponse> list = restTemplate
                .getForEntity("/savings-goals", SavingsGoalListResponse.class);
        assertThat(list.getBody().getGoals()).noneMatch(g -> "Boat Fund".equals(g.getName()));

        AccountResponse account = restTemplate
                .getForEntity("/accounts/" + accountId, AccountResponse.class).getBody();
        assertThat(account.getBalance()).isEqualTo(500.0);
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
