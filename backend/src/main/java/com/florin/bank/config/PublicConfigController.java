package com.florin.bank.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/** Rules the web app shows to customers, so the UI never hard-codes what the server enforces. */
@RestController
@RequestMapping("/api/config")
public class PublicConfigController {

    private final BankProperties bank;

    public PublicConfigController(BankProperties bank) {
        this.bank = bank;
    }

    @GetMapping
    public PublicConfig config() {
        BankProperties.Limits limits = bank.limits();
        BankProperties.Demo demo = bank.demo();
        return new PublicConfig(bank.currency(), bank.zone().getId(), bank.branchCode(), limits.perTransaction(),
                limits.dailyOutgoing(), limits.maxAccounts(), limits.maxPayees(),
                demo != null && demo.seed() ? new PublicConfig.DemoLogin(demo.email(), demo.password()) : null);
    }

    /** {@code demo} is only present on a demo deployment, where its login is meant to be public. */
    public record PublicConfig(String currency, String zone, String branchCode, BigDecimal perTransactionLimit,
                               BigDecimal dailyOutgoingLimit, int maxAccounts, int maxPayees, DemoLogin demo) {

        public record DemoLogin(String email, String password) {
        }
    }
}
