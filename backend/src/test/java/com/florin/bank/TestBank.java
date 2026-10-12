package com.florin.bank;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Banking scenarios over MockMvc, so tests read as "Asha pays Ravi" rather than as raw HTTP. */
public final class TestBank {

    public static final String PASSWORD = "correct-horse-battery";

    private final MockMvc mvc;

    public TestBank(MockMvc mvc) {
        this.mvc = mvc;
    }

    /** A signed-up, signed-in customer and the savings account every new customer gets. */
    public record Customer(long id, String email, String token, long accountId, String accountNumber) {
        public String bearer() {
            return "Bearer " + token;
        }
    }

    public Customer register(String fullName) throws Exception {
        String email = fullName.toLowerCase(Locale.ROOT).replace(' ', '.') + "-" + UUID.randomUUID() + "@example.com";
        String registered = mvc.perform(post("/api/customers/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}".formatted(fullName, email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String token = login(email, PASSWORD);
        String accounts = mvc.perform(get("/api/accounts").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> list = JsonPath.read(accounts, "$");
        return new Customer(((Number) JsonPath.read(registered, "$.id")).longValue(), email, token,
                ((Number) list.getFirst().get("id")).longValue(), (String) list.getFirst().get("accountNumber"));
    }

    public String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    /** Opens another account and returns its id and number. */
    public Map<String, Object> openAccount(Customer customer, String type, String nickname) throws Exception {
        String body = mvc.perform(post("/api/accounts").header("Authorization", customer.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"%s\",\"nickname\":\"%s\"}".formatted(type, nickname)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$");
    }

    public ResultActions deposit(Customer customer, long accountId, String amount, String note) throws Exception {
        return mvc.perform(post("/api/accounts/{id}/deposits", accountId).header("Authorization", customer.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":%s,\"description\":%s}".formatted(amount, quote(note))));
    }

    public ResultActions withdraw(Customer customer, long accountId, String amount, String note) throws Exception {
        return mvc.perform(post("/api/accounts/{id}/withdrawals", accountId).header("Authorization", customer.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":%s,\"description\":%s}".formatted(amount, quote(note))));
    }

    public ResultActions transfer(Customer customer, long fromAccountId, String toAccountNumber, String amount,
                                  String note) throws Exception {
        return mvc.perform(post("/api/transfers").header("Authorization", customer.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(transferJson(fromAccountId, toAccountNumber, amount, note)));
    }

    public static String transferJson(long fromAccountId, String toAccountNumber, String amount, String note) {
        return "{\"fromAccountId\":%d,\"toAccountNumber\":\"%s\",\"amount\":%s,\"description\":%s}"
                .formatted(fromAccountId, toAccountNumber, amount, quote(note));
    }

    public BigDecimal balance(Customer customer, long accountId) throws Exception {
        String body = mvc.perform(get("/api/accounts/{id}/balance", accountId).header("Authorization", customer.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return money(JsonPath.read(body, "$.balance"));
    }

    /** JSON numbers come back from JsonPath as Integer or Double; compare them as exact rupee amounts. */
    public static BigDecimal money(Object number) {
        return new BigDecimal(String.valueOf(number)).setScale(2);
    }

    private static String quote(String value) {
        return value == null ? "null" : "\"" + value.replace("\"", "\\\"") + "\"";
    }
}
