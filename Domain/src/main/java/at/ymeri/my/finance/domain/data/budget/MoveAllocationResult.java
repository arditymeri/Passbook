package at.ymeri.my.finance.domain.data.budget;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MoveAllocationResult {

    private AllocationTransferDto transfer;
    private BigDecimal fromEnvelopeBalance;
    private BigDecimal toEnvelopeBalance;
}
