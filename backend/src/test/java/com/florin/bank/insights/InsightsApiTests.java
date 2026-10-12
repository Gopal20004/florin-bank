package com.florin.bank.insights;

import com.florin.bank.TestBank;
import com.florin.bank.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class InsightsApiTests {

    @Autowired MockMvc mvc;
    TestBank bank;

    @BeforeEach
    void setUp() {
        bank = new TestBank(mvc);
    }

    @Test
    void moneyInAndOutIgnoreTransfersBetweenYourOwnAccounts() throws Exception {
        TestBank.Customer asha = bank.register("Asha Rao");
        TestBank.Customer ravi = bank.register("Ravi Kumar");
        Map<String, Object> rainyDay = bank.openAccount(asha, "SAVINGS", "Rainy Day Fund");
        bank.deposit(asha, asha.accountId(), "10000", "Salary");
        bank.transfer(asha, asha.accountId(), ravi.accountNumber(), "2500", "Rent share");
        bank.transfer(asha, asha.accountId(), (String) rainyDay.get("accountNumber"), "1000", "Saving");
        bank.withdraw(asha, asha.accountId(), "500", "Groceries");
        String today = LocalDate.now(ZoneId.of("Asia/Kolkata")).toString();

        mvc.perform(get("/api/insights").header("Authorization", asha.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.totalBalance").value(7000.0))
                .andExpect(jsonPath("$.monthIn").value(10000.0))
                .andExpect(jsonPath("$.monthOut").value(3000.0))
                .andExpect(jsonPath("$.previousMonthIn").value(0.0))
                .andExpect(jsonPath("$.history.length()").value(30))
                .andExpect(jsonPath("$.history[0].balance").value(0.0))
                .andExpect(jsonPath("$.history[29].date").value(today))
                .andExpect(jsonPath("$.history[29].balance").value(7000.0));

        mvc.perform(get("/api/insights").param("days", "90").header("Authorization", ravi.bearer()))
                .andExpect(jsonPath("$.history.length()").value(90))
                .andExpect(jsonPath("$.monthIn").value(2500.0))
                .andExpect(jsonPath("$.monthOut").value(0.0));
    }

    @Test
    void theWindowIsBetweenAWeekAndAYear() throws Exception {
        TestBank.Customer asha = bank.register("Asha Rao");
        mvc.perform(get("/api/insights").param("days", "3").header("Authorization", asha.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Ask for at least 7 days"));
        mvc.perform(get("/api/insights").param("days", "400").header("Authorization", asha.bearer()))
                .andExpect(status().isBadRequest());
    }
}
