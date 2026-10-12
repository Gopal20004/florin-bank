package com.florin.bank.account;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @GetMapping
    public List<AccountResponse> list(@AuthenticationPrincipal Long customerId) {
        return service.list(customerId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse open(@AuthenticationPrincipal Long customerId, @Valid @RequestBody OpenAccountRequest request) {
        return service.open(customerId, request);
    }

    @GetMapping("/{id}")
    public AccountResponse get(@AuthenticationPrincipal Long customerId, @PathVariable Long id) {
        return service.get(customerId, id);
    }

    @GetMapping("/{id}/balance")
    public BalanceResponse balance(@AuthenticationPrincipal Long customerId, @PathVariable Long id) {
        return service.balance(customerId, id);
    }

    @PatchMapping("/{id}")
    public AccountResponse rename(@AuthenticationPrincipal Long customerId, @PathVariable Long id,
                                  @Valid @RequestBody RenameAccountRequest request) {
        return service.rename(customerId, id, request);
    }

    @PostMapping("/{id}/freeze")
    public AccountResponse freeze(@AuthenticationPrincipal Long customerId, @PathVariable Long id) {
        return service.freeze(customerId, id);
    }

    @PostMapping("/{id}/unfreeze")
    public AccountResponse unfreeze(@AuthenticationPrincipal Long customerId, @PathVariable Long id) {
        return service.unfreeze(customerId, id);
    }

    @PostMapping("/{id}/close")
    public AccountResponse close(@AuthenticationPrincipal Long customerId, @PathVariable Long id) {
        return service.close(customerId, id);
    }
}
