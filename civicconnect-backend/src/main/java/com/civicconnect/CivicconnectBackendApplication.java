package com.civicconnect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class CivicconnectBackendApplication {
--watch=false
	public static void main(String[] args) {
		// Everything runs in UTC (see common.time.Time): the JVM, the database session and stored timestamps
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(CivicconnectBackendApplication.class, args);
	}
}
