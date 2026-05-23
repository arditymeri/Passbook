package at.ymeri.my.finance.domain.service.budget;

import at.ymeri.my.finance.domain.data.budget.BudgetDto;
import at.ymeri.my.finance.domain.data.category.CategoryDto;
import at.ymeri.my.finance.domain.spi.budget.GetBudgetPersistencePort;
import at.ymeri.my.finance.domain.spi.budget.SetBudgetPersistencePort;
import at.ymeri.my.finance.domain.spi.category.GetCategoryPersistencePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SetBudgetServiceImplTest {

    @Mock
    private SetBudgetPersistencePort setBudgetPersistencePort;

    @Mock
    private GetBudgetPersistencePort getBudgetPersistencePort;

    @Mock
    private GetCategoryPersistencePort getCategoryPersistencePort;

    @InjectMocks
    private SetBudgetServiceImpl setBudgetService;

    @Test
    void setBudget_validRequest_persistsAndReturns() {
        BudgetDto dto = budget("cat-1", 2026, 5, new BigDecimal("500.00"));
        CategoryDto category = new CategoryDto();
        category.setId("cat-1");

        when(getCategoryPersistencePort.getCategoryById("cat-1")).thenReturn(Optional.of(category));
        when(getBudgetPersistencePort.findByCategoryIdAndYearAndMonth("cat-1", 2026, 5)).thenReturn(Optional.empty());
        when(setBudgetPersistencePort.upsert(any())).thenReturn(dto);

        BudgetDto result = setBudgetService.setBudget(dto);

        assertNotNull(result);
        assertEquals(new BigDecimal("500.00"), result.getLimitAmount());
        verify(setBudgetPersistencePort).upsert(any());
    }

    @Test
    void setBudget_zeroLimit_throwsIllegalArgumentException() {
        BudgetDto dto = budget("cat-1", 2026, 5, BigDecimal.ZERO);
        assertThrows(IllegalArgumentException.class, () -> setBudgetService.setBudget(dto));
        verifyNoInteractions(setBudgetPersistencePort);
    }

    @Test
    void setBudget_negativeLimit_throwsIllegalArgumentException() {
        BudgetDto dto = budget("cat-1", 2026, 5, new BigDecimal("-10.00"));
        assertThrows(IllegalArgumentException.class, () -> setBudgetService.setBudget(dto));
        verifyNoInteractions(setBudgetPersistencePort);
    }

    @Test
    void setBudget_unknownCategory_throwsNoSuchElementException() {
        BudgetDto dto = budget("unknown-cat", 2026, 5, new BigDecimal("100.00"));
        when(getCategoryPersistencePort.getCategoryById("unknown-cat")).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> setBudgetService.setBudget(dto));
        verifyNoInteractions(setBudgetPersistencePort);
    }

    @Test
    void setBudget_existingCategoryAndMonth_updatesLimitAmountAndUpserts() {
        BudgetDto existing = budget("cat-1", 2026, 5, new BigDecimal("200.00"));
        existing.setId("existing-id");
        BudgetDto request = budget("cat-1", 2026, 5, new BigDecimal("350.00"));
        CategoryDto category = new CategoryDto();
        category.setId("cat-1");

        when(getCategoryPersistencePort.getCategoryById("cat-1")).thenReturn(Optional.of(category));
        when(getBudgetPersistencePort.findByCategoryIdAndYearAndMonth("cat-1", 2026, 5)).thenReturn(Optional.of(existing));
        when(setBudgetPersistencePort.upsert(any())).thenAnswer(inv -> inv.getArgument(0));

        BudgetDto result = setBudgetService.setBudget(request);

        assertEquals("existing-id", result.getId());
        assertEquals(new BigDecimal("350.00"), result.getLimitAmount());
    }

    private BudgetDto budget(String categoryId, int year, int month, BigDecimal limit) {
        BudgetDto dto = new BudgetDto();
        dto.setCategoryId(categoryId);
        dto.setYear(year);
        dto.setMonth(month);
        dto.setLimitAmount(limit);
        return dto;
    }
}
