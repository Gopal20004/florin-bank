package com.florin.bank.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.ZoneId;

/** The bank's own rules, from the {@code florin.*} block of application.yaml. */
@ConfigurationProperties("florin")
public record BankProperties(ZoneId zone, String currency, String branchCode, Limits limits, Demo demo) {

    public record Limits(BigDecimal perTransaction, BigDecimal dailyOutgoing, int maxAccounts, int maxPayees) {
    }

    public record Demo(boolean seed, String email, String password) {
    }
}
