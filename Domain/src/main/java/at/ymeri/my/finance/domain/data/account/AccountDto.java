package at.ymeri.my.finance.domain.data.account;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AccountDto {

    private String id;
    private String name;
    private AccountType type;
    private BigDecimal balance;
    private String currency;
    private String institution;
}
