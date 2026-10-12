package com.florin.bank.transaction;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Money movement. Every POST accepts an optional {@code Idempotency-Key} header: repeating a request
 * with the same key returns the original transaction (marked {@code Idempotent-Replayed: true}) instead
 * of moving the money again.
 */
@RestController
@RequestMapping("/api")
public class TransactionController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
    }

    @PostMapping("/accounts/{accountId}/deposits")
    public ResponseEntity<TransactionResponse> deposit(@AuthenticationPrincipal Long customerId,
                                                       @PathVariable Long accountId,
                                                       @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String key,
                                                       @Valid @RequestBody MoneyRequest request) {
        return created(service.deposit(customerId, accountId, request, key));
    }

    @PostMapping("/accounts/{accountId}/withdrawals")
    public ResponseEntity<TransactionResponse> withdraw(@AuthenticationPrincipal Long customerId,
                                                        @PathVariable Long accountId,
                                                        @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String key,
                                                        @Valid @RequestBody MoneyRequest request) {
        return created(service.withdraw(customerId, accountId, request, key));
    }

    @PostMapping("/transfers")
    public ResponseEntity<TransactionResponse> transfer(@AuthenticationPrincipal Long customerId,
                                                        @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String key,
                                                        @Valid @RequestBody TransferRequest request) {
        return created(service.transfer(customerId, request, key));
    }

    @GetMapping("/transactions/{reference}")
    public TransactionResponse receipt(@AuthenticationPrincipal Long customerId, @PathVariable String reference) {
        return service.receipt(customerId, reference);
    }

    @GetMapping("/limits")
    public LimitsResponse limits(@AuthenticationPrincipal Long customerId) {
        return service.limits(customerId);
    }

    private static ResponseEntity<TransactionResponse> created(Posted posted) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotent-Replayed", Boolean.toString(posted.replayed()))
                .body(posted.transaction());
    }
}
