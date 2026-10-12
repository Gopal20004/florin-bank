package com.florin.bank.transaction;

import com.florin.bank.TestBank;
import com.florin.bank.TestcontainersConfiguration;
import com.florin.bank.account.AccountNumbers;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class MoneyMovementApiTests {

    @Autowired MockMvc mvc;
    TestBank bank;

    @BeforeEach
    void setUp() {
        bank = new TestBank(mvc);
    }

    @Test
    void depositsAndWithdrawalsMoveTheBalanceAndReturnAReceipt() throws Exception {
        TestBank.Customer asha = bank.register("Asha Rao");

        bank.deposit(asha, asha.accountId(), "10000", "Opening deposit")
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.reference").value(matchesPattern("FLR[0-9A-HJKMNP-TV-Z]{10}")))
                .andExpect(jsonPath("$.amount").value(10000.0))
                .andExpect(jsonPath("$.description").value("Opening deposit"))
                .andExpect(jsonPath("$.from").value(nullValue()))
                .andExpect(jsonPath("$.to.own").value(true))
                .andExpect(jsonPath("$.to.name").value("Everyday Savings"));

        bank.withdraw(asha, asha.accountId(), "2500.75", "  Rent   advance ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.description").value("Rent advance"))
                .andExpect(jsonPath("$.to").value(nullValue()));

        assertEquals(new BigDecimal("7499.25"), bank.balance(asha, asha.accountId()));
    }

    @Test
    void anOverdraftIsRefusedAndNamesTheAvailableBalance() throws Exception {
        TestBank.Customer asha = bank.register("Asha Rao");
        bank.deposit(asha, asha.accountId(), "100", null).andExpect(status().isCreated());

        bank.withdraw(asha, asha.accountId(), "100.01", null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$.available").value(100.0))
                .andExpect(jsonPath("$.detail").value(containsString("₹100.00")));
        assertEquals(new BigDecimal("100.00"), bank.balance(asha, asha.accountId()));
    }

    @Test
    void amountsMustBePositiveWithAtMostTwoDecimals() throws Exception {
        TestBank.Customer asha = bank.register("Asha Rao");
        bank.deposit(asha, asha.accountId(), "0", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.amount").value("Enter an amount of at least ₹0.01"));
        bank.deposit(asha, asha.accountId(), "-5", null).andExpect(status().isBadRequest());
        bank.deposit(asha, asha.accountId(), "10.123", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Enter an amount with at most two decimal places"));
        bank.deposit(asha, asha.accountId(), "null", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Enter an amount"));
        // @Digits counts decimals as written, so 10.500 is three places even though it equals 10.50.
        bank.deposit(asha, asha.accountId(), "10.500", null).andExpect(status().isBadRequest());
        bank.deposit(asha, asha.accountId(), "10.5", null).andExpect(status().isCreated());
        assertEquals(new BigDecimal("10.50"), bank.balance(asha, asha.accountId()));
    }

    @Test
    void eachSideOfATransferSeesOnlyWhatItMay() throws Exception {
        TestBank.Customer asha = bank.register("Asha Rao");
        TestBank.Customer ravi = bank.register("Ravi Kumar");
        bank.deposit(asha, asha.accountId(), "5000", null).andExpect(status().isCreated());

        String body = bank.transfer(asha, asha.accountId(), ravi.accountNumber(), "1200", "Dinner")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("TRANSFER"))
                .andExpect(jsonPath("$.from.own").value(true))
                .andExpect(jsonPath("$.from.name").value("Everyday Savings"))
                .andExpect(jsonPath("$.to.own").value(false))
                .andExpect(jsonPath("$.to.name").value("Ravi K."))
                .andExpect(jsonPath("$.to.accountNumber").value("•••• " + ravi.accountNumber().substring(8)))
                .andExpect(jsonPath("$.to.accountId").value(nullValue()))
                .andReturn().getResponse().getContentAsString();
        String reference = JsonPath.read(body, "$.reference");

        assertEquals(new BigDecimal("3800.00"), bank.balance(asha, asha.accountId()));
        assertEquals(new BigDecimal("1200.00"), bank.balance(ravi, ravi.accountId()));

        mvc.perform(get("/api/transactions/{reference}", reference.toLowerCase()).header("Authorization", ravi.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from.name").value("Asha R."))
                .andExpect(jsonPath("$.from.own").value(false))
                .andExpect(jsonPath("$.to.own").value(true));

        TestBank.Customer stranger = bank.register("Meera Iyer");
        mvc.perform(get("/api/transactions/{reference}", reference).header("Authorization", stranger.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void accountNumbersAreCheckedBeforeAnyLookup() throws Exception {
        TestBank.Customer asha = bank.register("Asha Rao");
        TestBank.Customer ravi = bank.register("Ravi Kumar");
        bank.deposit(asha, asha.accountId(), "100", null).andExpect(status().isCreated());
        String number = ravi.accountNumber();

        String spaced = number.substring(0, 4) + " " + number.substring(4, 8) + " " + number.substring(8);
        bank.transfer(asha, asha.accountId(), spaced, "1", null).andExpect(status().isCreated());

        char last = number.charAt(11);
        String typo = number.substring(0, 11) + (last == '9' ? '0' : (char) (last + 1));
        bank.transfer(asha, asha.accountId(), typo, "1", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("doesn't look right")));
        bank.transfer(asha, asha.accountId(), AccountNumbers.generate(), "1", null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No Florin account has that number"));
        bank.transfer(asha, asha.accountId(), asha.accountNumber(), "1", null)
                .andExpect(status().isBadRequest());
        bank.transfer(asha, ravi.accountId(), asha.accountNumber(), "1", null)
                .andExpect(status().isNotFound());
    }

    @Test
    void theSinglePaymentLimitAppliesToPaymentsButNotBetweenYourOwnAccounts() throws Exception {
        TestBank.Customer asha = bank.register("Asha Rao");
        TestBank.Customer ravi = bank.register("Ravi Kumar");
        Map<String, Object> savings = bank.openAccount(asha, "SAVINGS", "Rainy Day Fund");

        bank.deposit(asha, asha.accountId(), "200000.01", null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PER_TRANSACTION_LIMIT"));
        bank.deposit(asha, asha.accountId(), "200000", null).andExpect(status().isCreated());
        bank.deposit(asha, asha.accountId(), "200000", null).andExpect(status().isCreated());

        bank.transfer(asha, asha.accountId(), (String) savings.get("accountNumber"), "300000", "Into savings")
                .andExpect(status().isCreated());
        bank.transfer(asha, asha.accountId(), ravi.accountNumber(), "100000", null).andExpect(status().isCreated());
        bank.transfer(asha, ((Number) savings.get("id")).longValue(), ravi.accountNumber(), "200000.01", null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PER_TRANSACTION_LIMIT"));
    }

    @Test
    void theDailyLimitCountsWithdrawalsAndPaymentsToOthersOnly() throws Exception {
        TestBank.Customer asha = bank.register("Asha Rao");
        TestBank.Customer ravi = bank.register("Ravi Kumar");
        Map<String, Object> savings = bank.openAccount(asha, "SAVINGS", "Rainy Day Fund");
        for (int i = 0; i < 3; i++) {
            bank.deposit(asha, asha.accountId(), "200000", null).andExpect(status().isCreated());
        }

        bank.withdraw(asha, asha.accountId(), "200000", null).andExpect(status().isCreated());
        bank.transfer(asha, asha.accountId(), ravi.accountNumber(), "200000", null).andExpect(status().isCreated());
        bank.transfer(asha, asha.accountId(), (String) savings.get("accountNumber"), "50000", "Not counted")
                .andExpect(status().isCreated());

        mvc.perform(get("/api/limits").header("Authorization", asha.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyOutgoing").value(500000.0))
                .andExpect(jsonPath("$.usedToday").value(400000.0))
                .andExpect(jsonPath("$.remainingToday").value(100000.0))
                .andExpect(jsonPath("$.resetsAt").exists());

        bank.transfer(asha, asha.accountId(), ravi.accountNumber(), "100000.01", null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DAILY_LIMIT"))
                .andExpect(jsonPath("$.remaining").value(100000.0));
        bank.withdraw(asha, asha.accountId(), "100000", null).andExpect(status().isCreated());
        bank.withdraw(asha, asha.accountId(), "0.01", null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DAILY_LIMIT"));
        // Ravi's limit is his own: receiving money never uses it up.
        bank.withdraw(ravi, ravi.accountId(), "1000", null).andExpect(status().isCreated());
    }

    @Test
    void retryingWithTheSameIdempotencyKeyMovesMoneyOnce() throws Exception {
        TestBank.Customer asha = bank.register("Asha Rao");
        TestBank.Customer ravi = bank.register("Ravi Kumar");
        bank.deposit(asha, asha.accountId(), "1000", null).andExpect(status().isCreated());
        String key = "pay-" + UUID.randomUUID();

        String first = pay(asha, key, ravi.accountNumber(), "100")
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andReturn().getResponse().getContentAsString();
        String second = pay(asha, key, ravi.accountNumber(), "100")
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andReturn().getResponse().getContentAsString();

        assertEquals((String) JsonPath.read(first, "$.reference"), JsonPath.read(second, "$.reference"));
        assertEquals(new BigDecimal("900.00"), bank.balance(asha, asha.accountId()));
        assertEquals(new BigDecimal("100.00"), bank.balance(ravi, ravi.accountId()));

        pay(asha, key, ravi.accountNumber(), "250")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        pay(asha, "short", ravi.accountNumber(), "1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("Idempotency-Key")));
        // Keys belong to the customer: Ravi can use the same string for his own payment.
        mvc.perform(post("/api/accounts/{id}/withdrawals", ravi.accountId()).header("Authorization", ravi.bearer())
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"));
    }

    @Test
    void aFrozenAccountCannotPay() throws Exception {
        TestBank.Customer asha = bank.register("Asha Rao");
        TestBank.Customer ravi = bank.register("Ravi Kumar");
        bank.deposit(asha, asha.accountId(), "100", null).andExpect(status().isCreated());
        mvc.perform(post("/api/accounts/{id}/freeze", asha.accountId()).header("Authorization", asha.bearer()))
                .andExpect(status().isOk());

        bank.transfer(asha, asha.accountId(), ravi.accountNumber(), "1", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("frozen")));
    }

    private ResultActions pay(TestBank.Customer from, String key, String toAccountNumber, String amount) throws Exception {
        return mvc.perform(post("/api/transfers").header("Authorization", from.bearer())
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(TestBank.transferJson(from.accountId(), toAccountNumber, amount, "Rent share")));
    }
}
