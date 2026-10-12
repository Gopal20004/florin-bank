package com.florin.bank.customer;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse register(@Valid @RequestBody RegisterCustomerRequest request) {
        return customerService.register(request);
    }

    @GetMapping("/me")
    public CustomerResponse profile(@AuthenticationPrincipal Long customerId) {
        return customerService.profile(customerId);
    }

    @PatchMapping("/me")
    public CustomerResponse updateProfile(@AuthenticationPrincipal Long customerId,
                                          @Valid @RequestBody UpdateProfileRequest request) {
        return customerService.updateProfile(customerId, request);
    }
}
