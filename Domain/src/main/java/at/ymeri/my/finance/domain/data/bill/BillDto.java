package at.ymeri.my.finance.domain.data.bill;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
public class BillDto {

    private String id;
    private String description;
    private BigDecimal amount;
    private OffsetDateTime time;
}
