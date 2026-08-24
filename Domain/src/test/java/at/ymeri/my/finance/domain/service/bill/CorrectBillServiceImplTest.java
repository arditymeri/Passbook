package at.ymeri.my.finance.domain.service.bill;

import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.spi.bill.AddBillPersistencePort;
import at.ymeri.my.finance.domain.spi.bill.GetBillPersistencePort;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorrectBillServiceImplTest {

    private static final UUID ORIGINAL_ID = UUID.randomUUID();
    private static final OffsetDateTime ORIGINAL_TIME = OffsetDateTime.parse("2026-08-01T10:00:00Z");

    @Mock
    private AddBillPersistencePort addBillPersistencePort;

    @Mock
    private GetBillPersistencePort getBillPersistencePort;

    @InjectMocks
    private CorrectBillServiceImpl service;

    @Test
    void correctBill_readsTheOriginalUnderALockSoConcurrentCorrectionsSerialize() {
        when(getBillPersistencePort.lockBillById(ORIGINAL_ID))
                .thenReturn(Optional.of(original(new BigDecimal("40.00"))));
        when(addBillPersistencePort.addBill(any())).thenAnswer(inv -> inv.getArgument(0));

        service.correctBill(ORIGINAL_ID, corrected(new BigDecimal("45.50")));

        verify(getBillPersistencePort).lockBillById(ORIGINAL_ID);
        verify(getBillPersistencePort, never()).getBillById(any());
    }

    @Test
    void correctBill_doesNotMutateTheOriginal() {
        BillDto original = original(new BigDecimal("40.00"));
        when(getBillPersistencePort.lockBillById(ORIGINAL_ID)).thenReturn(Optional.of(original));
        when(addBillPersistencePort.addBill(any())).thenAnswer(inv -> inv.getArgument(0));

        service.correctBill(ORIGINAL_ID, corrected(new BigDecimal("45.50")));

        assertThat(original.getAmount()).isEqualByComparingTo("40.00");
        assertThat(original.getCorrectsTransactionId()).isNull();
        assertThat(original.isReversal()).isFalse();
        assertThat(original.getTime()).isEqualTo(ORIGINAL_TIME);
    }

    @Test
    void correctBill_writesReversalWithNegatedAmountAndSameCategoryAccountAndTime() {
        BillDto original = original(new BigDecimal("40.00"));
        when(getBillPersistencePort.lockBillById(ORIGINAL_ID)).thenReturn(Optional.of(original));
        when(addBillPersistencePort.addBill(any())).thenAnswer(inv -> inv.getArgument(0));

        service.correctBill(ORIGINAL_ID, corrected(new BigDecimal("45.50")));

        ArgumentCaptor<BillDto> captor = ArgumentCaptor.forClass(BillDto.class);
        verify(addBillPersistencePort, times(2)).addBill(captor.capture());
        BillDto reversal = captor.getAllValues().get(0);

        assertThat(reversal.getAmount()).isEqualByComparingTo("-40.00");
        assertThat(reversal.isReversal()).isTrue();
        assertThat(reversal.getCorrectsTransactionId()).isEqualTo(ORIGINAL_ID.toString());
        assertThat(reversal.getCategoryId()).isEqualTo("cat-original");
        assertThat(reversal.getAccountId()).isEqualTo("acc-original");
        assertThat(reversal.getTime()).isEqualTo(ORIGINAL_TIME);
    }

    @Test
    void correctBill_writesReplacementWithCorrectedValues() {
        BillDto original = original(new BigDecimal("40.00"));
        when(getBillPersistencePort.lockBillById(ORIGINAL_ID)).thenReturn(Optional.of(original));
        when(addBillPersistencePort.addBill(any())).thenAnswer(inv -> inv.getArgument(0));

        BillDto correction = corrected(new BigDecimal("45.50"));
        correction.setCategoryId("cat-corrected");
        service.correctBill(ORIGINAL_ID, correction);

        ArgumentCaptor<BillDto> captor = ArgumentCaptor.forClass(BillDto.class);
        verify(addBillPersistencePort, times(2)).addBill(captor.capture());
        BillDto replacement = captor.getAllValues().get(1);

        assertThat(replacement.getAmount()).isEqualByComparingTo("45.50");
        assertThat(replacement.isReversal()).isFalse();
        assertThat(replacement.getCorrectsTransactionId()).isEqualTo(ORIGINAL_ID.toString());
        assertThat(replacement.getCategoryId()).isEqualTo("cat-corrected");
    }

    @Test
    void correctBill_onAlreadyCorrectedRow_reversesThatRowsAmount() {
        BillDto secondGeneration = original(new BigDecimal("45.50"));
        secondGeneration.setCorrectsTransactionId(UUID.randomUUID().toString());
        when(getBillPersistencePort.lockBillById(ORIGINAL_ID)).thenReturn(Optional.of(secondGeneration));
        when(addBillPersistencePort.addBill(any())).thenAnswer(inv -> inv.getArgument(0));

        service.correctBill(ORIGINAL_ID, corrected(new BigDecimal("50.00")));

        ArgumentCaptor<BillDto> captor = ArgumentCaptor.forClass(BillDto.class);
        verify(addBillPersistencePort, times(2)).addBill(captor.capture());

        assertThat(captor.getAllValues().get(0).getAmount()).isEqualByComparingTo("-45.50");
        assertThat(captor.getAllValues().get(1).getAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    void correctBill_amountZeroOrLess_isRejected() {
        BillDto original = original(new BigDecimal("40.00"));
        when(getBillPersistencePort.lockBillById(ORIGINAL_ID)).thenReturn(Optional.of(original));

        assertThatThrownBy(() -> service.correctBill(ORIGINAL_ID, corrected(BigDecimal.ZERO)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void correctBill_unknownId_throwsNoSuchElement() {
        when(getBillPersistencePort.lockBillById(ORIGINAL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.correctBill(ORIGINAL_ID, corrected(new BigDecimal("45.50"))))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void correctBill_alreadySupersededRow_throwsIllegalState() {
        BillDto superseded = original(new BigDecimal("40.00"));
        BillDto replacement = original(new BigDecimal("45.50"));
        replacement.setId(UUID.randomUUID().toString());
        replacement.setCorrectsTransactionId(ORIGINAL_ID.toString());

        when(getBillPersistencePort.lockBillById(ORIGINAL_ID)).thenReturn(Optional.of(superseded));
        when(getBillPersistencePort.getAll()).thenReturn(List.of(superseded, replacement));

        assertThatThrownBy(() -> service.correctBill(ORIGINAL_ID, corrected(new BigDecimal("50.00"))))
                .isInstanceOf(IllegalStateException.class);
    }

    private BillDto original(BigDecimal amount) {
        BillDto dto = new BillDto();
        dto.setId(ORIGINAL_ID.toString());
        dto.setAmount(amount);
        dto.setDescription("Groceries");
        dto.setTime(ORIGINAL_TIME);
        dto.setCategoryId("cat-original");
        dto.setAccountId("acc-original");
        return dto;
    }

    private BillDto corrected(BigDecimal amount) {
        BillDto dto = new BillDto();
        dto.setAmount(amount);
        dto.setDescription("Groceries");
        dto.setTime(ORIGINAL_TIME);
        dto.setCategoryId("cat-original");
        dto.setAccountId("acc-original");
        return dto;
    }
}
