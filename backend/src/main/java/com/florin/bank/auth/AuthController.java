package com.florin.bank.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request,
                               @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        return authService.login(request, userAgent);
    }

    @DeleteMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader("Authorization") String authorization) {
        authService.logout(BearerToken.from(authorization));
    }

    @GetMapping("/sessions")
    public List<SessionResponse> sessions(@AuthenticationPrincipal Long customerId,
                                          @RequestHeader("Authorization") String authorization) {
        return authService.sessions(customerId, BearerToken.from(authorization));
    }

    @DeleteMapping("/sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal Long customerId, @PathVariable Long id) {
        authService.revoke(customerId, id);
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal Long customerId,
                               @RequestHeader("Authorization") String authorization,
                               @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(customerId, BearerToken.from(authorization), request);
    }
}
