package com.midland.bar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BarApplication {

	public static void main(String[] args) {
		SpringApplication.run(BarApplication.class, args);
	}

}
