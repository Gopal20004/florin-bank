package com.florin.bank.account;

import com.florin.bank.TestBank;
import com.florin.bank.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AccountApiTests {

    @Autowired MockMvc mvc;
    TestBank bank;

    @BeforeEach
    void setUp() {
        bank = new TestBank(mvc);
    }

    @Test
    void newCustomersStartWithOneEmptySavingsAccount() throws Exception {
        TestBank.Customer ravi = bank.register("Ravi Kumar");

        mvc.perform(get("/api/accounts").header("Authorization", ravi.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("SAVINGS"))
                .andExpect(jsonPath("$[0].nickname").value("Everyday Savings"))
                .andExpect(jsonPath("$[0].balance").value(0.0))
                .andExpect(jsonPath("$[0].currency").value("INR"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].accountNumber").value(matchesPattern("[1-9]\\d{11}")));
        assertTrue(AccountNumbers.isValid(ravi.accountNumber()));
    }

    @Test
    void balanceInquiryReturnsTheCommittedBalance() throws Exception {
        TestBank.Customer ravi = bank.register("Ravi Kumar");
        bank.deposit(ravi, ravi.accountId(), "2500.50", "Cash").andExpect(status().isCreated());

        mvc.perform(get("/api/accounts/{id}/balance", ravi.accountId()).header("Authorization", ravi.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(2500.5))
                .andExpect(jsonPath("$.accountNumber").value(ravi.accountNumber()))
                .andExpect(jsonPath("$.asOf").exists());
    }

    @Test
    void accountsCanBeOpenedUpToTheCap() throws Exception {
        TestBank.Customer ravi = bank.register("Ravi Kumar");
        Map<String, Object> studio = bank.openAccount(ravi, "CURRENT", "Studio");
        assertEquals("CURRENT", studio.get("type"));
        bank.openAccount(ravi, "SAVINGS", "Rainy Day Fund");
        bank.openAccount(ravi, "SAVINGS", "Travel");
        bank.openAccount(ravi, "SAVINGS", "Gifts");

        mvc.perform(post("/api/accounts").header("Authorization", ravi.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SAVINGS\",\"nickname\":\"One too many\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("up to 5 open accounts")));
    }

    @Test
    void openingNeedsAKnownAccountTypeAndASensibleName() throws Exception {
        TestBank.Customer ravi = bank.register("Ravi Kumar");
        mvc.perform(post("/api/accounts").header("Authorization", ravi.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"GOLD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mvc.perform(post("/api/accounts").header("Authorization", ravi.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"Studio\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.type").value("Choose a savings or current account"));
        mvc.perform(post("/api/accounts").header("Authorization", ravi.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SAVINGS\",\"nickname\":\"<script>\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/accounts").header("Authorization", ravi.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"CURRENT\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nickname").value("Current"));
    }

    @Test
    void aFrozenAccountStillReceivesMoneyButCannotSendIt() throws Exception {
        TestBank.Customer ravi = bank.register("Ravi Kumar");
        mvc.perform(patch("/api/accounts/{id}", ravi.accountId()).header("Authorization", ravi.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"  Rent & bills \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("Rent & bills"));
        bank.deposit(ravi, ravi.accountId(), "1000", null).andExpect(status().isCreated());

        mvc.perform(post("/api/accounts/{id}/freeze", ravi.accountId()).header("Authorization", ravi.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"));
        bank.withdraw(ravi, ravi.accountId(), "10", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("frozen")));
        bank.deposit(ravi, ravi.accountId(), "5", null).andExpect(status().isCreated());

        mvc.perform(post("/api/accounts/{id}/unfreeze", ravi.accountId()).header("Authorization", ravi.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        bank.withdraw(ravi, ravi.accountId(), "10", null).andExpect(status().isCreated());
        assertEquals(new BigDecimal("995.00"), bank.balance(ravi, ravi.accountId()));
    }

    @Test
    void onlyAnEmptyAccountCanBeClosedAndTheLastOneStaysOpen() throws Exception {
        TestBank.Customer ravi = bank.register("Ravi Kumar");
        Map<String, Object> travel = bank.openAccount(ravi, "SAVINGS", "Travel");
        long travelId = ((Number) travel.get("id")).longValue();
        bank.deposit(ravi, travelId, "500", null).andExpect(status().isCreated());

        mvc.perform(post("/api/accounts/{id}/close", travelId).header("Authorization", ravi.bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("remaining balance")));

        bank.transfer(ravi, travelId, ravi.accountNumber(), "500", "Emptying Travel").andExpect(status().isCreated());
        mvc.perform(post("/api/accounts/{id}/close", travelId).header("Authorization", ravi.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.closedAt").exists());
        bank.deposit(ravi, travelId, "1", null).andExpect(status().isConflict());
        bank.transfer(ravi, ravi.accountId(), (String) travel.get("accountNumber"), "1", null)
                .andExpect(status().isConflict());

        mvc.perform(post("/api/accounts/{id}/close", ravi.accountId()).header("Authorization", ravi.bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("only open account")));
    }

    @Test
    void anotherCustomersAccountLooksExactlyLikeAMissingOne() throws Exception {
        TestBank.Customer asha = bank.register("Asha Rao");
        TestBank.Customer ravi = bank.register("Ravi Kumar");

        mvc.perform(get("/api/accounts/{id}", ravi.accountId()).header("Authorization", asha.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/accounts/{id}/balance", ravi.accountId()).header("Authorization", asha.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/accounts/{id}/freeze", ravi.accountId()).header("Authorization", asha.bearer()))
                .andExpect(status().isNotFound());
        bank.deposit(asha, ravi.accountId(), "10", null).andExpect(status().isNotFound());
        bank.withdraw(asha, ravi.accountId(), "10", null).andExpect(status().isNotFound());
        mvc.perform(get("/api/accounts/{id}", 987_654_321L).header("Authorization", asha.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Account not found"));
    }
}
