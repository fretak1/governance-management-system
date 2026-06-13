package com.example.governance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GovernanceServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(GovernanceServiceApplication.class, args);
	}

}

