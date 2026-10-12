package com.florin.bank.transaction;

import com.florin.bank.TestBank;
import com.florin.bank.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Many requests at once against a real Tomcat and MySQL, released together by a latch. Checks the
 * properties that matter for money: nothing is created or lost, nothing goes negative, nothing is
 * paid twice, and every balance equals the sum of its ledger.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ConcurrencyTests {

    @LocalServerPort int port;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    TestBank bank;
    RestClient client;

    @BeforeEach
    void setUp() {
        bank = new TestBank(mvc);
        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
    }

    @Test
    void opposingTransfersNeitherDeadlockNorLoseMoney() throws Exception {
        TestBank.Customer asha = bank.register("Asha Rao");
        TestBank.Customer ravi = bank.register("Ravi Kumar");
        bank.deposit(asha, asha.accountId(), "50000", null).andExpect(status().isCreated());
        bank.deposit(ravi, ravi.accountId(), "50000", null).andExpect(status().isCreated());

        List<Callable<ResponseEntity<String>>> calls = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            calls.add(() -> transfer(asha, asha.accountId(), ravi.accountNumber(), "37.00", null));
            calls.add(() -> transfer(ravi, ravi.accountId(), asha.accountNumber(), "23.00", null));
        }
        for (ResponseEntity<String> response : runTogether(calls)) {
            assertEquals(201, response.getStatusCode().value(), response.getBody());
        }

        // 30 x 37 out and 30 x 23 in for Asha; the mirror image for Ravi; the total is unchanged.
        assertEquals(new BigDecimal("49580.00"), bank.balance(asha, asha.accountId()));
        assertEquals(new BigDecimal("50420.00"), bank.balance(ravi, ravi.accountId()));
        assertLedgerMatchesBalance(asha.accountId());
        assertLedgerMatchesBalance(ravi.accountId());
    }

    @Test
    void simultaneousWithdrawalsCanNeverOverdraw() throws Exception {
        TestBank.Customer asha = bank.register("Asha Rao");
        bank.deposit(asha, asha.accountId(), "1000", null).andExpect(status().isCreated());

        List<Callable<ResponseEntity<String>>> calls = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            calls.add(() -> client.post().uri("/api/accounts/{id}/withdrawals", asha.accountId())
                    .header("Authorization", asha.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"amount\":100}")
                    .retrieve().toEntity(String.class));
        }
        int paid = 0;
        int refused = 0;
        for (ResponseEntity<String> response : runTogether(calls)) {
            if (response.getStatusCode().value() == 201) {
                paid++;
            } else {
                assertEquals(422, response.getStatusCode().value(), response.getBody());
                refused++;
            }
        }

        assertEquals(10, paid);
        assertEquals(15, refused);
        assertEquals(new BigDecimal("0.00"), bank.balance(asha, asha.accountId()));
        assertLedgerMatchesBalance(asha.accountId());
    }

    @Test
    void aRetryStormWithOneIdempotencyKeyPaysOnce() throws Exception {
        TestBank.Customer asha = bank.register("Asha Rao");
        TestBank.Customer ravi = bank.register("Ravi Kumar");
        bank.deposit(asha, asha.accountId(), "1000", null).andExpect(status().isCreated());
        String key = "retry-" + UUID.randomUUID();

        List<Callable<ResponseEntity<String>>> calls = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            calls.add(() -> transfer(asha, asha.accountId(), ravi.accountNumber(), "100", key));
        }
        Set<String> references = new HashSet<>();
        for (ResponseEntity<String> response : runTogether(calls)) {
            assertEquals(201, response.getStatusCode().value(), response.getBody());
            references.add(JsonPath.read(response.getBody(), "$.reference"));
        }

        assertEquals(1, references.size());
        assertEquals(new BigDecimal("900.00"), bank.balance(asha, asha.accountId()));
        assertEquals(new BigDecimal("100.00"), bank.balance(ravi, ravi.accountId()));
        assertEquals(1, jdbc.queryForObject("select count(*) from transactions where idempotency_key = ?", Integer.class, key));
    }

    private ResponseEntity<String> transfer(TestBank.Customer from, long fromAccountId, String to, String amount,
                                            String key) {
        RestClient.RequestBodySpec request = client.post().uri("/api/transfers")
                .header("Authorization", from.bearer())
                .contentType(MediaType.APPLICATION_JSON);
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return request.body(TestBank.transferJson(fromAccountId, to, amount, "Concurrent"))
                .retrieve().toEntity(String.class);
    }

    /** Starts every call on its own virtual thread and releases them at the same instant. */
    private static <T> List<T> runTogether(List<Callable<T>> calls) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> call : calls) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return call.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        }
    }

    /** The stored balance, the sum of the account's ledger and its last running balance must all agree. */
    private void assertLedgerMatchesBalance(long accountId) {
        Map<String, Object> row = jdbc.queryForMap("""
                select a.balance,
                       (select coalesce(sum(case when e.direction = 'CREDIT' then e.amount else -e.amount end), 0)
                          from ledger_entries e where e.account_id = a.id) as ledger_sum,
                       (select e.balance_after from ledger_entries e where e.account_id = a.id
                          order by e.id desc limit 1) as last_balance_after
                from accounts a where a.id = ?
                """, accountId);
        BigDecimal balance = (BigDecimal) row.get("balance");
        assertEquals(0, balance.compareTo((BigDecimal) row.get("ledger_sum")), "ledger sum of account " + accountId);
        assertEquals(0, balance.compareTo((BigDecimal) row.get("last_balance_after")), "running balance of account " + accountId);
    }
}
