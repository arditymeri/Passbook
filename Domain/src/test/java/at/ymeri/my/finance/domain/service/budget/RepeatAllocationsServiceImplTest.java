package at.ymeri.my.finance.domain.service.budget;

import at.ymeri.my.finance.domain.data.budget.AllocationTopUp;
import at.ymeri.my.finance.domain.data.budget.BudgetDto;
import at.ymeri.my.finance.domain.spi.budget.GetBudgetPersistencePort;
import at.ymeri.my.finance.domain.spi.budget.SetBudgetPersistencePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepeatAllocationsServiceImplTest {

    @Mock
    private GetBudgetPersistencePort getBudgetPersistencePort;

    @Mock
    private SetBudgetPersistencePort setBudgetPersistencePort;

    @InjectMocks
    private RepeatAllocationsServiceImpl service;

    @Test
    void repeatAllocations_targetMonthEmpty_topsUpFromZero() {
        when(getBudgetPersistencePort.findByYearAndMonth(2026, 5))
                .thenReturn(List.of(allocation("groceries", 2026, 5, new BigDecimal("400.00"))));
        when(getBudgetPersistencePort.findByCategoryIdAndYearAndMonth("groceries", 2026, 6))
                .thenReturn(Optional.empty());
        when(setBudgetPersistencePort.upsert(any())).thenAnswer(inv -> inv.getArgument(0));

        List<AllocationTopUp> applied = service.repeatAllocations(2026, 5, 2026, 6);

        assertEquals(1, applied.size());
        assertEquals("groceries", applied.get(0).getCategoryId());
        assertEquals(new BigDecimal("400.00"), applied.get(0).getAmountAdded());
        assertEquals(new BigDecimal("400.00"), applied.get(0).getNewMonthlyAmount());
    }

    @Test
    void repeatAllocations_targetMonthHasExistingAllocation_addsOnTop() {
        when(getBudgetPersistencePort.findByYearAndMonth(2026, 5))
                .thenReturn(List.of(allocation("groceries", 2026, 5, new BigDecimal("400.00"))));
        when(getBudgetPersistencePort.findByCategoryIdAndYearAndMonth("groceries", 2026, 6))
                .thenReturn(Optional.of(allocation("groceries", 2026, 6, new BigDecimal("60.00"))));
        when(setBudgetPersistencePort.upsert(any())).thenAnswer(inv -> inv.getArgument(0));

        List<AllocationTopUp> applied = service.repeatAllocations(2026, 5, 2026, 6);

        assertEquals(new BigDecimal("400.00"), applied.get(0).getAmountAdded());
        assertEquals(new BigDecimal("460.00"), applied.get(0).getNewMonthlyAmount());
    }

    @Test
    void repeatAllocations_emptySourceMonth_throws() {
        when(getBudgetPersistencePort.findByYearAndMonth(2026, 5)).thenReturn(List.of());

        assertThrows(IllegalArgumentException.class, () -> service.repeatAllocations(2026, 5, 2026, 6));
        verifyNoInteractions(setBudgetPersistencePort);
    }

    @Test
    void repeatAllocations_multipleCategories_upsertsEachIndependently() {
        when(getBudgetPersistencePort.findByYearAndMonth(2026, 5)).thenReturn(List.of(
                allocation("groceries", 2026, 5, new BigDecimal("400.00")),
                allocation("dining", 2026, 5, new BigDecimal("150.00"))));
        when(getBudgetPersistencePort.findByCategoryIdAndYearAndMonth("groceries", 2026, 6)).thenReturn(Optional.empty());
        when(getBudgetPersistencePort.findByCategoryIdAndYearAndMonth("dining", 2026, 6)).thenReturn(Optional.empty());
        when(setBudgetPersistencePort.upsert(any())).thenAnswer(inv -> inv.getArgument(0));

        List<AllocationTopUp> applied = service.repeatAllocations(2026, 5, 2026, 6);

        assertEquals(2, applied.size());
        verify(setBudgetPersistencePort, org.mockito.Mockito.times(2)).upsert(any());
    }

    private BudgetDto allocation(String categoryId, int year, int month, BigDecimal amount) {
        BudgetDto dto = new BudgetDto();
        dto.setCategoryId(categoryId);
        dto.setYear(year);
        dto.setMonth(month);
        dto.setLimitAmount(amount);
        return dto;
    }
}
