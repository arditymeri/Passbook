package at.ymeri.my.finance.domain.data.goal;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
public class SavingsGoalDto {

    private String id;
    private String name;
    private BigDecimal targetAmount;
    private OffsetDateTime targetDate;
    private String accountId;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
