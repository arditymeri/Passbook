package at.ymeri.my.finance.domain.service.income;

import at.ymeri.my.finance.domain.data.income.IncomeDto;
import at.ymeri.my.finance.domain.data.income.IncomeSource;
import at.ymeri.my.finance.domain.spi.income.AddIncomePersistencePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddIncomeServiceImplTest {

    @Mock
    private AddIncomePersistencePort addIncomePersistencePort;

    @InjectMocks
    private AddIncomeServiceImpl addIncomeService;

    // --- happy path ---

    @Test
    void addIncome_validIncome_returnsSavedIncomeWithId() {
        // given
        IncomeDto request = validIncome();
        IncomeDto saved = validIncome();
        saved.setId("generated-id-123");
        when(addIncomePersistencePort.addIncome(request)).thenReturn(saved);

        // when
        IncomeDto result = addIncomeService.addIncome(request);

        // then
        assertThat(result.getId()).isEqualTo("generated-id-123");
        verify(addIncomePersistencePort).addIncome(request);
    }

    // --- amount validation ---

    @Test
    void addIncome_nullAmount_throwsIllegalArgumentException() {
        IncomeDto income = validIncome();
        income.setAmount(null);

        assertThatThrownBy(() -> addIncomeService.addIncome(income))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Amount is required");
    }

    @Test
    void addIncome_zeroAmount_throwsIllegalArgumentException() {
        IncomeDto income = validIncome();
        income.setAmount(BigDecimal.ZERO);

        assertThatThrownBy(() -> addIncomeService.addIncome(income))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Amount must be greater than zero");
    }

    @Test
    void addIncome_negativeAmount_throwsIllegalArgumentException() {
        IncomeDto income = validIncome();
        income.setAmount(new BigDecimal("-0.01"));

        assertThatThrownBy(() -> addIncomeService.addIncome(income))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Amount must be greater than zero");
    }

    // --- time validation ---

    @Test
    void addIncome_nullTime_throwsIllegalArgumentException() {
        IncomeDto income = validIncome();
        income.setTime(null);

        assertThatThrownBy(() -> addIncomeService.addIncome(income))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Time is required");
    }

    // --- helpers ---

    private IncomeDto validIncome() {
        IncomeDto income = new IncomeDto();
        income.setAmount(new BigDecimal("2500.00"));
        income.setTime(OffsetDateTime.now());
        income.setDescription("Monthly salary");
        income.setSource(IncomeSource.SALARY);
        return income;
    }
}
