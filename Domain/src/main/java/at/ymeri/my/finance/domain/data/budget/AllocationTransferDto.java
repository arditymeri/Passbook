package at.ymeri.my.finance.domain.data.budget;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
public class AllocationTransferDto {

    private String id;
    private String fromCategoryId;
    private String toCategoryId;
    private int year;
    private int month;
    private BigDecimal amount;
    private OffsetDateTime createdAt;
}
