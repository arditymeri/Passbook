package at.ymeri.my.finance.domain.api;

import at.ymeri.my.finance.domain.data.budget.MoveAllocationResult;

import java.math.BigDecimal;

public interface MoveAllocationService {

    MoveAllocationResult moveAllocation(String fromCategoryId, String toCategoryId, int year, int month, BigDecimal amount);
}
