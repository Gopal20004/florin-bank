package com.florin.bank.payee;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payees")
public class PayeeController {

    private final PayeeService service;

    public PayeeController(PayeeService service) {
        this.service = service;
    }

    @GetMapping
    public List<PayeeResponse> list(@AuthenticationPrincipal Long customerId) {
        return service.list(customerId);
    }

    // A POST so the account number travels in the body, not in URLs that end up in access logs.
    @PostMapping("/lookup")
    public AccountLookupResponse lookup(@AuthenticationPrincipal Long customerId,
                                        @Valid @RequestBody AccountLookupRequest request) {
        return service.lookup(customerId, request.accountNumber());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PayeeResponse add(@AuthenticationPrincipal Long customerId, @Valid @RequestBody AddPayeeRequest request) {
        return service.add(customerId, request);
    }

    @PatchMapping("/{id}")
    public PayeeResponse rename(@AuthenticationPrincipal Long customerId, @PathVariable Long id,
                                @Valid @RequestBody RenamePayeeRequest request) {
        return service.rename(customerId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal Long customerId, @PathVariable Long id) {
        service.remove(customerId, id);
    }
}
