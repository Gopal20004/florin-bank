package com.florin.bank.insights;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/insights")
public class InsightsController {

    private final InsightsService service;

    public InsightsController(InsightsService service) {
        this.service = service;
    }

    @GetMapping
    public InsightsResponse summary(@AuthenticationPrincipal Long customerId,
                                    @RequestParam(defaultValue = "30")
                                    @Min(value = 7, message = "Ask for at least 7 days")
                                    @Max(value = 365, message = "Ask for at most 365 days") int days) {
        return service.summary(customerId, days);
    }
}
