package com.ichaabane.ecommerce;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
@Slf4j
public class PaymentApplication {

	public static void main(String[] args) {
		SpringApplication.run(PaymentApplication.class, args);
	}

	@EventListener(ApplicationReadyEvent.class)
	public void onStartup() {
		log.info("Payment service started successfully and is ready to process payments");
	}

	@PreDestroy
	public void onShutdown() {
		log.info("Payment service is shutting down");
	}
}
