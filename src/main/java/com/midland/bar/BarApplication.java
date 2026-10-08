package com.midland.bar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class BarApplication {

	public static void main(String[] args) {
		// Servers run on UTC, three hours behind the bars: shifts, sales and
		// every LocalDateTime.now() read 3 hours early and the day turned over
		// at 03:00. Tanzania time, wherever the server is.
		TimeZone.setDefault(TimeZone.getTimeZone("Africa/Dar_es_Salaam"));
		SpringApplication.run(BarApplication.class, args);
	}

}
