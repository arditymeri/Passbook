package at.ymeri.my.finance.domain.service.income;

import at.ymeri.my.finance.domain.data.income.IncomeDto;
import at.ymeri.my.finance.domain.data.income.IncomeSource;
import at.ymeri.my.finance.domain.spi.income.GetIncomePersistencePort;
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
class GetIncomeServiceImplTest {

    private static final UUID ORIGINAL = UUID.randomUUID();
    private static final UUID REVERSAL_1 = UUID.randomUUID();
    private static final UUID REPLACEMENT_1 = UUID.randomUUID();
    private static final UUID REVERSAL_2 = UUID.randomUUID();
    private static final UUID REPLACEMENT_2 = UUID.randomUUID();
    private static final UUID UNTOUCHED = UUID.randomUUID();

    @Mock
    private GetIncomePersistencePort getIncomePersistencePort;

    @InjectMocks
    private GetIncomeServiceImpl service;

    @Test
    void getAll_hidesReversalRows() {
        when(getIncomePersistencePort.getAll()).thenReturn(List.of(
                income(ORIGINAL, "2000.00", null, false),
                income(REVERSAL_1, "-2000.00", ORIGINAL, true)));

        assertThat(service.getAll()).extracting(IncomeDto::getId)
                .doesNotContain(REVERSAL_1.toString());
    }

    @Test
    void getAll_hidesRowsSupersededByACorrection() {
        when(getIncomePersistencePort.getAll()).thenReturn(List.of(
                income(ORIGINAL, "2000.00", null, false),
                income(REVERSAL_1, "-2000.00", ORIGINAL, true),
                income(REPLACEMENT_1, "2500.00", ORIGINAL, false)));

        assertThat(service.getAll()).extracting(IncomeDto::getId)
                .containsExactly(REPLACEMENT_1.toString());
    }

    @Test
    void getAll_showsPlainUncorrectedRows() {
        when(getIncomePersistencePort.getAll()).thenReturn(List.of(
                income(ORIGINAL, "2000.00", null, false),
                income(UNTOUCHED, "150.00", null, false)));

        assertThat(service.getAll()).extracting(IncomeDto::getId)
                .containsExactlyInAnyOrder(ORIGINAL.toString(), UNTOUCHED.toString());
    }

    @Test
    void getAll_inACorrectionChain_showsOnlyTheNewestReplacement() {
        when(getIncomePersistencePort.getAll()).thenReturn(List.of(
                income(ORIGINAL, "2000.00", null, false),
                income(REVERSAL_1, "-2000.00", ORIGINAL, true),
                income(REPLACEMENT_1, "2500.00", ORIGINAL, false),
                income(REVERSAL_2, "-2500.00", REPLACEMENT_1, true),
                income(REPLACEMENT_2, "3000.00", REPLACEMENT_1, false)));

        assertThat(service.getAll()).extracting(IncomeDto::getId)
                .containsExactly(REPLACEMENT_2.toString());
    }

    @Test
    void getAll_hidesRowRemovedByAReversalWithNoReplacement() {
        when(getIncomePersistencePort.getAll()).thenReturn(List.of(
                income(ORIGINAL, "2000.00", null, false),
                income(REVERSAL_1, "-2000.00", ORIGINAL, true),
                income(UNTOUCHED, "150.00", null, false)));

        assertThat(service.getAll()).extracting(IncomeDto::getId)
                .containsExactly(UNTOUCHED.toString());
    }

    @Test
    void getIncomeById_staysUnfiltered_soOriginalsRemainFetchable() {
        when(getIncomePersistencePort.getIncomeById(ORIGINAL))
                .thenReturn(Optional.of(income(ORIGINAL, "2000.00", null, false)));

        assertThat(service.getIncomeById(ORIGINAL).getAmount()).isEqualByComparingTo("2000.00");
    }

    // --- history (US3) ---

    @Test
    void getHistory_walksCorrectsTransactionIdBackToTheOriginal() {
        when(getIncomePersistencePort.getAll()).thenReturn(List.of(
                income(ORIGINAL, "2000.00", null, false),
                income(REPLACEMENT_1, "2500.00", ORIGINAL, false),
                income(REPLACEMENT_2, "3000.00", REPLACEMENT_1, false)));

        assertThat(service.getHistory(REPLACEMENT_2)).extracting(IncomeDto::getId)
                .containsExactly(REPLACEMENT_1.toString(), ORIGINAL.toString());
    }

    @Test
    void getHistory_forNeverCorrectedRow_isEmpty() {
        when(getIncomePersistencePort.getAll())
                .thenReturn(List.of(income(ORIGINAL, "2000.00", null, false)));

        assertThat(service.getHistory(ORIGINAL)).isEmpty();
    }

    @Test
    void getHistory_excludesReversalRowsFromTheChain() {
        when(getIncomePersistencePort.getAll()).thenReturn(List.of(
                income(ORIGINAL, "2000.00", null, false),
                income(REVERSAL_1, "-2000.00", ORIGINAL, true),
                income(REPLACEMENT_1, "2500.00", ORIGINAL, false)));

        assertThat(service.getHistory(REPLACEMENT_1)).extracting(IncomeDto::getId)
                .containsExactly(ORIGINAL.toString());
    }

    private IncomeDto income(UUID id, String amount, UUID correctsTransactionId, boolean reversal) {
        IncomeDto dto = new IncomeDto();
        dto.setId(id.toString());
        dto.setAmount(new BigDecimal(amount));
        dto.setTime(OffsetDateTime.parse("2026-08-01T09:00:00Z"));
        dto.setSource(IncomeSource.SALARY);
        dto.setCorrectsTransactionId(correctsTransactionId == null ? null : correctsTransactionId.toString());
        dto.setReversal(reversal);
        return dto;
    }
}
