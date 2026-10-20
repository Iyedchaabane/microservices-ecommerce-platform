package com.ichaabane.ecommerce;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Context-load smoke test. Runs on the {@code test} profile so the
 * application boots against an in-memory H2 database (see
 * {@code src/test/resources/application-test.yml}) instead of the external
 * PostgreSQL/Config Server the service normally depends on at runtime.
 */
@SpringBootTest
@ActiveProfiles("test")
class PaymentApplicationTests {

	@Test
	void contextLoads() {
	}

}
