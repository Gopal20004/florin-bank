package com.florin.bank.statement;

import com.florin.bank.TestBank;
import com.florin.bank.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class StatementApiTests {

    private static final ZoneId BANK_ZONE = ZoneId.of("Asia/Kolkata");

    @Autowired MockMvc mvc;
    TestBank bank;

    @BeforeEach
    void setUp() {
        bank = new TestBank(mvc);
    }

    @Test
    void activityIsNewestFirstPagedAndNamesTheOtherSide() throws Exception {
        TestBank.Customer asha = bank.register("Asha Rao");
        TestBank.Customer ravi = bank.register("Ravi Kumar");
        Map<String, Object> rainyDay = bank.openAccount(asha, "SAVINGS", "Rainy Day Fund");
        bank.deposit(asha, asha.accountId(), "5000", "Salary").andExpect(status().isCreated());
        bank.transfer(asha, asha.accountId(), ravi.accountNumber(), "1200", "Dinner at the corner cafe")
                .andExpect(status().isCreated());
        bank.transfer(asha, asha.accountId(), (String) rainyDay.get("accountNumber"), "800", "Saving")
                .andExpect(status().isCreated());
        bank.withdraw(asha, asha.accountId(), "300", "Groceries").andExpect(status().isCreated());

        // Five lines: deposit, payment to Ravi, both legs of the move to savings, withdrawal.
        mvc.perform(get("/api/activity").param("size", "2").header("Authorization", asha.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content[0].type").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.content[0].balanceAfter").value(2700.0))
                .andExpect(jsonPath("$.content[0].account.nickname").value("Everyday Savings"))
                .andExpect(jsonPath("$.content[1].direction").value("CREDIT"))
                .andExpect(jsonPath("$.content[1].account.nickname").value("Rainy Day Fund"))
                .andExpect(jsonPath("$.content[1].counterparty.name").value("Everyday Savings"))
                .andExpect(jsonPath("$.content[1].counterparty.own").value(true));

        mvc.perform(get("/api/activity").param("size", "2").param("page", "1").header("Authorization", asha.bearer()))
                .andExpect(jsonPath("$.content[0].counterparty.name").value("Rainy Day Fund"))
                .andExpect(jsonPath("$.content[1].description").value("Dinner at the corner cafe"))
                .andExpect(jsonPath("$.content[1].counterparty.name").value("Ravi K."))
                .andExpect(jsonPath("$.content[1].counterparty.own").value(false));

        mvc.perform(get("/api/activity").param("page", "2").param("size", "2").header("Authorization", asha.bearer()))
                .andExpect(jsonPath("$.content[0].type").value("DEPOSIT"))
                .andExpect(jsonPath("$.content[0].counterparty").doesNotExist());

        // Ravi sees his side only, and Asha's name masked.
        mvc.perform(get("/api/activity").header("Authorization", ravi.bearer()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].direction").value("CREDIT"))
                .andExpect(jsonPath("$.content[0].counterparty.name").value("Asha R."));
    }

    @Test
    void activityCanBeFilteredAndSearched() throws Exception {
        TestBank.Customer asha = bank.register("Asha Rao");
        Map<String, Object> rainyDay = bank.openAccount(asha, "SAVINGS", "Rainy Day Fund");
        long rainyDayId = ((Number) rainyDay.get("id")).longValue();
        String deposit = bank.deposit(asha, asha.accountId(), "5000", "Salary for October")
                .andReturn().getResponse().getContentAsString();
        bank.transfer(asha, asha.accountId(), (String) rainyDay.get("accountNumber"), "800", "Saving 100% of it")
                .andExpect(status().isCreated());
        bank.withdraw(asha, asha.accountId(), "300", "Groceries").andExpect(status().isCreated());
        String today = LocalDate.now(BANK_ZONE).toString();
        String tomorrow = LocalDate.now(BANK_ZONE).plusDays(1).toString();

        expectCount(asha, 2, "direction", "CREDIT");
        expectCount(asha, 2, "direction", "DEBIT");
        expectCount(asha, 1, "q", "OCTOBER");
        expectCount(asha, 1, "q", JsonPath.read(deposit, "$.reference"));
        // LIKE wildcards in a search are literal characters, not patterns.
        expectCount(asha, 2, "q", "100%");
        expectCount(asha, 0, "q", "_");
        expectCount(asha, 4, "from", today, "to", today);
        expectCount(asha, 0, "from", tomorrow);
        expectCount(asha, 1, "accountId", String.valueOf(rainyDayId));

        mvc.perform(get("/api/accounts/{id}/transactions", rainyDayId).header("Authorization", asha.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].amount").value(800.0));

        TestBank.Customer ravi = bank.register("Ravi Kumar");
        mvc.perform(get("/api/accounts/{id}/transactions", rainyDayId).header("Authorization", ravi.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/activity").param("accountId", String.valueOf(rainyDayId)).header("Authorization", ravi.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/activity").param("size", "500").header("Authorization", asha.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Ask for at most 100 lines per page"));
    }

    @Test
    void statementsDownloadAsCsvWithSpreadsheetFormulasDefused() throws Exception {
        TestBank.Customer asha = bank.register("Asha Rao");
        bank.deposit(asha, asha.accountId(), "1000", "=HYPERLINK(\"http://evil.example\",\"Click\")")
                .andExpect(status().isCreated());
        bank.withdraw(asha, asha.accountId(), "250.50", "Tea, biscuits").andExpect(status().isCreated());
        LocalDate today = LocalDate.now(BANK_ZONE);

        var response = mvc.perform(get("/api/accounts/{id}/statement", asha.accountId())
                        .param("from", today.minusDays(1).toString()).param("to", today.toString())
                        .header("Authorization", asha.bearer()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith("text/csv")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("florin-statement-" + asha.accountNumber().substring(8))))
                .andReturn().getResponse();

        String[] lines = response.getContentAsString().split("\r\n");
        assertEquals("﻿Date,Reference,Description,Type,Counterparty,Debit,Credit,Balance", lines[0]);
        assertEquals(3, lines.length);
        assertTrue(lines[1].contains(",\"'=HYPERLINK(\"\"http://evil.example\"\",\"\"Click\"\")\",DEPOSIT,\"Cash deposit\",,1000.00,1000.00"),
                lines[1]);
        assertTrue(lines[2].endsWith(",\"Tea, biscuits\",WITHDRAWAL,\"Cash withdrawal\",250.50,,749.50"), lines[2]);

        mvc.perform(get("/api/accounts/{id}/statement", asha.accountId())
                        .param("from", today.toString()).param("to", today.minusDays(1).toString())
                        .header("Authorization", asha.bearer()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/accounts/{id}/statement", asha.accountId())
                        .param("from", today.minusYears(2).toString()).param("to", today.toString())
                        .header("Authorization", asha.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Download at most one year of statements at a time"));
    }

    private void expectCount(TestBank.Customer customer, int count, String... params) throws Exception {
        var request = get("/api/activity").header("Authorization", customer.bearer());
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        mvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(count));
    }
}
