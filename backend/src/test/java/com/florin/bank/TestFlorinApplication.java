package com.florin.bank;

import org.springframework.boot.SpringApplication;

/** Runs the app against throwaway MySQL and Redis containers: no local database needed. */
public class TestFlorinApplication {

	public static void main(String[] args) {
		SpringApplication.from(FlorinApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
