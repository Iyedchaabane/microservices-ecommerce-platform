package com.ichaabane.ecommerce;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@Slf4j
public class NotificationApplication {

	public static void main(String[] args) {
		SpringApplication.run(NotificationApplication.class, args);
	}

	@EventListener(ApplicationReadyEvent.class)
	public void onStartup() {
		log.info("Notification service started successfully and is listening for events");
	}

	@PreDestroy
	public void onShutdown() {
		log.info("Notification service is shutting down");
	}
}
