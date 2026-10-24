package com.ichaabane.discovery;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;
import org.springframework.context.event.EventListener;

@SpringBootApplication
@EnableEurekaServer
public class DiscoveryApplication {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(DiscoveryApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("Eureka Discovery Server started successfully and is ready for registrations");
    }

    @PreDestroy
    public void onShutdown() {
        log.info("Eureka Discovery Server is shutting down");
    }
}
