package com.florin.bank.payee;

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

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PayeeApiTests {

    @Autowired MockMvc mvc;
    TestBank bank;

    @BeforeEach
    void setUp() {
        bank = new TestBank(mvc);
    }

    @Test
    void lookingUpAnAccountShowsAMaskedNameBeforeAnythingIsSaved() throws Exception {
        TestBank.Customer asha = bank.register("Asha Rao");
        TestBank.Customer ravi = bank.register("Ravi Kumar");
        String spaced = ravi.accountNumber().substring(0, 4) + " " + ravi.accountNumber().substring(4);

        lookup(asha, spaced)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value(ravi.accountNumber()))
                .andExpect(jsonPath("$.holderName").value("Ravi K."))
                .andExpect(jsonPath("$.ownAccount").value(false))
                .andExpect(jsonPath("$.acceptsPayments").value(true));
        lookup(asha, asha.accountNumber()).andExpect(jsonPath("$.ownAccount").value(true));
        lookup(asha, "123456789012").andExpect(status().isBadRequest());
        lookup(asha, AccountNumbers.generate()).andExpect(status().isNotFound());
        lookup(asha, "").andExpect(status().isBadRequest());
    }

    @Test
    void payeesCanBeSavedRenamedAndRemoved() throws Exception {
        TestBank.Customer asha = bank.register("Asha Rao");
        TestBank.Customer ravi = bank.register("Ravi Kumar");

        String body = add(asha, ravi.accountNumber(), "Ravi · Rent")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nickname").value("Ravi · Rent"))
                .andExpect(jsonPath("$.holderName").value("Ravi K."))
                .andExpect(jsonPath("$.accountOpen").value(true))
                .andReturn().getResponse().getContentAsString();
        long payeeId = ((Number) JsonPath.read(body, "$.id")).longValue();

        add(asha, ravi.accountNumber(), "Again")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("already saved this account")));
        add(asha, asha.accountNumber(), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("your own accounts")));

        mvc.perform(patch("/api/payees/{id}", payeeId).header("Authorization", asha.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"Landlord\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("Landlord"));

        TestBank.Customer stranger = bank.register("Meera Iyer");
        mvc.perform(delete("/api/payees/{id}", payeeId).header("Authorization", stranger.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/payees").header("Authorization", asha.bearer()))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nickname").value("Landlord"));

        mvc.perform(delete("/api/payees/{id}", payeeId).header("Authorization", asha.bearer()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/payees").header("Authorization", asha.bearer()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void withoutANicknameAPayeeIsSavedUnderTheMaskedName() throws Exception {
        TestBank.Customer asha = bank.register("Asha Rao");
        TestBank.Customer ravi = bank.register("Ravi Kumar");
        add(asha, ravi.accountNumber(), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nickname").value("Ravi K."));
    }

    @Test
    void aClosedAccountCannotBeSavedAndShowsAsClosedOnceItCloses() throws Exception {
        TestBank.Customer asha = bank.register("Asha Rao");
        TestBank.Customer ravi = bank.register("Ravi Kumar");
        Map<String, Object> second = bank.openAccount(ravi, "SAVINGS", "Second");
        Map<String, Object> third = bank.openAccount(ravi, "SAVINGS", "Third");

        add(asha, (String) second.get("accountNumber"), "Ravi spare").andExpect(status().isCreated());
        close(ravi, ((Number) second.get("id")).longValue());
        mvc.perform(get("/api/payees").header("Authorization", asha.bearer()))
                .andExpect(jsonPath("$[0].accountOpen").value(false));

        close(ravi, ((Number) third.get("id")).longValue());
        add(asha, (String) third.get("accountNumber"), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("closed")));
        lookup(asha, (String) third.get("accountNumber")).andExpect(jsonPath("$.acceptsPayments").value(false));
    }

    private ResultActions lookup(TestBank.Customer customer, String accountNumber) throws Exception {
        return mvc.perform(post("/api/payees/lookup").header("Authorization", customer.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"accountNumber\":\"%s\"}".formatted(accountNumber)));
    }

    private ResultActions add(TestBank.Customer customer, String accountNumber, String nickname) throws Exception {
        String nick = nickname == null ? "null" : "\"" + nickname + "\"";
        return mvc.perform(post("/api/payees").header("Authorization", customer.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountNumber\":\"%s\",\"nickname\":%s}".formatted(accountNumber, nick)));
    }

    private void close(TestBank.Customer customer, long accountId) throws Exception {
        mvc.perform(post("/api/accounts/{id}/close", accountId).header("Authorization", customer.bearer()))
                .andExpect(status().isOk());
    }
}
