package at.ymeri.my.finance.integration.tests;

import at.ymeri.my.finance.MyFinanceApplication;
import at.ymeri.my.finance.application.data.AccountResponse;
import at.ymeri.my.finance.application.data.AccountType;
import at.ymeri.my.finance.application.data.BillListResponseModel;
import at.ymeri.my.finance.application.data.CreateAccountRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SC-004: two imports of overlapping statements running at the same moment must not both record the
 * shared transactions.
 *
 * <p><strong>This test has to be genuinely concurrent, and that is the entire point of it.</strong>
 * Two sequential calls would pass whether or not the uniqueness constraint exists — the second would
 * simply find the rows already there — which would leave SC-004 green while the guarantee it claims
 * is absent. A latch releases both threads together so their writes land in the same window.
 *
 * <p>What this proves is that the arbitration lives in the database. Application code that asked
 * "have I seen this?" before writing would fail here: both threads would look, both would see
 * nothing, and both would write.
 */
@SpringBootTest(
        classes = {MyFinanceApplication.class, TestConfig.class, TestDataSourceConfig.class, TestSecurityConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@EmbeddedKafka(partitions = 1, topics = {"booking.topic", "transaction.topic"}, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
public class ConcurrentIngestionIntegrationTest {

    /** Both statements contain these same rows, so every row is contested. */
    private static final String STATEMENT = """
            date,description,amount
            2026-06-01,CONTESTED SUPERMARKET,-54.20
            2026-06-02,CONTESTED COFFEE,-3.40
            2026-06-02,CONTESTED COFFEE,-3.40
            2026-06-03,CONTESTED PHARMACY,-18.90
            """;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void twoSimultaneousImportsOfTheSameStatementRecordEachTransactionOnce() throws Exception {
        String account = anAccount();

        CountDownLatch startTogether = new CountDownLatch(1);
        CountDownLatch bothFinished = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            for (int i = 0; i < 2; i++) {
                pool.submit(() -> {
                    try {
                        startTogether.await();
                        restTemplate.exchange("/statements/ingest", HttpMethod.POST,
                                multipart(STATEMENT, account), String.class);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        bothFinished.countDown();
                    }
                });
            }

            startTogether.countDown();
            assertThat(bothFinished.await(60, TimeUnit.SECONDS))
                    .as("both imports should complete")
                    .isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(billCount(account, "CONTESTED SUPERMARKET")).isEqualTo(1);
        assertThat(billCount(account, "CONTESTED PHARMACY")).isEqualTo(1);
        assertThat(billCount(account, "CONTESTED COFFEE"))
                .as("two identical rows in the statement means two transactions — and only two, "
                        + "however the two imports interleaved")
                .isEqualTo(2);
    }

    private String anAccount() {
        return restTemplate.postForEntity("/accounts",
                new CreateAccountRequest("Concurrent " + UUID.randomUUID(), AccountType.CHECKING,
                        List.of("EUR"), "EUR"),
                AccountResponse.class).getBody().getId().toString();
    }

    private HttpEntity<MultiValueMap<String, Object>> multipart(String csv, String accountId) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.TEXT_PLAIN);
        body.add("file", new HttpEntity<>(new ByteArrayResource(csv.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "statement.csv";
            }
        }, fileHeaders));
        body.add("accountId", accountId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return new HttpEntity<>(body, headers);
    }

    private long billCount(String accountId, String description) {
        BillListResponseModel list = restTemplate
                .getForEntity("/bills", BillListResponseModel.class).getBody();
        if (list == null || list.getBills() == null) {
            return 0;
        }
        return list.getBills().stream()
                .filter(bill -> accountId.equals(bill.getAccountId()))
                .filter(bill -> description.equals(bill.getDescription()))
                .count();
    }
}
