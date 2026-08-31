package at.ymeri.my.finance.domain.service.sync;

import at.ymeri.my.finance.domain.data.account.AccountDto;
import at.ymeri.my.finance.domain.data.budget.BudgetDto;
import at.ymeri.my.finance.domain.data.category.CategoryDto;
import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesDto;
import at.ymeri.my.finance.domain.service.recurring.RecurringMatching;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * "Is this incoming entity the same as one I already have" for the entity types that have a
 * natural key (research.md R2, data-model.md's matching table). Tried in order: same id, else
 * same natural key. Bills, incomes, and savings goals have no natural key and are matched by id
 * alone, directly in {@link ComputeMergePlanService} — no method here for them.
 */
@Component
public class SyncEntityMatching {

    public Optional<AccountDto> matchAccount(List<AccountDto> local, AccountDto incoming) {
        return byIdThenKey(local, incoming, AccountDto::getId, a -> a.getName());
    }

    public Optional<CategoryDto> matchCategory(List<CategoryDto> local, CategoryDto incoming) {
        return byIdThenKey(local, incoming, CategoryDto::getId, c -> c.getName());
    }

    public Optional<BudgetDto> matchBudget(List<BudgetDto> local, BudgetDto incoming) {
        return byIdThenKey(local, incoming, BudgetDto::getId,
                b -> b.getCategoryId() + "|" + b.getYear() + "|" + b.getMonth());
    }

    public Optional<RecurringSeriesDto> matchRecurringSeries(List<RecurringSeriesDto> local, RecurringSeriesDto incoming) {
        return byIdThenKey(local, incoming, RecurringSeriesDto::getId,
                s -> s.getTransactionType() + "|" + s.getGroupKey() + "|"
                        + RecurringMatching.normalizeDescription(s.getDescription()));
    }

    private <T> Optional<T> byIdThenKey(List<T> local, T incoming,
                                         java.util.function.Function<T, String> idOf,
                                         java.util.function.Function<T, String> naturalKeyOf) {
        String incomingId = idOf.apply(incoming);
        if (incomingId != null) {
            Optional<T> byId = local.stream().filter(l -> incomingId.equals(idOf.apply(l))).findFirst();
            if (byId.isPresent()) {
                return byId;
            }
        }
        String incomingKey = naturalKeyOf.apply(incoming);
        return local.stream()
                .filter(l -> Objects.equals(naturalKeyOf.apply(l), incomingKey))
                .findFirst();
    }
}
