package at.ymeri.my.finance.domain.service.income;

import at.ymeri.my.finance.domain.data.income.IncomeDto;
import at.ymeri.my.finance.domain.data.income.IncomeSource;
import at.ymeri.my.finance.domain.spi.income.AddIncomePersistencePort;
import at.ymeri.my.finance.domain.spi.income.GetIncomePersistencePort;
import at.ymeri.my.finance.domain.spi.DirectUnitOfWork;
import at.ymeri.my.finance.domain.spi.UnitOfWork;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RemoveIncomeServiceImplTest {

    private static final UUID TARGET_ID = UUID.randomUUID();
    private static final OffsetDateTime TARGET_TIME = OffsetDateTime.parse("2026-08-01T09:00:00Z");

    @Mock
    private AddIncomePersistencePort addIncomePersistencePort;

    @Mock
    private GetIncomePersistencePort getIncomePersistencePort;

    @Spy
    private UnitOfWork unitOfWork = new DirectUnitOfWork();

    @InjectMocks
    private RemoveIncomeServiceImpl service;

    @Test
    void removeIncome_readsTheTargetUnderALockSoConcurrentRemovalsSerialize() {
        when(getIncomePersistencePort.lockIncomeById(TARGET_ID))
                .thenReturn(Optional.of(income("2000.00", null)));
        when(addIncomePersistencePort.addIncome(any())).thenAnswer(inv -> inv.getArgument(0));

        service.removeIncome(TARGET_ID);

        verify(getIncomePersistencePort).lockIncomeById(TARGET_ID);
        verify(getIncomePersistencePort, never()).getIncomeById(any());
    }

    @Test
    void removeIncome_writesExactlyOneReversalRowAndNoReplacement() {
        when(getIncomePersistencePort.lockIncomeById(TARGET_ID)).thenReturn(Optional.of(income("2000.00", null)));
        when(addIncomePersistencePort.addIncome(any())).thenAnswer(inv -> inv.getArgument(0));

        service.removeIncome(TARGET_ID);

        ArgumentCaptor<IncomeDto> captor = ArgumentCaptor.forClass(IncomeDto.class);
        verify(addIncomePersistencePort, times(1)).addIncome(captor.capture());

        IncomeDto reversal = captor.getValue();
        assertThat(reversal.getAmount()).isEqualByComparingTo("-2000.00");
        assertThat(reversal.isReversal()).isTrue();
        assertThat(reversal.getCorrectsTransactionId()).isEqualTo(TARGET_ID.toString());
        assertThat(reversal.getSource()).isEqualTo(IncomeSource.SALARY);
        assertThat(reversal.getAccountId()).isEqualTo("acc-1");
        assertThat(reversal.getTime()).isEqualTo(TARGET_TIME);
    }

    @Test
    void removeIncome_doesNotMutateTheOriginal() {
        IncomeDto original = income("2000.00", null);
        when(getIncomePersistencePort.lockIncomeById(TARGET_ID)).thenReturn(Optional.of(original));
        when(addIncomePersistencePort.addIncome(any())).thenAnswer(inv -> inv.getArgument(0));

        service.removeIncome(TARGET_ID);

        assertThat(original.getAmount()).isEqualByComparingTo("2000.00");
        assertThat(original.isReversal()).isFalse();
        assertThat(original.getCorrectsTransactionId()).isNull();
    }

    @Test
    void removeIncome_onAlreadyCorrectedRow_reversesItsCurrentValue() {
        IncomeDto secondGeneration = income("2500.00", UUID.randomUUID().toString());
        when(getIncomePersistencePort.lockIncomeById(TARGET_ID)).thenReturn(Optional.of(secondGeneration));
        when(addIncomePersistencePort.addIncome(any())).thenAnswer(inv -> inv.getArgument(0));

        service.removeIncome(TARGET_ID);

        ArgumentCaptor<IncomeDto> captor = ArgumentCaptor.forClass(IncomeDto.class);
        verify(addIncomePersistencePort).addIncome(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("-2500.00");
    }

    @Test
    void removeIncome_unknownId_throwsNoSuchElement() {
        when(getIncomePersistencePort.lockIncomeById(TARGET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeIncome(TARGET_ID))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void removeIncome_alreadyRemovedRow_throwsIllegalState() {
        IncomeDto target = income("2000.00", null);
        IncomeDto existingReversal = income("-2000.00", TARGET_ID.toString());
        existingReversal.setId(UUID.randomUUID().toString());

        when(getIncomePersistencePort.lockIncomeById(TARGET_ID)).thenReturn(Optional.of(target));
        when(getIncomePersistencePort.getAll()).thenReturn(List.of(target, existingReversal));

        assertThatThrownBy(() -> service.removeIncome(TARGET_ID))
                .isInstanceOf(IllegalStateException.class);
    }

    private IncomeDto income(String amount, String correctsTransactionId) {
        IncomeDto dto = new IncomeDto();
        dto.setId(TARGET_ID.toString());
        dto.setAmount(new BigDecimal(amount));
        dto.setDescription("Salary");
        dto.setTime(TARGET_TIME);
        dto.setSource(IncomeSource.SALARY);
        dto.setAccountId("acc-1");
        dto.setCorrectsTransactionId(correctsTransactionId);
        return dto;
    }
}
