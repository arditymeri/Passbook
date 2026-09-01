package at.ymeri.my.finance.application.mapper;

import at.ymeri.my.finance.application.data.ImportSummary;
import at.ymeri.my.finance.application.data.SyncAccount;
import at.ymeri.my.finance.application.data.SyncBill;
import at.ymeri.my.finance.application.data.SyncBudget;
import at.ymeri.my.finance.application.data.SyncCategory;
import at.ymeri.my.finance.application.data.SyncIncome;
import at.ymeri.my.finance.application.data.SyncRecurringSeries;
import at.ymeri.my.finance.application.data.SyncSavingsGoal;
import at.ymeri.my.finance.application.data.SyncSnapshot;
import at.ymeri.my.finance.domain.data.account.AccountDto;
import at.ymeri.my.finance.domain.data.account.AccountType;
import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.data.budget.BudgetDto;
import at.ymeri.my.finance.domain.data.category.CategoryDto;
import at.ymeri.my.finance.domain.data.category.CategoryType;
import at.ymeri.my.finance.domain.data.goal.SavingsGoalDto;
import at.ymeri.my.finance.domain.data.income.IncomeDto;
import at.ymeri.my.finance.domain.data.income.IncomeSource;
import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesDto;
import at.ymeri.my.finance.domain.data.sync.ImportSummaryDto;
import at.ymeri.my.finance.domain.data.sync.SyncSnapshotDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;

/**
 * Every amount field is a decimal string on the wire (Constitution Principle IV), matching how
 * {@code BillCorrectionController.toAmount} handles {@code correctBillRequest} — parsed via
 * {@code new BigDecimal(String)}, formatted via {@link BigDecimal#toPlainString()} so it never
 * renders in scientific notation. {@code type}/{@code source} fields are plain strings on the
 * generated sync models (not $ref'd enums, unlike the primary REST models), so they round-trip
 * through the domain enum's {@code name()}/{@code valueOf(String)} explicitly; the enum fields
 * that *are* declared inline with an {@code enum:} constraint in sync-model.yaml (recurring
 * series' {@code transactionType}/{@code frequency}/{@code status}, a bill's {@code necessityTag},
 * an income's {@code recurringFrequency}) generate their own nested Java enum with matching
 * constant names, which MapStruct converts automatically with no extra code.
 */
@Mapper
public interface SyncMapper {

    SyncMapper INSTANCE = Mappers.getMapper(SyncMapper.class);

    SyncSnapshot map(SyncSnapshotDto dto);

    SyncSnapshotDto map(SyncSnapshot api);

    ImportSummary map(ImportSummaryDto dto);

    @Mapping(target = "type", qualifiedByName = "domainAccountTypeToString")
    @Mapping(target = "balance", qualifiedByName = "domainAmountToString")
    SyncAccount map(AccountDto dto);

    @Mapping(target = "type", qualifiedByName = "stringToAccountType")
    @Mapping(target = "balance", qualifiedByName = "stringToAmount")
    AccountDto map(SyncAccount api);

    @Mapping(target = "type", qualifiedByName = "domainCategoryTypeToString")
    SyncCategory map(CategoryDto dto);

    @Mapping(target = "type", qualifiedByName = "stringToCategoryType")
    CategoryDto map(SyncCategory api);

    @Mapping(target = "limitAmount", qualifiedByName = "domainAmountToString")
    SyncBudget map(BudgetDto dto);

    @Mapping(target = "limitAmount", qualifiedByName = "stringToAmount")
    BudgetDto map(SyncBudget api);

    SyncRecurringSeries map(RecurringSeriesDto dto);

    RecurringSeriesDto map(SyncRecurringSeries api);

    @Mapping(target = "amount", qualifiedByName = "domainAmountToString")
    SyncBill map(BillDto dto);

    @Mapping(target = "amount", qualifiedByName = "stringToAmount")
    BillDto map(SyncBill api);

    @Mapping(target = "amount", qualifiedByName = "domainAmountToString")
    @Mapping(target = "source", qualifiedByName = "domainIncomeSourceToString")
    SyncIncome map(IncomeDto dto);

    @Mapping(target = "amount", qualifiedByName = "stringToAmount")
    @Mapping(target = "source", qualifiedByName = "stringToIncomeSource")
    IncomeDto map(SyncIncome api);

    @Mapping(target = "targetAmount", qualifiedByName = "domainAmountToString")
    SyncSavingsGoal map(SavingsGoalDto dto);

    @Mapping(target = "targetAmount", qualifiedByName = "stringToAmount")
    SavingsGoalDto map(SyncSavingsGoal api);

    @Named("domainAmountToString")
    default String domainAmountToString(BigDecimal amount) {
        return amount != null ? amount.toPlainString() : null;
    }

    @Named("stringToAmount")
    default BigDecimal stringToAmount(String amount) {
        return amount != null ? new BigDecimal(amount) : null;
    }

    @Named("domainAccountTypeToString")
    default String domainAccountTypeToString(AccountType type) {
        return type != null ? type.name() : null;
    }

    @Named("stringToAccountType")
    default AccountType stringToAccountType(String type) {
        return type != null ? AccountType.valueOf(type) : null;
    }

    @Named("domainCategoryTypeToString")
    default String domainCategoryTypeToString(CategoryType type) {
        return type != null ? type.name() : null;
    }

    @Named("stringToCategoryType")
    default CategoryType stringToCategoryType(String type) {
        return type != null ? CategoryType.valueOf(type) : null;
    }

    @Named("domainIncomeSourceToString")
    default String domainIncomeSourceToString(IncomeSource source) {
        return source != null ? source.name() : null;
    }

    @Named("stringToIncomeSource")
    default IncomeSource stringToIncomeSource(String source) {
        return source != null ? IncomeSource.valueOf(source) : null;
    }
}
