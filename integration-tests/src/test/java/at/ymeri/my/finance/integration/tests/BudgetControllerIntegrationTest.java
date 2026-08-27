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
public class BudgetControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    // ── Helpers ──────────────────────────────────────────────────────────────

    private CreateCategoryRequest category(String name) {
        CreateCategoryRequest req = new CreateCategoryRequest();
        req.setName(name);
        req.setType(CategoryType.EXPENSE);
        return req;
    }

    private String createCategory(String name) {
        ResponseEntity<CategoryResponse> resp = restTemplate
                .postForEntity("/categories", category(name), CategoryResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resp.getBody().getId().toString();
    }

    private String createIncomeOnlyCategory(String name) {
        CreateCategoryRequest req = category(name);
        req.setType(CategoryType.INCOME);
        ResponseEntity<CategoryResponse> resp = restTemplate
                .postForEntity("/categories", req, CategoryResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resp.getBody().getId().toString();
    }

    private void createBill(String categoryId, int year, int month, double amount) {
        OffsetDateTime time = OffsetDateTime.of(year, month, 20, 0, 0, 0, 0, ZoneOffset.UTC);
        Bill bill = new Bill().amount(amount).time(time).categoryId(categoryId);
        restTemplate.postForEntity("/createBill", bill, BillResponseModel.class);
    }

    private CreateBudgetRequest budgetRequest(String categoryId, int year, int month, double limit) {
        CreateBudgetRequest req = new CreateBudgetRequest();
        req.setCategoryId(java.util.UUID.fromString(categoryId));
        req.setYear(year);
        req.setMonth(month);
        req.setLimitAmount(BigDecimal.valueOf(limit));
        return req;
    }

    private void createIncome(int year, int month, double amount) {
        OffsetDateTime time = OffsetDateTime.of(year, month, 15, 0, 0, 0, 0, ZoneOffset.UTC);
        CreateIncomeRequest income = new CreateIncomeRequest(amount, time);
        restTemplate.postForEntity("/incomes", income, IncomeResponse.class);
    }

    // ── US1: Set Monthly Budget ───────────────────────────────────────────────

    @Test
    void createBudget_validRequest_returns200WithId() {
        String catId = createCategory("Groceries-IT-US1a");

        ResponseEntity<BudgetResponse> response = restTemplate
                .postForEntity("/budgets", budgetRequest(catId, 2026, 5, 500.0), BudgetResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isNotNull();
        assertThat(response.getBody().getLimitAmount()).isEqualByComparingTo(BigDecimal.valueOf(500.0));
    }

    @Test
    void createBudget_sameMonthAndCategory_updatesLimit() {
        String catId = createCategory("Groceries-IT-US1b");

        restTemplate.postForEntity("/budgets", budgetRequest(catId, 2026, 6, 200.0), BudgetResponse.class);
        ResponseEntity<BudgetResponse> updated = restTemplate
                .postForEntity("/budgets", budgetRequest(catId, 2026, 6, 350.0), BudgetResponse.class);

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().getLimitAmount()).isEqualByComparingTo(BigDecimal.valueOf(350.0));
    }

    @Test
    void createBudget_zeroLimit_returns400() {
        String catId = createCategory("Groceries-IT-US1c");

        ResponseEntity<String> response = restTemplate
                .postForEntity("/budgets", budgetRequest(catId, 2026, 5, 0.0), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createBudget_unknownCategory_returns404() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/budgets",
                budgetRequest("00000000-0000-0000-0000-000000000000", 2026, 5, 100.0),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── US2: Budget vs Actual Status ─────────────────────────────────────────

    @Test
    void budgetStatus_emptyMonth_returnsEmptyEntries() {
        ResponseEntity<BudgetStatusResponse> response = restTemplate
                .getForEntity("/budgets/status?year=1999&month=1", BudgetStatusResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getEntries()).isEmpty();
    }

    @Test
    void budgetStatus_invalidMonth_returns400() {
        ResponseEntity<String> response = restTemplate
                .getForEntity("/budgets/status?year=2026&month=13", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void budgetStatus_withBudgetNoSpend_showsUnderBudget() {
        String catId = createCategory("Transport-IT-US2a");
        restTemplate.postForEntity("/budgets", budgetRequest(catId, 2025, 3, 150.0), BudgetResponse.class);

        ResponseEntity<BudgetStatusResponse> response = restTemplate
                .getForEntity("/budgets/status?year=2025&month=3", BudgetStatusResponse.class);

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
        restTemplate.postForEntity("/budgets", budgetRequest(catA, 2024, 1, 100.0), BudgetResponse.class);
        restTemplate.postForEntity("/budgets", budgetRequest(catB, 2024, 1, 200.0), BudgetResponse.class);

        ResponseEntity<BudgetListResponse> response = restTemplate
                .getForEntity("/budgets?year=2024&month=1", BudgetListResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getBudgets().size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void deleteBudget_existingId_returns204() {
        String catId = createCategory("Delete-IT-US3b");
        ResponseEntity<BudgetResponse> created = restTemplate
                .postForEntity("/budgets", budgetRequest(catId, 2023, 2, 300.0), BudgetResponse.class);
        String budgetId = created.getBody().getId().toString();

        restTemplate.delete("/budgets/" + budgetId);

        ResponseEntity<BudgetListResponse> list = restTemplate
                .getForEntity("/budgets?year=2023&month=2", BudgetListResponse.class);
        assertThat(list.getBody().getBudgets())
                .noneMatch(b -> budgetId.equals(b.getId()));
    }

    @Test
    void deleteBudget_nonExistentId_returns404() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/budgets/00000000-0000-0000-0000-000000000099",
                String.class);
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ── US1 (009): Unallocated Balance ────────────────────────────────────────

    @Test
    void budgetStatus_cumulativeAcrossMonths_unallocatedReflectsCarryover() {
        // `unallocated` is a global running total (all income vs. all allocations to date), so —
        // sharing a database with every other test in this suite — we assert the delta this test's
        // own mutations produce, not an absolute value.
        String catId = createCategory("Groceries-IT-009-US1");
        BigDecimal before = restTemplate
                .getForEntity("/budgets/status?year=2031&month=5", BudgetStatusResponse.class)
                .getBody().getUnallocated();

        createIncome(2031, 4, 3000.0);
        createIncome(2031, 5, 3000.0);
        restTemplate.postForEntity("/budgets", budgetRequest(catId, 2031, 4, 2800.0), BudgetResponse.class);

        ResponseEntity<BudgetStatusResponse> response = restTemplate
                .getForEntity("/budgets/status?year=2031&month=5", BudgetStatusResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // (3000 Apr income + 3000 May income) - 2800 Apr allocation = 3200, carried into May
        BigDecimal delta = response.getBody().getUnallocated().subtract(before);
        assertThat(delta).isEqualByComparingTo(BigDecimal.valueOf(3200.0));
    }

    // ── US2 (009): Assign Income to a Category ────────────────────────────────

    @Test
    void createBudget_thenSpend_envelopeBalanceReflectsAllocationMinusSpend() {
        String catId = createCategory("Groceries-IT-009-US2a");
        restTemplate.postForEntity("/budgets", budgetRequest(catId, 2032, 1, 400.0), BudgetResponse.class);
        createBill(catId, 2032, 1, 120.0);

        ResponseEntity<BudgetStatusResponse> response = restTemplate
                .getForEntity("/budgets/status?year=2032&month=1", BudgetStatusResponse.class);

        assertThat(response.getBody().getEntries())
                .filteredOn(e -> catId.equals(e.getCategoryId().toString()))
                .singleElement()
                .satisfies(e -> assertThat(e.getEnvelopeBalance()).isEqualByComparingTo(BigDecimal.valueOf(280.0)));
    }

    @Test
    void createBudget_reassignSameMonth_upsertsRatherThanDuplicating() {
        String catId = createCategory("Dining-IT-009-US2b");
        restTemplate.postForEntity("/budgets", budgetRequest(catId, 2032, 2, 200.0), BudgetResponse.class);
        restTemplate.postForEntity("/budgets", budgetRequest(catId, 2032, 2, 350.0), BudgetResponse.class);

        ResponseEntity<BudgetListResponse> list = restTemplate
                .getForEntity("/budgets?year=2032&month=2", BudgetListResponse.class);

        assertThat(list.getBody().getBudgets())
                .filteredOn(b -> catId.equals(b.getCategoryId().toString()))
                .singleElement()
                .satisfies(b -> assertThat(b.getLimitAmount()).isEqualByComparingTo(BigDecimal.valueOf(350.0)));
    }

    @Test
    void createBudget_incomeOnlyCategory_returns400() {
        String catId = createIncomeOnlyCategory("Salary-IT-009-US2c");

        ResponseEntity<String> response = restTemplate
                .postForEntity("/budgets", budgetRequest(catId, 2032, 3, 100.0), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── US3 (009): Move Money Between Categories ──────────────────────────────

    private TransferAllocationRequest transferRequest(String from, String to, int year, int month, double amount) {
        TransferAllocationRequest req = new TransferAllocationRequest();
        req.setFromCategoryId(java.util.UUID.fromString(from));
        req.setToCategoryId(java.util.UUID.fromString(to));
        req.setYear(year);
        req.setMonth(month);
        req.setAmount(BigDecimal.valueOf(amount));
        return req;
    }

    @Test
    void transferAllocation_withinAvailableBalance_updatesBothCategories() {
        String dining = createCategory("Dining-IT-009-US3a");
        String groceries = createCategory("Groceries-IT-009-US3a");
        restTemplate.postForEntity("/budgets", budgetRequest(dining, 2033, 1, 200.0), BudgetResponse.class);
        restTemplate.postForEntity("/budgets", budgetRequest(groceries, 2033, 1, 300.0), BudgetResponse.class);

        ResponseEntity<TransferAllocationResponse> response = restTemplate.postForEntity(
                "/budgets/transfer", transferRequest(dining, groceries, 2033, 1, 50.0), TransferAllocationResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getFromEnvelopeBalance()).isEqualByComparingTo(BigDecimal.valueOf(150.0));
        assertThat(response.getBody().getToEnvelopeBalance()).isEqualByComparingTo(BigDecimal.valueOf(350.0));
    }

    @Test
    void transferAllocation_exceedsAvailableBalance_returns400() {
        String dining = createCategory("Dining-IT-009-US3b");
        String groceries = createCategory("Groceries-IT-009-US3b");
        restTemplate.postForEntity("/budgets", budgetRequest(dining, 2033, 2, 50.0), BudgetResponse.class);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/budgets/transfer", transferRequest(dining, groceries, 2033, 2, 100.0), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── US4 (009): Repeat Last Month's Assignments ────────────────────────────

    private RepeatAllocationsRequest repeatRequest(int fromYear, int fromMonth, int toYear, int toMonth) {
        RepeatAllocationsRequest req = new RepeatAllocationsRequest();
        req.setFromYear(fromYear);
        req.setFromMonth(fromMonth);
        req.setToYear(toYear);
        req.setToMonth(toMonth);
        return req;
    }

    @Test
    void repeatAllocations_intoEmptyTargetMonth_createsMatchingAllocations() {
        String catId = createCategory("Groceries-IT-009-US4a");
        restTemplate.postForEntity("/budgets", budgetRequest(catId, 2034, 1, 400.0), BudgetResponse.class);

        ResponseEntity<RepeatAllocationsResponse> response = restTemplate.postForEntity(
                "/budgets/repeat", repeatRequest(2034, 1, 2034, 2), RepeatAllocationsResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getApplied())
                .filteredOn(a -> catId.equals(a.getCategoryId().toString()))
                .singleElement()
                .satisfies(a -> assertThat(a.getNewMonthlyAmount()).isEqualByComparingTo(BigDecimal.valueOf(400.0)));
    }

    @Test
    void repeatAllocations_intoMonthWithExistingAllocation_addsOnTop() {
        String catId = createCategory("Dining-IT-009-US4b");
        restTemplate.postForEntity("/budgets", budgetRequest(catId, 2034, 3, 150.0), BudgetResponse.class);
        restTemplate.postForEntity("/budgets", budgetRequest(catId, 2034, 4, 60.0), BudgetResponse.class);

        ResponseEntity<RepeatAllocationsResponse> response = restTemplate.postForEntity(
                "/budgets/repeat", repeatRequest(2034, 3, 2034, 4), RepeatAllocationsResponse.class);

        assertThat(response.getBody().getApplied())
                .filteredOn(a -> catId.equals(a.getCategoryId().toString()))
                .singleElement()
                .satisfies(a -> assertThat(a.getNewMonthlyAmount()).isEqualByComparingTo(BigDecimal.valueOf(210.0)));
    }

    @Test
    void repeatAllocations_emptySourceMonth_returns400() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/budgets/repeat", repeatRequest(1998, 1, 2034, 5), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
