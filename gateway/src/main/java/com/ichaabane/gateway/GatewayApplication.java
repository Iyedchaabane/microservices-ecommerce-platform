package com.ichaabane.gateway;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@SpringBootApplication
public class GatewayApplication {

	private static final Logger log = LoggerFactory.getLogger(GatewayApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(GatewayApplication.class, args);
	}

	@EventListener(ApplicationReadyEvent.class)
	public void onStartup() {
		log.info("API Gateway started successfully and is ready to route traffic");
	}

	@PreDestroy
	public void onShutdown() {
		log.info("API Gateway is shutting down");
	}
}
