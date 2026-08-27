package at.ymeri.my.finance.domain.data.recurring;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PriceChangeAlertDto {

    private String transactionId;
    private TransactionType transactionType;
    private String groupKey;
    private String description;
    private BigDecimal priorAmount;
    private BigDecimal newAmount;
    private BigDecimal delta;
}
