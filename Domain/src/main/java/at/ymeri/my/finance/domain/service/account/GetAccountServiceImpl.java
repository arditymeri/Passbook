package at.ymeri.my.finance.domain.service.account;

import at.ymeri.my.finance.domain.api.GetAccountService;
import at.ymeri.my.finance.domain.data.account.AccountDto;
import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.data.income.IncomeDto;
import at.ymeri.my.finance.domain.spi.account.GetAccountPersistencePort;
import at.ymeri.my.finance.domain.spi.bill.GetBillPersistencePort;
import at.ymeri.my.finance.domain.spi.income.GetIncomePersistencePort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class GetAccountServiceImpl implements GetAccountService {

    private final GetAccountPersistencePort getAccountPersistencePort;
    private final GetBillPersistencePort getBillPersistencePort;
    private final GetIncomePersistencePort getIncomePersistencePort;

    public GetAccountServiceImpl(GetAccountPersistencePort getAccountPersistencePort,
                                 GetBillPersistencePort getBillPersistencePort,
                                 GetIncomePersistencePort getIncomePersistencePort) {
        this.getAccountPersistencePort = getAccountPersistencePort;
        this.getBillPersistencePort = getBillPersistencePort;
        this.getIncomePersistencePort = getIncomePersistencePort;
    }

    @Override
    public AccountDto getAccountById(String id) {
        AccountDto account = getAccountPersistencePort.getAccountById(id).orElseThrow();
        deriveBalance(account);
        return account;
    }

    @Override
    public List<AccountDto> getAll() {
        List<AccountDto> accounts = getAccountPersistencePort.getAll();
        accounts.forEach(this::deriveBalance);
        return accounts;
    }

    @Override
    public List<AccountDto> getByType(String type) {
        List<AccountDto> accounts = getAccountPersistencePort.getByType(type);
        accounts.forEach(this::deriveBalance);
        return accounts;
    }

    private void deriveBalance(AccountDto account) {
        BigDecimal startingBalance = account.getBalance() != null ? account.getBalance() : BigDecimal.ZERO;

        BigDecimal totalIncome = getIncomePersistencePort.getAll().stream()
                .filter(income -> account.getId().equals(income.getAccountId()))
                .map(IncomeDto::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalBills = getBillPersistencePort.getAll().stream()
                .filter(bill -> account.getId().equals(bill.getAccountId()))
                .map(BillDto::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        account.setBalance(startingBalance.add(totalIncome).subtract(totalBills));
    }
}
