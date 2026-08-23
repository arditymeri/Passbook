package at.ymeri.my.finance.domain.service.income;

import at.ymeri.my.finance.domain.data.income.IncomeDto;
import at.ymeri.my.finance.domain.data.income.IncomeSource;
import at.ymeri.my.finance.domain.spi.income.AddIncomePersistencePort;
import at.ymeri.my.finance.domain.spi.income.GetIncomePersistencePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorrectIncomeServiceImplTest {

    private static final UUID ORIGINAL_ID = UUID.randomUUID();
    private static final OffsetDateTime ORIGINAL_TIME = OffsetDateTime.parse("2026-08-01T09:00:00Z");

    @Mock
    private AddIncomePersistencePort addIncomePersistencePort;

    @Mock
    private GetIncomePersistencePort getIncomePersistencePort;

    @InjectMocks
    private CorrectIncomeServiceImpl service;

    @Test
    void correctIncome_doesNotMutateTheOriginal() {
        IncomeDto original = original(new BigDecimal("2000.00"));
        when(getIncomePersistencePort.getIncomeById(ORIGINAL_ID)).thenReturn(Optional.of(original));
        when(addIncomePersistencePort.addIncome(any())).thenAnswer(inv -> inv.getArgument(0));

        service.correctIncome(ORIGINAL_ID, corrected(new BigDecimal("2500.00")));

        assertThat(original.getAmount()).isEqualByComparingTo("2000.00");
        assertThat(original.getCorrectsTransactionId()).isNull();
        assertThat(original.isReversal()).isFalse();
        assertThat(original.getTime()).isEqualTo(ORIGINAL_TIME);
    }

    @Test
    void correctIncome_writesReversalWithNegatedAmountAndSameSourceAccountAndTime() {
        IncomeDto original = original(new BigDecimal("2000.00"));
        when(getIncomePersistencePort.getIncomeById(ORIGINAL_ID)).thenReturn(Optional.of(original));
        when(addIncomePersistencePort.addIncome(any())).thenAnswer(inv -> inv.getArgument(0));

        service.correctIncome(ORIGINAL_ID, corrected(new BigDecimal("2500.00")));

        ArgumentCaptor<IncomeDto> captor = ArgumentCaptor.forClass(IncomeDto.class);
        verify(addIncomePersistencePort, times(2)).addIncome(captor.capture());
        IncomeDto reversal = captor.getAllValues().get(0);

        assertThat(reversal.getAmount()).isEqualByComparingTo("-2000.00");
        assertThat(reversal.isReversal()).isTrue();
        assertThat(reversal.getCorrectsTransactionId()).isEqualTo(ORIGINAL_ID.toString());
        assertThat(reversal.getSource()).isEqualTo(IncomeSource.SALARY);
        assertThat(reversal.getAccountId()).isEqualTo("acc-original");
        assertThat(reversal.getTime()).isEqualTo(ORIGINAL_TIME);
    }

    @Test
    void correctIncome_writesReplacementWithCorrectedValues() {
        IncomeDto original = original(new BigDecimal("2000.00"));
        when(getIncomePersistencePort.getIncomeById(ORIGINAL_ID)).thenReturn(Optional.of(original));
        when(addIncomePersistencePort.addIncome(any())).thenAnswer(inv -> inv.getArgument(0));

        IncomeDto correction = corrected(new BigDecimal("2500.00"));
        correction.setSource(IncomeSource.FREELANCE);
        service.correctIncome(ORIGINAL_ID, correction);

        ArgumentCaptor<IncomeDto> captor = ArgumentCaptor.forClass(IncomeDto.class);
        verify(addIncomePersistencePort, times(2)).addIncome(captor.capture());
        IncomeDto replacement = captor.getAllValues().get(1);

        assertThat(replacement.getAmount()).isEqualByComparingTo("2500.00");
        assertThat(replacement.isReversal()).isFalse();
        assertThat(replacement.getCorrectsTransactionId()).isEqualTo(ORIGINAL_ID.toString());
        assertThat(replacement.getSource()).isEqualTo(IncomeSource.FREELANCE);
    }

    @Test
    void correctIncome_onAlreadyCorrectedRow_reversesThatRowsAmount() {
        IncomeDto secondGeneration = original(new BigDecimal("2500.00"));
        secondGeneration.setCorrectsTransactionId(UUID.randomUUID().toString());
        when(getIncomePersistencePort.getIncomeById(ORIGINAL_ID)).thenReturn(Optional.of(secondGeneration));
        when(addIncomePersistencePort.addIncome(any())).thenAnswer(inv -> inv.getArgument(0));

        service.correctIncome(ORIGINAL_ID, corrected(new BigDecimal("3000.00")));

        ArgumentCaptor<IncomeDto> captor = ArgumentCaptor.forClass(IncomeDto.class);
        verify(addIncomePersistencePort, times(2)).addIncome(captor.capture());

        assertThat(captor.getAllValues().get(0).getAmount()).isEqualByComparingTo("-2500.00");
        assertThat(captor.getAllValues().get(1).getAmount()).isEqualByComparingTo("3000.00");
    }

    @Test
    void correctIncome_amountZeroOrLess_isRejected() {
        IncomeDto original = original(new BigDecimal("2000.00"));
        when(getIncomePersistencePort.getIncomeById(ORIGINAL_ID)).thenReturn(Optional.of(original));

        assertThatThrownBy(() -> service.correctIncome(ORIGINAL_ID, corrected(BigDecimal.ZERO)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void correctIncome_unknownId_throwsNoSuchElement() {
        when(getIncomePersistencePort.getIncomeById(ORIGINAL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.correctIncome(ORIGINAL_ID, corrected(new BigDecimal("2500.00"))))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void correctIncome_alreadySupersededRow_throwsIllegalState() {
        IncomeDto superseded = original(new BigDecimal("2000.00"));
        IncomeDto replacement = original(new BigDecimal("2500.00"));
        replacement.setId(UUID.randomUUID().toString());
        replacement.setCorrectsTransactionId(ORIGINAL_ID.toString());

        when(getIncomePersistencePort.getIncomeById(ORIGINAL_ID)).thenReturn(Optional.of(superseded));
        when(getIncomePersistencePort.getAll()).thenReturn(List.of(superseded, replacement));

        assertThatThrownBy(() -> service.correctIncome(ORIGINAL_ID, corrected(new BigDecimal("3000.00"))))
                .isInstanceOf(IllegalStateException.class);
    }

    private IncomeDto original(BigDecimal amount) {
        IncomeDto dto = new IncomeDto();
        dto.setId(ORIGINAL_ID.toString());
        dto.setAmount(amount);
        dto.setDescription("Salary");
        dto.setTime(ORIGINAL_TIME);
        dto.setSource(IncomeSource.SALARY);
        dto.setAccountId("acc-original");
        return dto;
    }

    private IncomeDto corrected(BigDecimal amount) {
        IncomeDto dto = new IncomeDto();
        dto.setAmount(amount);
        dto.setDescription("Salary");
        dto.setTime(ORIGINAL_TIME);
        dto.setSource(IncomeSource.SALARY);
        dto.setAccountId("acc-original");
        return dto;
    }
}
