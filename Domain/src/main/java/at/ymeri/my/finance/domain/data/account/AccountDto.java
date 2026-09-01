package at.ymeri.my.finance.domain.data.account;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Data
public class AccountDto {

    private String id;
    private String name;
    private AccountType type;
    private BigDecimal balance;
    private List<String> currencies;
    private String defaultCurrency;
    private String institution;
    private OffsetDateTime updatedAt;
}
