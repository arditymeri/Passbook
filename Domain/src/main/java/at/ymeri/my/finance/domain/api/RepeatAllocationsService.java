package at.ymeri.my.finance.domain.api;

import at.ymeri.my.finance.domain.data.budget.AllocationTopUp;

import java.util.List;

public interface RepeatAllocationsService {

    List<AllocationTopUp> repeatAllocations(int fromYear, int fromMonth, int toYear, int toMonth);
}
