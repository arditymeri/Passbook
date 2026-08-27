package at.ymeri.my.finance.domain.data.goal;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
public class SavingsGoalStatusDto {

    private String id;
    private String name;
    private BigDecimal targetAmount;
    private OffsetDateTime targetDate;
    private String accountId;
    private OffsetDateTime createdAt;
    private BigDecimal savedAmount;
    private BigDecimal percentComplete;
    private BigDecimal remainingAmount;
    private boolean achieved;
    private PaceStatus paceStatus;
}
