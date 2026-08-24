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
class RemoveBillServiceImplTest {

    private static final UUID TARGET_ID = UUID.randomUUID();
    private static final OffsetDateTime TARGET_TIME = OffsetDateTime.parse("2026-08-01T10:00:00Z");

    @Mock
    private AddBillPersistencePort addBillPersistencePort;

    @Mock
    private GetBillPersistencePort getBillPersistencePort;

    @InjectMocks
    private RemoveBillServiceImpl service;

    @Test
    void removeBill_readsTheTargetUnderALockSoConcurrentRemovalsSerialize() {
        when(getBillPersistencePort.lockBillById(TARGET_ID)).thenReturn(Optional.of(bill("40.00", null)));
        when(addBillPersistencePort.addBill(any())).thenAnswer(inv -> inv.getArgument(0));

        service.removeBill(TARGET_ID);

        verify(getBillPersistencePort).lockBillById(TARGET_ID);
        verify(getBillPersistencePort, never()).getBillById(any());
    }

    @Test
    void removeBill_writesExactlyOneReversalRowAndNoReplacement() {
        when(getBillPersistencePort.lockBillById(TARGET_ID)).thenReturn(Optional.of(bill("40.00", null)));
        when(addBillPersistencePort.addBill(any())).thenAnswer(inv -> inv.getArgument(0));

        service.removeBill(TARGET_ID);

        ArgumentCaptor<BillDto> captor = ArgumentCaptor.forClass(BillDto.class);
        verify(addBillPersistencePort, times(1)).addBill(captor.capture());

        BillDto reversal = captor.getValue();
        assertThat(reversal.getAmount()).isEqualByComparingTo("-40.00");
        assertThat(reversal.isReversal()).isTrue();
        assertThat(reversal.getCorrectsTransactionId()).isEqualTo(TARGET_ID.toString());
        assertThat(reversal.getCategoryId()).isEqualTo("cat-1");
        assertThat(reversal.getAccountId()).isEqualTo("acc-1");
        assertThat(reversal.getTime()).isEqualTo(TARGET_TIME);
    }

    @Test
    void removeBill_doesNotMutateTheOriginal() {
        BillDto original = bill("40.00", null);
        when(getBillPersistencePort.lockBillById(TARGET_ID)).thenReturn(Optional.of(original));
        when(addBillPersistencePort.addBill(any())).thenAnswer(inv -> inv.getArgument(0));

        service.removeBill(TARGET_ID);

        assertThat(original.getAmount()).isEqualByComparingTo("40.00");
        assertThat(original.isReversal()).isFalse();
        assertThat(original.getCorrectsTransactionId()).isNull();
    }

    @Test
    void removeBill_onAlreadyCorrectedRow_reversesItsCurrentValue() {
        BillDto secondGeneration = bill("45.50", UUID.randomUUID().toString());
        when(getBillPersistencePort.lockBillById(TARGET_ID)).thenReturn(Optional.of(secondGeneration));
        when(addBillPersistencePort.addBill(any())).thenAnswer(inv -> inv.getArgument(0));

        service.removeBill(TARGET_ID);

        ArgumentCaptor<BillDto> captor = ArgumentCaptor.forClass(BillDto.class);
        verify(addBillPersistencePort).addBill(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("-45.50");
    }

    @Test
    void removeBill_unknownId_throwsNoSuchElement() {
        when(getBillPersistencePort.lockBillById(TARGET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeBill(TARGET_ID))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void removeBill_alreadyRemovedRow_throwsIllegalState() {
        BillDto target = bill("40.00", null);
        BillDto existingReversal = bill("-40.00", TARGET_ID.toString());
        existingReversal.setId(UUID.randomUUID().toString());

        when(getBillPersistencePort.lockBillById(TARGET_ID)).thenReturn(Optional.of(target));
        when(getBillPersistencePort.getAll()).thenReturn(List.of(target, existingReversal));

        assertThatThrownBy(() -> service.removeBill(TARGET_ID))
                .isInstanceOf(IllegalStateException.class);
    }

    private BillDto bill(String amount, String correctsTransactionId) {
        BillDto dto = new BillDto();
        dto.setId(TARGET_ID.toString());
        dto.setAmount(new BigDecimal(amount));
        dto.setDescription("Groceries");
        dto.setTime(TARGET_TIME);
        dto.setCategoryId("cat-1");
        dto.setAccountId("acc-1");
        dto.setCorrectsTransactionId(correctsTransactionId);
        return dto;
    }
}
