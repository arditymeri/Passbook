package at.ymeri.my.finance.seed;

import at.ymeri.my.finance.domain.api.AddAccountService;
import at.ymeri.my.finance.domain.api.AddBillService;
import at.ymeri.my.finance.domain.api.AddCategoryService;
import at.ymeri.my.finance.domain.api.AddIncomeService;
import at.ymeri.my.finance.domain.api.AddSavingsGoalService;
import at.ymeri.my.finance.domain.api.ConfirmRecurringSeriesService;
import at.ymeri.my.finance.domain.api.DetectRecurringSeriesService;
import at.ymeri.my.finance.domain.api.GetAccountService;
import at.ymeri.my.finance.domain.api.SetBudgetService;
import at.ymeri.my.finance.domain.data.account.AccountDto;
import at.ymeri.my.finance.domain.data.account.AccountType;
import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.data.budget.BudgetDto;
import at.ymeri.my.finance.domain.data.category.CategoryDto;
import at.ymeri.my.finance.domain.data.category.CategoryType;
import at.ymeri.my.finance.domain.data.income.IncomeDto;
import at.ymeri.my.finance.domain.data.income.IncomeSource;
import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesDto;
import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Populates a fresh (empty) database with realistic demo data — accounts, categories, six months
 * of bills/income (including a confirmed rent/subscription/salary recurring pattern), an envelope
 * budget for the current month, and a savings goal — so every feature has something to show right
 * after the app first starts. Runs once: guarded by {@link GetAccountService#getAll()} being
 * empty, so it never duplicates data on a later restart. Disable via
 * {@code app.demo-data.enabled=false}.
 */
@Component
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String CURRENCY = "EUR";

    // Groceries trends upward and Dining Out trends downward across the 6 seeded months, so
    // Spending Trends & Insights (016) has a real "biggest mover" to show between the last two.
    private static final double[] GROCERIES_TREND = {0.85, 0.90, 0.95, 1.00, 1.05, 1.15};
    private static final double[] DINING_TREND = {1.20, 1.10, 1.05, 1.00, 0.95, 0.85};

    private final GetAccountService getAccountService;
    private final AddAccountService addAccountService;
    private final AddCategoryService addCategoryService;
    private final AddBillService addBillService;
    private final AddIncomeService addIncomeService;
    private final SetBudgetService setBudgetService;
    private final AddSavingsGoalService addSavingsGoalService;
    private final DetectRecurringSeriesService detectRecurringSeriesService;
    private final ConfirmRecurringSeriesService confirmRecurringSeriesService;

    public DemoDataSeeder(GetAccountService getAccountService,
                           AddAccountService addAccountService,
                           AddCategoryService addCategoryService,
                           AddBillService addBillService,
                           AddIncomeService addIncomeService,
                           SetBudgetService setBudgetService,
                           AddSavingsGoalService addSavingsGoalService,
                           DetectRecurringSeriesService detectRecurringSeriesService,
                           ConfirmRecurringSeriesService confirmRecurringSeriesService) {
        this.getAccountService = getAccountService;
        this.addAccountService = addAccountService;
        this.addCategoryService = addCategoryService;
        this.addBillService = addBillService;
        this.addIncomeService = addIncomeService;
        this.setBudgetService = setBudgetService;
        this.addSavingsGoalService = addSavingsGoalService;
        this.detectRecurringSeriesService = detectRecurringSeriesService;
        this.confirmRecurringSeriesService = confirmRecurringSeriesService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!getAccountService.getAll().isEmpty()) {
            return;
        }
        log.info("No accounts found — seeding demo data");

        Accounts accounts = seedAccounts();
        Categories categories = seedCategories();
        OffsetDateTime now = OffsetDateTime.now();
        for (int monthsAgo = 5; monthsAgo >= 0; monthsAgo--) {
            seedMonth(monthsAgo, now, accounts, categories);
        }
        seedBudgets(now, categories);
        seedSavingsGoal(accounts);
        seedConfirmedRecurringSeries();

        log.info("Demo data seeded");
    }

    private Accounts seedAccounts() {
        String checkingId = addAccountService.addAccount(newAccount(
                "Checking", AccountType.CHECKING, "1500.00", "N26")).getId();
        String savingsId = addAccountService.addAccount(newAccount(
                "Savings", AccountType.SAVINGS, "5000.00", "N26")).getId();
        String creditCardId = addAccountService.addAccount(newAccount(
                "Credit Card", AccountType.CREDIT_CARD, "0.00", "Amex")).getId();
        return new Accounts(checkingId, savingsId, creditCardId);
    }

    private AccountDto newAccount(String name, AccountType type, String startingBalance, String institution) {
        AccountDto account = new AccountDto();
        account.setName(name);
        account.setType(type);
        account.setBalance(new BigDecimal(startingBalance));
        account.setCurrencies(List.of(CURRENCY));
        account.setDefaultCurrency(CURRENCY);
        account.setInstitution(institution);
        return account;
    }

    private Categories seedCategories() {
        return new Categories(
                addCategory("Rent"),
                addCategory("Groceries"),
                addCategory("Dining Out"),
                addCategory("Utilities"),
                addCategory("Transportation"),
                addCategory("Subscriptions"),
                addCategory("Entertainment"),
                addCategory("Health")
        );
    }

    private String addCategory(String name) {
        CategoryDto category = new CategoryDto();
        category.setName(name);
        category.setType(CategoryType.EXPENSE);
        return addCategoryService.addCategory(category).getId();
    }

    private void seedMonth(int monthsAgo, OffsetDateTime now, Accounts accounts, Categories categories) {
        addBill(categories.rent, accounts.checking, "Rent", "900.00", dateInMonth(now, monthsAgo, 1));
        addBill(categories.subscriptions, accounts.checking, "Netflix", "15.99", dateInMonth(now, monthsAgo, 5));
        addIncome(accounts.checking, "Salary", "3200.00", dateInMonth(now, monthsAgo, 25));

        addBill(categories.utilities, accounts.checking, "Electricity & Water",
                scale("120.00", 0.9 + 0.03 * monthsAgo), dateInMonth(now, monthsAgo, 10));
        addBill(categories.transportation, accounts.checking, "Monthly Transit Pass",
                scale("75.00", 0.9 + 0.02 * monthsAgo), dateInMonth(now, monthsAgo, 15));

        int trendIndex = 5 - monthsAgo;
        addBill(categories.groceries, accounts.checking, "Groceries",
                scale("42.50", GROCERIES_TREND[trendIndex]), dateInMonth(now, monthsAgo, 3));
        addBill(categories.groceries, accounts.checking, "Groceries",
                scale("58.20", GROCERIES_TREND[trendIndex]), dateInMonth(now, monthsAgo, 10));
        addBill(categories.groceries, accounts.checking, "Groceries",
                scale("33.10", GROCERIES_TREND[trendIndex]), dateInMonth(now, monthsAgo, 17));
        addBill(categories.groceries, accounts.checking, "Groceries",
                scale("71.40", GROCERIES_TREND[trendIndex]), dateInMonth(now, monthsAgo, 24));

        addBill(categories.diningOut, accounts.creditCard, "Restaurant",
                scale("34.00", DINING_TREND[trendIndex]), dateInMonth(now, monthsAgo, 6));
        addBill(categories.diningOut, accounts.creditCard, "Restaurant",
                scale("28.50", DINING_TREND[trendIndex]), dateInMonth(now, monthsAgo, 14));
        addBill(categories.diningOut, accounts.creditCard, "Restaurant",
                scale("46.90", DINING_TREND[trendIndex]), dateInMonth(now, monthsAgo, 22));

        addBill(categories.entertainment, accounts.checking, "Cinema", "24.00", dateInMonth(now, monthsAgo, 8));

        if (monthsAgo == 2) {
            addBill(categories.health, accounts.checking, "Doctor Visit", "180.00", dateInMonth(now, monthsAgo, 12));
        }
    }

    private void seedBudgets(OffsetDateTime now, Categories categories) {
        int year = now.getYear();
        int month = now.getMonthValue();
        setBudget(categories.groceries, year, month, "400.00");
        setBudget(categories.diningOut, year, month, "150.00");
        setBudget(categories.utilities, year, month, "150.00");
        setBudget(categories.transportation, year, month, "100.00");
        setBudget(categories.entertainment, year, month, "100.00");
        setBudget(categories.subscriptions, year, month, "30.00");
    }

    private void setBudget(String categoryId, int year, int month, String limitAmount) {
        BudgetDto budget = new BudgetDto();
        budget.setCategoryId(categoryId);
        budget.setYear(year);
        budget.setMonth(month);
        budget.setLimitAmount(new BigDecimal(limitAmount));
        setBudgetService.setBudget(budget);
    }

    private void seedSavingsGoal(Accounts accounts) {
        addSavingsGoalService.addGoal("Emergency Fund", new BigDecimal("10000.00"),
                OffsetDateTime.now().plusMonths(12), accounts.savings);
    }

    /**
     * Detects the rent/subscription/salary patterns just seeded and confirms each one, so
     * Upcoming Recurring (010) and the Cash Flow Forecast (015) already have confirmed series to
     * project from, rather than requiring the user to manually detect and confirm first.
     */
    private void seedConfirmedRecurringSeries() {
        List<RecurringSeriesDto> detected = detectRecurringSeriesService.detect();
        for (RecurringSeriesDto series : detected) {
            if (series.getStatus() != RecurringSeriesStatus.PROPOSED) {
                continue;
            }
            String description = series.getDescription();
            if ("rent".equals(description) || "netflix".equals(description) || "salary".equals(description)) {
                confirmRecurringSeriesService.confirm(series.getId());
            }
        }
    }

    private void addBill(String categoryId, String accountId, String description, String amount, OffsetDateTime time) {
        if (time == null) {
            return;
        }
        BillDto bill = new BillDto();
        bill.setDescription(description);
        bill.setAmount(new BigDecimal(amount));
        bill.setCurrency(CURRENCY);
        bill.setTime(time);
        bill.setCategoryId(categoryId);
        bill.setAccountId(accountId);
        addBillService.addBill(bill);
    }

    private void addIncome(String accountId, String description, String amount, OffsetDateTime time) {
        if (time == null) {
            return;
        }
        IncomeDto income = new IncomeDto();
        income.setDescription(description);
        income.setAmount(new BigDecimal(amount));
        income.setCurrency(CURRENCY);
        income.setTime(time);
        income.setSource(IncomeSource.SALARY);
        income.setAccountId(accountId);
        addIncomeService.addIncome(income);
    }

    /**
     * The given day-of-month, {@code monthsAgo} months before {@code now} — clamped to the
     * target month's actual length — or {@code null} when that date would fall in the future
     * (only relevant for the current month, {@code monthsAgo == 0}), so a demo run partway
     * through a month never seeds a transaction dated after "today".
     */
    private OffsetDateTime dateInMonth(OffsetDateTime now, int monthsAgo, int dayOfMonth) {
        OffsetDateTime monthAnchor = now.minusMonths(monthsAgo);
        int day = Math.min(dayOfMonth, monthAnchor.toLocalDate().lengthOfMonth());
        OffsetDateTime candidate = monthAnchor.withDayOfMonth(day)
                .withHour(10).withMinute(0).withSecond(0).withNano(0);
        return candidate.toLocalDate().isAfter(now.toLocalDate()) ? null : candidate;
    }

    private String scale(String baseAmount, double factor) {
        return BigDecimal.valueOf(Double.parseDouble(baseAmount) * factor)
                .setScale(2, RoundingMode.HALF_EVEN)
                .toString();
    }

    private record Accounts(String checking, String savings, String creditCard) {
    }

    private record Categories(String rent, String groceries, String diningOut, String utilities,
                               String transportation, String subscriptions, String entertainment, String health) {
    }
}
