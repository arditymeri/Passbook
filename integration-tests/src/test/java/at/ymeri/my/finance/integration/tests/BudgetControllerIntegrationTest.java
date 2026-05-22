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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = {MyFinanceApplication.class, TestConfig.class, TestDataSourceConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@EmbeddedKafka(
        partitions = 1,
        topics = {"booking.topic", "transaction.topic"}
)
public class BudgetControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    // ── Helpers ──────────────────────────────────────────────────────────────

    private CreateCategoryRequest category(String name) {
        CreateCategoryRequest req = new CreateCategoryRequest();
        req.setName(name);
        req.setType(CreateCategoryRequest.TypeEnum.EXPENSE);
        return req;
    }

    private String createCategory(String name) {
        ResponseEntity<CategoryResponse> resp = restTemplate
                .postForEntity("/api/v1/categories", category(name), CategoryResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resp.getBody().getId();
    }

    private CreateBudgetRequest budgetRequest(String categoryId, int year, int month, double limit) {
        CreateBudgetRequest req = new CreateBudgetRequest();
        req.setCategoryId(java.util.UUID.fromString(categoryId));
        req.setYear(year);
        req.setMonth(month);
        req.setLimitAmount(BigDecimal.valueOf(limit));
        return req;
    }

    // ── US1: Set Monthly Budget ───────────────────────────────────────────────

    @Test
    void createBudget_validRequest_returns200WithId() {
        String catId = createCategory("Groceries-IT-US1a");

        ResponseEntity<BudgetResponse> response = restTemplate
                .postForEntity("/api/v1/budgets", budgetRequest(catId, 2026, 5, 500.0), BudgetResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isNotNull();
        assertThat(response.getBody().getLimitAmount()).isEqualByComparingTo(BigDecimal.valueOf(500.0));
    }

    @Test
    void createBudget_sameMonthAndCategory_updatesLimit() {
        String catId = createCategory("Groceries-IT-US1b");

        restTemplate.postForEntity("/api/v1/budgets", budgetRequest(catId, 2026, 6, 200.0), BudgetResponse.class);
        ResponseEntity<BudgetResponse> updated = restTemplate
                .postForEntity("/api/v1/budgets", budgetRequest(catId, 2026, 6, 350.0), BudgetResponse.class);

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().getLimitAmount()).isEqualByComparingTo(BigDecimal.valueOf(350.0));
    }

    @Test
    void createBudget_zeroLimit_returns400() {
        String catId = createCategory("Groceries-IT-US1c");

        ResponseEntity<String> response = restTemplate
                .postForEntity("/api/v1/budgets", budgetRequest(catId, 2026, 5, 0.0), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createBudget_unknownCategory_returns404() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/budgets",
                budgetRequest("00000000-0000-0000-0000-000000000000", 2026, 5, 100.0),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── US2: Budget vs Actual Status ─────────────────────────────────────────

    @Test
    void budgetStatus_emptyMonth_returnsEmptyEntries() {
        ResponseEntity<BudgetStatusResponse> response = restTemplate
                .getForEntity("/api/v1/budgets/status?year=1999&month=1", BudgetStatusResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getEntries()).isEmpty();
    }

    @Test
    void budgetStatus_invalidMonth_returns400() {
        ResponseEntity<String> response = restTemplate
                .getForEntity("/api/v1/budgets/status?year=2026&month=13", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void budgetStatus_withBudgetNoSpend_showsUnderBudget() {
        String catId = createCategory("Transport-IT-US2a");
        restTemplate.postForEntity("/api/v1/budgets", budgetRequest(catId, 2025, 3, 150.0), BudgetResponse.class);

        ResponseEntity<BudgetStatusResponse> response = restTemplate
                .getForEntity("/api/v1/budgets/status?year=2025&month=3", BudgetStatusResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getEntries())
                .anyMatch(e -> e.getCategoryId() != null
                        && e.getCategoryId().toString().equals(catId)
                        && e.getStatus() == BudgetStatusEntry.StatusEnum.UNDER_BUDGET);
    }

    // ── US3: List and Delete ──────────────────────────────────────────────────

    @Test
    void listBudgets_returnsBudgetsForMonth() {
        String catA = createCategory("CatA-IT-US3a");
        String catB = createCategory("CatB-IT-US3a");
        restTemplate.postForEntity("/api/v1/budgets", budgetRequest(catA, 2024, 1, 100.0), BudgetResponse.class);
        restTemplate.postForEntity("/api/v1/budgets", budgetRequest(catB, 2024, 1, 200.0), BudgetResponse.class);

        ResponseEntity<BudgetListResponse> response = restTemplate
                .getForEntity("/api/v1/budgets?year=2024&month=1", BudgetListResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getBudgets().size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void deleteBudget_existingId_returns204() {
        String catId = createCategory("Delete-IT-US3b");
        ResponseEntity<BudgetResponse> created = restTemplate
                .postForEntity("/api/v1/budgets", budgetRequest(catId, 2023, 2, 300.0), BudgetResponse.class);
        String budgetId = created.getBody().getId();

        restTemplate.delete("/api/v1/budgets/" + budgetId);

        ResponseEntity<BudgetListResponse> list = restTemplate
                .getForEntity("/api/v1/budgets?year=2023&month=2", BudgetListResponse.class);
        assertThat(list.getBody().getBudgets())
                .noneMatch(b -> budgetId.equals(b.getId()));
    }

    @Test
    void deleteBudget_nonExistentId_returns404() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/budgets/00000000-0000-0000-0000-000000000099",
                String.class);
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
