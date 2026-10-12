package com.florin.bank.auth;

import com.florin.bank.TestBank;
import com.florin.bank.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.florin.bank.TestBank.PASSWORD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuthApiTests {

    @Autowired MockMvc mvc;
    TestBank bank;

    @BeforeEach
    void setUp() {
        bank = new TestBank(mvc);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:5173", "http://127.0.0.1:5173"})
    void theLocalWebAppCanPreflightAPaymentWithAnIdempotencyKey(String origin) throws Exception {
        mvc.perform(options("/api/transfers")
                        .header("Origin", origin)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization, Content-Type, Idempotency-Key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", origin));
    }

    @Test
    void otherOriginsAreRefused() throws Exception {
        mvc.perform(options("/api/auth/login")
                        .header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void signUpSignInListSessionsAndSignOut() throws Exception {
        mvc.perform(get("/api/customers/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Sign in to continue."));

        String email = "Asha.Rao-" + UUID.randomUUID() + "@Example.com";
        mvc.perform(post("/api/customers/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"  Asha   Rao \",\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fullName").value("Asha Rao"))
                .andExpect(jsonPath("$.email").value(email.toLowerCase(Locale.ROOT)))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"wrong-password\"}".formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        // Sign-in normalises the email too, so the address works in any capitalisation.
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "Mozilla/5.0 (Macintosh) Florin tests")
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email.toUpperCase(Locale.ROOT), PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresAt").exists())
                .andReturn().getResponse().getContentAsString();
        String bearer = "Bearer " + JsonPath.read(body, "$.token");

        mvc.perform(get("/api/customers/me").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Asha Rao"));
        mvc.perform(get("/api/auth/sessions").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].current").value(true))
                .andExpect(jsonPath("$[0].userAgent").value("Mozilla/5.0 (Macintosh) Florin tests"));

        mvc.perform(delete("/api/auth/logout").header("Authorization", bearer))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/customers/me").header("Authorization", bearer))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anEmailCanOnlyBeRegisteredOnceInAnyCapitalisation() throws Exception {
        TestBank.Customer asha = bank.register("Asha Rao");
        mvc.perform(post("/api/customers/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Another Asha\",\"email\":\"%s\",\"password\":\"%s\"}"
                                .formatted(asha.email().toUpperCase(Locale.ROOT), PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void validationErrorsNameTheFieldInPlainWords() throws Exception {
        mvc.perform(post("/api/customers/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"R2-D2\",\"email\":\"not-an-email\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.fullName").value("Use letters, spaces, apostrophes, dots or hyphens in your name"))
                .andExpect(jsonPath("$.fields.email").value("Enter a valid email address"))
                .andExpect(jsonPath("$.fields.password").value("Use 8 to 100 characters for your password"));
    }

    @Test
    void changingThePasswordSignsOutEveryOtherDevice() throws Exception {
        TestBank.Customer asha = bank.register("Asha Rao");
        String laptop = asha.bearer();
        String phone = "Bearer " + bank.login(asha.email(), PASSWORD);

        mvc.perform(post("/api/auth/password").header("Authorization", laptop).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"not-my-password\",\"newPassword\":\"a-brand-new-secret\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Your current password is not correct"));

        mvc.perform(post("/api/auth/password").header("Authorization", laptop).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"%s\",\"newPassword\":\"a-brand-new-secret\"}".formatted(PASSWORD)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/customers/me").header("Authorization", phone)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/customers/me").header("Authorization", laptop)).andExpect(status().isOk());
        bank.login(asha.email(), "a-brand-new-secret");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(asha.email(), PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aSessionCanBeRevokedFromAnotherDeviceButNotByStrangers() throws Exception {
        TestBank.Customer asha = bank.register("Asha Rao");
        String phone = "Bearer " + bank.login(asha.email(), PASSWORD);
        String sessions = mvc.perform(get("/api/auth/sessions").header("Authorization", asha.bearer()))
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> list = JsonPath.read(sessions, "$[?(@.current == false)]");
        long phoneSession = ((Number) list.getFirst().get("id")).longValue();

        TestBank.Customer stranger = bank.register("Ravi Kumar");
        mvc.perform(delete("/api/auth/sessions/{id}", phoneSession).header("Authorization", stranger.bearer()))
                .andExpect(status().isNotFound());

        mvc.perform(delete("/api/auth/sessions/{id}", phoneSession).header("Authorization", asha.bearer()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/customers/me").header("Authorization", phone)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/customers/me").header("Authorization", asha.bearer())).andExpect(status().isOk());
    }

    @Test
    void theProfileNameCanBeChanged() throws Exception {
        TestBank.Customer asha = bank.register("Asha Rao");
        mvc.perform(patch("/api/customers/me").header("Authorization", asha.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\" Asha  D'Souza-Rao \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Asha D'Souza-Rao"));
        mvc.perform(patch("/api/customers/me").header("Authorization", asha.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
