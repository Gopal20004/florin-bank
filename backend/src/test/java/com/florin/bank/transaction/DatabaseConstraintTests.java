package com.florin.bank.transaction;

import com.florin.bank.TestBank;
import com.florin.bank.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The schema enforces the money rules on its own, underneath the application. These writes bypass the
 * service layer on purpose, as a buggy future code path or a hand-run SQL fix would.
 *
 * <p>MySQL reports a failed CHECK as error 3819 with SQLSTATE HY000, which Spring's JDBC error-code table
 * does not classify, so the tests look at the vendor code itself. (Through JPA, Hibernate maps 3819 to a
 * constraint violation and the API answers 409.)
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class DatabaseConstraintTests {

    private static final int CHECK_CONSTRAINT_VIOLATED = 3819;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    TestBank bank;

    @BeforeEach
    void setUp() {
        bank = new TestBank(mvc);
    }

    @Test
    void aBalanceCanNeverBeNegative() throws Exception {
        TestBank.Customer asha = bank.register("Asha Rao");
        DataAccessException failure = assertThrows(DataAccessException.class,
                () -> jdbc.update("update accounts set balance = -0.01 where id = ?", asha.accountId()));
        assertEquals(CHECK_CONSTRAINT_VIOLATED, vendorCode(failure));
    }

    @Test
    void aClosedAccountMustBeEmpty() throws Exception {
        TestBank.Customer asha = bank.register("Asha Rao");
        bank.deposit(asha, asha.accountId(), "10", null).andExpect(status().isCreated());
        DataAccessException failure = assertThrows(DataAccessException.class,
                () -> jdbc.update("update accounts set status = 'CLOSED' where id = ?", asha.accountId()));
        assertEquals(CHECK_CONSTRAINT_VIOLATED, vendorCode(failure));
    }

    @Test
    void ledgerEntriesNeedAPositiveAmountAndAKnownDirection() throws Exception {
        TestBank.Customer asha = bank.register("Asha Rao");
        String body = bank.deposit(asha, asha.accountId(), "10", null)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long transactionId = ((Number) JsonPath.read(body, "$.id")).longValue();
        Map<String, Object> other = bank.openAccount(asha, "SAVINGS", "Second");
        long otherAccount = ((Number) other.get("id")).longValue();
        String insert = "insert into ledger_entries (transaction_id, account_id, direction, amount, balance_after, created_at)"
                + " values (?, ?, ?, ?, ?, now(6))";

        assertEquals(CHECK_CONSTRAINT_VIOLATED, vendorCode(assertThrows(DataAccessException.class,
                () -> jdbc.update(insert, transactionId, otherAccount, "CREDIT", 0, 0))));
        assertEquals(CHECK_CONSTRAINT_VIOLATED, vendorCode(assertThrows(DataAccessException.class,
                // Six characters, so it fits VARCHAR(6) and the CHECK (not "data too long") is what rejects it.
                () -> jdbc.update(insert, transactionId, otherAccount, "REFUND", 1, 1))));
        assertEquals(CHECK_CONSTRAINT_VIOLATED, vendorCode(assertThrows(DataAccessException.class,
                () -> jdbc.update(insert, transactionId, otherAccount, "CREDIT", 5, -5))));
    }

    @Test
    void anAccountNumberBelongsToExactlyOneAccount() throws Exception {
        TestBank.Customer asha = bank.register("Asha Rao");
        TestBank.Customer ravi = bank.register("Ravi Kumar");
        assertThrows(DuplicateKeyException.class, () -> jdbc.update(
                "update accounts set account_number = ? where id = ?", ravi.accountNumber(), asha.accountId()));
    }

    private static int vendorCode(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) {
                return sql.getErrorCode();
            }
        }
        return -1;
    }
}
