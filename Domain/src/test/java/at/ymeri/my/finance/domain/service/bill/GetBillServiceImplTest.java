package at.ymeri.my.finance.domain.service.bill;

import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.spi.bill.GetBillPersistencePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetBillServiceImplTest {

    private static final UUID ORIGINAL = UUID.randomUUID();
    private static final UUID REVERSAL_1 = UUID.randomUUID();
    private static final UUID REPLACEMENT_1 = UUID.randomUUID();
    private static final UUID REVERSAL_2 = UUID.randomUUID();
    private static final UUID REPLACEMENT_2 = UUID.randomUUID();
    private static final UUID UNTOUCHED = UUID.randomUUID();

    @Mock
    private GetBillPersistencePort getBillPersistencePort;

    @InjectMocks
    private GetBillServiceImpl service;

    @Test
    void getAll_hidesReversalRows() {
        when(getBillPersistencePort.getAll()).thenReturn(List.of(
                bill(ORIGINAL, "40.00", null, false),
                bill(REVERSAL_1, "-40.00", ORIGINAL, true)));

        assertThat(service.getAll()).extracting(BillDto::getId)
                .doesNotContain(REVERSAL_1.toString());
    }

    @Test
    void getAll_hidesRowsSupersededByACorrection() {
        when(getBillPersistencePort.getAll()).thenReturn(List.of(
                bill(ORIGINAL, "40.00", null, false),
                bill(REVERSAL_1, "-40.00", ORIGINAL, true),
                bill(REPLACEMENT_1, "45.50", ORIGINAL, false)));

        assertThat(service.getAll()).extracting(BillDto::getId)
                .containsExactly(REPLACEMENT_1.toString());
    }

    @Test
    void getAll_showsPlainUncorrectedRows() {
        when(getBillPersistencePort.getAll()).thenReturn(List.of(
                bill(ORIGINAL, "40.00", null, false),
                bill(UNTOUCHED, "12.00", null, false)));

        assertThat(service.getAll()).extracting(BillDto::getId)
                .containsExactlyInAnyOrder(ORIGINAL.toString(), UNTOUCHED.toString());
    }

    @Test
    void getAll_inACorrectionChain_showsOnlyTheNewestReplacement() {
        when(getBillPersistencePort.getAll()).thenReturn(List.of(
                bill(ORIGINAL, "40.00", null, false),
                bill(REVERSAL_1, "-40.00", ORIGINAL, true),
                bill(REPLACEMENT_1, "45.50", ORIGINAL, false),
                bill(REVERSAL_2, "-45.50", REPLACEMENT_1, true),
                bill(REPLACEMENT_2, "50.00", REPLACEMENT_1, false)));

        assertThat(service.getAll()).extracting(BillDto::getId)
                .containsExactly(REPLACEMENT_2.toString());
    }

    @Test
    void getAll_hidesRowRemovedByAReversalWithNoReplacement() {
        when(getBillPersistencePort.getAll()).thenReturn(List.of(
                bill(ORIGINAL, "40.00", null, false),
                bill(REVERSAL_1, "-40.00", ORIGINAL, true),
                bill(UNTOUCHED, "12.00", null, false)));

        assertThat(service.getAll()).extracting(BillDto::getId)
                .containsExactly(UNTOUCHED.toString());
    }

    @Test
    void getBillById_staysUnfiltered_soOriginalsRemainFetchable() {
        when(getBillPersistencePort.getBillById(ORIGINAL))
                .thenReturn(Optional.of(bill(ORIGINAL, "40.00", null, false)));

        assertThat(service.getBillById(ORIGINAL).getAmount()).isEqualByComparingTo("40.00");
    }

    // --- history (US3) ---

    @Test
    void getHistory_walksCorrectsTransactionIdBackToTheOriginal() {
        when(getBillPersistencePort.getAll()).thenReturn(List.of(
                bill(ORIGINAL, "40.00", null, false),
                bill(REPLACEMENT_1, "45.50", ORIGINAL, false),
                bill(REPLACEMENT_2, "50.00", REPLACEMENT_1, false)));

        assertThat(service.getHistory(REPLACEMENT_2)).extracting(BillDto::getId)
                .containsExactly(REPLACEMENT_1.toString(), ORIGINAL.toString());
    }

    @Test
    void getHistory_forNeverCorrectedRow_isEmpty() {
        when(getBillPersistencePort.getAll())
                .thenReturn(List.of(bill(ORIGINAL, "40.00", null, false)));

        assertThat(service.getHistory(ORIGINAL)).isEmpty();
    }

    @Test
    void getHistory_excludesReversalRowsFromTheChain() {
        when(getBillPersistencePort.getAll()).thenReturn(List.of(
                bill(ORIGINAL, "40.00", null, false),
                bill(REVERSAL_1, "-40.00", ORIGINAL, true),
                bill(REPLACEMENT_1, "45.50", ORIGINAL, false)));

        assertThat(service.getHistory(REPLACEMENT_1)).extracting(BillDto::getId)
                .containsExactly(ORIGINAL.toString());
    }

    private BillDto bill(UUID id, String amount, UUID correctsTransactionId, boolean reversal) {
        BillDto dto = new BillDto();
        dto.setId(id.toString());
        dto.setAmount(new BigDecimal(amount));
        dto.setTime(OffsetDateTime.parse("2026-08-01T10:00:00Z"));
        dto.setCategoryId("cat-1");
        dto.setCorrectsTransactionId(correctsTransactionId == null ? null : correctsTransactionId.toString());
        dto.setReversal(reversal);
        return dto;
    }
}
