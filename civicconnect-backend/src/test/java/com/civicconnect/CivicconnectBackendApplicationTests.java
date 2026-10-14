package com.civicconnect;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Tag("integration") // needs the database from .env
class CivicconnectBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
