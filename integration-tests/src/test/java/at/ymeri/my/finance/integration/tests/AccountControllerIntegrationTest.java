package at.ymeri.my.finance.integration.tests;

import at.ymeri.my.finance.MyFinanceApplication;
import at.ymeri.my.finance.application.data.AccountResponse;
import at.ymeri.my.finance.application.data.AccountType;
import at.ymeri.my.finance.application.data.Bill;
import at.ymeri.my.finance.application.data.BillResponseModel;
import at.ymeri.my.finance.application.data.CorrectBillRequest;
import at.ymeri.my.finance.application.data.CreateAccountRequest;
import at.ymeri.my.finance.application.data.CreateIncomeRequest;
import at.ymeri.my.finance.application.data.IncomeResponse;
import at.ymeri.my.finance.application.data.UpdateAccountRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = {MyFinanceApplication.class, TestConfig.class, TestDataSourceConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@EmbeddedKafka(partitions = 1, topics = {"booking.topic", "transaction.topic"}, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
public class AccountControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    // --- create ---

    @Test
    void createAccount_validRequest_returns201WithId() {
        CreateAccountRequest request = new CreateAccountRequest("ING Checking", AccountType.CHECKING,
                List.of("EUR"), "EUR");

        ResponseEntity<AccountResponse> response = restTemplate
                .postForEntity("/accounts", request, AccountResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getId()).isNotNull();
        assertThat(response.getBody().getName()).isEqualTo("ING Checking");
        assertThat(response.getBody().getCurrencies()).containsExactly("EUR");
        assertThat(response.getBody().getDefaultCurrency()).isEqualTo("EUR");
    }

    @Test
    void createAccount_missingName_returns400() {
        CreateAccountRequest request = new CreateAccountRequest(null, AccountType.CHECKING,
                List.of("EUR"), "EUR");

        ResponseEntity<String> response = restTemplate
                .postForEntity("/accounts", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createAccount_invalidCurrencyCode_returns400() {
        CreateAccountRequest request = new CreateAccountRequest("My Account", AccountType.SAVINGS,
                List.of("XYZ"), "XYZ");

        ResponseEntity<String> response = restTemplate
                .postForEntity("/accounts", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createAccount_defaultCurrencyNotInList_returns400() {
        CreateAccountRequest request = new CreateAccountRequest("My Account", AccountType.SAVINGS,
                List.of("EUR"), "USD");

        ResponseEntity<String> response = restTemplate
                .postForEntity("/accounts", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createAccount_duplicateName_returns409() {
        CreateAccountRequest request = new CreateAccountRequest("Raiffeisen Savings", AccountType.SAVINGS,
                List.of("EUR"), "EUR");
        restTemplate.postForEntity("/accounts", request, AccountResponse.class);

        ResponseEntity<String> duplicate = restTemplate
                .postForEntity("/accounts", request, String.class);

        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // --- get ---

    @Test
    void getAccountById_exists_returns200() {
        CreateAccountRequest create = new CreateAccountRequest("Cash Wallet", AccountType.CASH,
                List.of("EUR", "USD"), "EUR");
        AccountResponse created = restTemplate
                .postForEntity("/accounts", create, AccountResponse.class).getBody();

        ResponseEntity<AccountResponse> response = restTemplate
                .getForEntity("/accounts/" + created.getId(), AccountResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getCurrencies()).containsExactlyInAnyOrder("EUR", "USD");
    }

    @Test
    void getAccountById_notFound_returns404() {
        ResponseEntity<String> response = restTemplate
                .getForEntity("/accounts/00000000-0000-0000-0000-000000000000", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // --- update ---

    @Test
    void updateAccount_validRequest_returns200() {
        CreateAccountRequest create = new CreateAccountRequest("OldAccountName", AccountType.CHECKING,
                List.of("EUR"), "EUR");
        AccountResponse created = restTemplate
                .postForEntity("/accounts", create, AccountResponse.class).getBody();

        UpdateAccountRequest update = new UpdateAccountRequest("NewAccountName", AccountType.SAVINGS,
                List.of("EUR", "CHF"), "CHF");
        ResponseEntity<AccountResponse> response = restTemplate.exchange(
                "/accounts/" + created.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(update),
                AccountResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getName()).isEqualTo("NewAccountName");
        assertThat(response.getBody().getDefaultCurrency()).isEqualTo("CHF");
    }

    // --- delete ---

    @Test
    void deleteAccount_notReferenced_returns204() {
        CreateAccountRequest create = new CreateAccountRequest("ToDeleteAccount", AccountType.CASH,
                List.of("EUR"), "EUR");
        AccountResponse created = restTemplate
                .postForEntity("/accounts", create, AccountResponse.class).getBody();

        ResponseEntity<Void> response = restTemplate.exchange(
                "/accounts/" + created.getId(),
                HttpMethod.DELETE, null, Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void deleteAccount_notFound_returns404() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/accounts/00000000-0000-0000-0000-000000000000",
                HttpMethod.DELETE, null, String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // --- derived balance ---

    @Test
    void getAccountById_noLinkedTransactions_balanceEqualsStartingBalance() {
        CreateAccountRequest create = new CreateAccountRequest("Balance Test Account", AccountType.CHECKING,
                List.of("EUR"), "EUR").balance(1000.0);
        AccountResponse created = restTemplate
                .postForEntity("/accounts", create, AccountResponse.class).getBody();

        AccountResponse fetched = restTemplate
                .getForEntity("/accounts/" + created.getId(), AccountResponse.class).getBody();

        assertThat(fetched.getBalance()).isEqualTo(1000.0);
    }

    @Test
    void getAccountById_linkedIncome_balanceReflectsIncome() {
        CreateAccountRequest create = new CreateAccountRequest("Income Linked Account", AccountType.CHECKING,
                List.of("EUR"), "EUR").balance(1000.0);
        AccountResponse account = restTemplate
                .postForEntity("/accounts", create, AccountResponse.class).getBody();

        CreateIncomeRequest income = new CreateIncomeRequest(200.0, OffsetDateTime.now())
                .accountId(account.getId().toString());
        restTemplate.postForEntity("/incomes", income, IncomeResponse.class);

        AccountResponse fetched = restTemplate
                .getForEntity("/accounts/" + account.getId(), AccountResponse.class).getBody();

        assertThat(fetched.getBalance()).isEqualTo(1200.0);
    }

    @Test
    void getAccountById_linkedBill_balanceReflectsBill() {
        CreateAccountRequest create = new CreateAccountRequest("Bill Linked Account", AccountType.CHECKING,
                List.of("EUR"), "EUR").balance(1000.0);
        AccountResponse account = restTemplate
                .postForEntity("/accounts", create, AccountResponse.class).getBody();

        Bill bill = new Bill().amount(50.0).time(OffsetDateTime.now()).accountId(account.getId().toString());
        restTemplate.postForEntity("/createBill", bill, BillResponseModel.class);

        AccountResponse fetched = restTemplate
                .getForEntity("/accounts/" + account.getId(), AccountResponse.class).getBody();

        assertThat(fetched.getBalance()).isEqualTo(950.0);
    }

    @Test
    void getAccountById_linkedBillAndIncome_balanceReflectsBoth() {
        CreateAccountRequest create = new CreateAccountRequest("Mixed Linked Account", AccountType.CHECKING,
                List.of("EUR"), "EUR").balance(1000.0);
        AccountResponse account = restTemplate
                .postForEntity("/accounts", create, AccountResponse.class).getBody();

        Bill bill = new Bill().amount(50.0).time(OffsetDateTime.now()).accountId(account.getId().toString());
        restTemplate.postForEntity("/createBill", bill, BillResponseModel.class);

        CreateIncomeRequest income = new CreateIncomeRequest(200.0, OffsetDateTime.now())
                .accountId(account.getId().toString());
        restTemplate.postForEntity("/incomes", income, IncomeResponse.class);

        AccountResponse fetched = restTemplate
                .getForEntity("/accounts/" + account.getId(), AccountResponse.class).getBody();

        assertThat(fetched.getBalance()).isEqualTo(1150.0);
    }

    @Test
    void correctBill_movesTheLinkedAccountsBalanceToTheCorrectedAmount() {
        CreateAccountRequest create = new CreateAccountRequest("Correction Balance Account", AccountType.CHECKING,
                List.of("EUR"), "EUR").balance(1000.0);
        AccountResponse account = restTemplate
                .postForEntity("/accounts", create, AccountResponse.class).getBody();

        Bill bill = new Bill().amount(50.0).time(OffsetDateTime.now()).accountId(account.getId().toString());
        UUID billId = restTemplate.postForEntity("/createBill", bill, BillResponseModel.class)
                .getBody().getBill().getId();

        CorrectBillRequest correction = new CorrectBillRequest(80.0, OffsetDateTime.now())
                .accountId(account.getId().toString());
        restTemplate.exchange("/bills/" + billId, HttpMethod.PUT,
                new HttpEntity<>(correction), BillResponseModel.class);

        AccountResponse fetched = restTemplate
                .getForEntity("/accounts/" + account.getId(), AccountResponse.class).getBody();

        // 1000 - 50 (original) + 50 (reversal) - 80 (corrected) = 920
        assertThat(fetched.getBalance()).isEqualTo(920.0);
    }

    @Test
    void removeBill_restoresTheLinkedAccountsBalance() {
        CreateAccountRequest create = new CreateAccountRequest("Removal Balance Account", AccountType.CHECKING,
                List.of("EUR"), "EUR").balance(1000.0);
        AccountResponse account = restTemplate
                .postForEntity("/accounts", create, AccountResponse.class).getBody();

        Bill bill = new Bill().amount(75.0).time(OffsetDateTime.now()).accountId(account.getId().toString());
        UUID billId = restTemplate.postForEntity("/createBill", bill, BillResponseModel.class)
                .getBody().getBill().getId();

        restTemplate.exchange("/bills/" + billId, HttpMethod.DELETE, null, Void.class);

        AccountResponse fetched = restTemplate
                .getForEntity("/accounts/" + account.getId(), AccountResponse.class).getBody();

        assertThat(fetched.getBalance()).isEqualTo(1000.0);
    }

    @Test
    void createBill_noAccountId_stillSucceedsWithoutAffectingAnyAccountBalance() {
        CreateAccountRequest create = new CreateAccountRequest("Unaffected Account", AccountType.CHECKING,
                List.of("EUR"), "EUR").balance(1000.0);
        AccountResponse account = restTemplate
                .postForEntity("/accounts", create, AccountResponse.class).getBody();

        Bill bill = new Bill().amount(75.0).time(OffsetDateTime.now());
        ResponseEntity<BillResponseModel> response = restTemplate.postForEntity("/createBill", bill, BillResponseModel.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        AccountResponse fetched = restTemplate
                .getForEntity("/accounts/" + account.getId(), AccountResponse.class).getBody();

        assertThat(fetched.getBalance()).isEqualTo(1000.0);
    }
}
