package com.florin.bank.config;

import com.florin.bank.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs against a real Tomcat on purpose. MockMvc never performs the container's error dispatch, which is
 * exactly where a misconfigured security chain turns every 404 and 400 into an empty 401.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ErrorResponseTests {

    @LocalServerPort int port;
    RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
    }

    @Test
    void anonymousCallersGetA401ProblemBody() {
        ResponseEntity<String> response = client.get().uri("/api/accounts").retrieve().toEntity(String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertTrue(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertTrue(response.getBody().contains("Sign in to continue."), response.getBody());
    }

    @Test
    void signedInCallersGetTheRealStatus() {
        String bearer = "Bearer " + signUpAndIn();

        ResponseEntity<String> missing = client.get().uri("/api/no-such-thing").header("Authorization", bearer)
                .retrieve().toEntity(String.class);
        assertEquals(HttpStatus.NOT_FOUND, missing.getStatusCode());

        ResponseEntity<String> invalid = client.get().uri("/api/activity?size=0").header("Authorization", bearer)
                .retrieve().toEntity(String.class);
        assertEquals(HttpStatus.BAD_REQUEST, invalid.getStatusCode());
        assertTrue(invalid.getBody().contains("Ask for at least 1 line"), invalid.getBody());

        ResponseEntity<String> notAllowed = client.put().uri("/api/accounts").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).body("{}").retrieve().toEntity(String.class);
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, notAllowed.getStatusCode());

        ResponseEntity<String> wrongType = client.get().uri("/api/accounts/not-a-number").header("Authorization", bearer)
                .retrieve().toEntity(String.class);
        assertEquals(HttpStatus.BAD_REQUEST, wrongType.getStatusCode());
    }

    @Test
    void malformedJsonIsABadRequestNotAServerError() {
        ResponseEntity<String> response = client.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .body("{\"email\": ").retrieve().toEntity(String.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("MALFORMED_REQUEST"), response.getBody());
    }

    private String signUpAndIn() {
        String email = "errors-" + UUID.randomUUID() + "@example.com";
        client.post().uri("/api/customers/register").contentType(MediaType.APPLICATION_JSON)
                .body("{\"fullName\":\"Error Tester\",\"email\":\"" + email + "\",\"password\":\"secret-password\"}")
                .retrieve().toBodilessEntity();
        String login = client.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .body("{\"email\":\"" + email + "\",\"password\":\"secret-password\"}")
                .retrieve().body(String.class);
        return login.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
    }
}
