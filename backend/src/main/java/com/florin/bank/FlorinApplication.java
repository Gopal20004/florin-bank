package com.florin.bank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Customers authenticate with bearer tokens (auth/SessionAuthenticationFilter), never with Spring's
// in-memory user, so its auto-configuration only printed an unused generated password at every start.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling
@ConfigurationPropertiesScan
public class FlorinApplication {

	public static void main(String[] args) {
		SpringApplication.run(FlorinApplication.class, args);
	}

}
