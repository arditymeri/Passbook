package at.ymeri.my.finance.domain.service.budget;

import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.data.budget.AllocationTransferDto;
import at.ymeri.my.finance.domain.data.budget.BudgetDto;
import at.ymeri.my.finance.domain.data.budget.MoveAllocationResult;
import at.ymeri.my.finance.domain.data.category.CategoryDto;
import at.ymeri.my.finance.domain.spi.bill.GetBillPersistencePort;
import at.ymeri.my.finance.domain.spi.budget.AddAllocationTransferPersistencePort;
import at.ymeri.my.finance.domain.spi.budget.GetAllocationTransferPersistencePort;
import at.ymeri.my.finance.domain.spi.budget.GetBudgetPersistencePort;
import at.ymeri.my.finance.domain.spi.category.GetCategoryPersistencePort;
import at.ymeri.my.finance.domain.spi.income.GetIncomePersistencePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MoveAllocationServiceImplTest {

    @Mock
    private GetCategoryPersistencePort getCategoryPersistencePort;

    @Mock
    private AddAllocationTransferPersistencePort addAllocationTransferPersistencePort;

    @Mock
    private GetBudgetPersistencePort getBudgetPersistencePort;

    @Mock
    private GetIncomePersistencePort getIncomePersistencePort;

    @Mock
    private GetAllocationTransferPersistencePort getAllocationTransferPersistencePort;

    @Mock
    private GetBillPersistencePort getBillPersistencePort;

    private MoveAllocationServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(getCategoryPersistencePort.getCategoryById(any())).thenAnswer(inv -> {
            CategoryDto dto = new CategoryDto();
            dto.setId(inv.getArgument(0));
            return Optional.of(dto);
        });
        EnvelopeBalances envelopeBalances = new EnvelopeBalances(
                getBudgetPersistencePort, getIncomePersistencePort, getAllocationTransferPersistencePort, getBillPersistencePort);
        service = new MoveAllocationServiceImpl(getCategoryPersistencePort, envelopeBalances, addAllocationTransferPersistencePort);
    }

    @Test
    void moveAllocation_withinAvailableBalance_persistsTransfer() {
        when(getBudgetPersistencePort.getAll())
                .thenReturn(List.of(allocation("dining", 2026, 5, new BigDecimal("200.00"))));
        when(getBillPersistencePort.getAll()).thenReturn(List.of());
        // Simulate persistence: getAll() reflects whatever add() has stored so far, the same way
        // the real Postgres-backed ports would once the transfer is committed.
        List<AllocationTransferDto> transferStore = new ArrayList<>();
        when(getAllocationTransferPersistencePort.getAll()).thenAnswer(inv -> new ArrayList<>(transferStore));
        when(addAllocationTransferPersistencePort.add(any())).thenAnswer(inv -> {
            AllocationTransferDto t = inv.getArgument(0);
            transferStore.add(t);
            return t;
        });

        MoveAllocationResult result = service.moveAllocation("dining", "groceries", 2026, 5, new BigDecimal("50.00"));

        assertEquals("dining", result.getTransfer().getFromCategoryId());
        assertEquals("groceries", result.getTransfer().getToCategoryId());
        assertEquals(new BigDecimal("50.00"), result.getTransfer().getAmount());
        assertNotNull(result.getTransfer().getCreatedAt());
        assertEquals(new BigDecimal("150.00"), result.getFromEnvelopeBalance());
        assertEquals(new BigDecimal("50.00"), result.getToEnvelopeBalance());
        verify(addAllocationTransferPersistencePort).add(any());
    }

    @Test
    void moveAllocation_exceedsAvailableBalance_throwsAndDoesNotPersist() {
        when(getBudgetPersistencePort.getAll())
                .thenReturn(List.of(allocation("dining", 2026, 5, new BigDecimal("50.00"))));
        when(getAllocationTransferPersistencePort.getAll()).thenReturn(List.of());
        when(getBillPersistencePort.getAll()).thenReturn(List.of());

        assertThrows(IllegalArgumentException.class,
                () -> service.moveAllocation("dining", "groceries", 2026, 5, new BigDecimal("100.00")));
        verifyNoInteractions(addAllocationTransferPersistencePort);
    }

    @Test
    void moveAllocation_sameSourceAndDestination_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> service.moveAllocation("dining", "dining", 2026, 5, new BigDecimal("10.00")));
        verifyNoInteractions(addAllocationTransferPersistencePort);
    }

    @Test
    void moveAllocation_zeroAmount_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> service.moveAllocation("dining", "groceries", 2026, 5, BigDecimal.ZERO));
        verifyNoInteractions(addAllocationTransferPersistencePort);
    }

    @Test
    void moveAllocation_unknownSourceCategory_throwsNoSuchElement() {
        when(getCategoryPersistencePort.getCategoryById("unknown")).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class,
                () -> service.moveAllocation("unknown", "groceries", 2026, 5, new BigDecimal("10.00")));
        verifyNoInteractions(addAllocationTransferPersistencePort);
    }

    @Test
    void moveAllocation_unknownDestinationCategory_throwsNoSuchElement() {
        when(getCategoryPersistencePort.getCategoryById("unknown")).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class,
                () -> service.moveAllocation("dining", "unknown", 2026, 5, new BigDecimal("10.00")));
        verifyNoInteractions(addAllocationTransferPersistencePort);
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
