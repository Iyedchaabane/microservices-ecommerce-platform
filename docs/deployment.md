# Deployment Guide

## Prerequisites

| Tool | Version | Purpose |
|---|---|---|
| Docker Engine | 20.x+ | Container runtime |
| Docker Compose | 2.x+ | Multi-container orchestration |
| Java JDK | 17+ | Compile & run JARs locally (optional if using Docker) |
| Maven | 3.8+ | Build tool (optional if using Docker) |

---

## Infrastructure via Docker Compose

The root `docker-compose.yml` provisions all backing services:

* PostgreSQL (port 5432) – user/pass/db: `postgres`/`password`/`postgres`
* pgAdmin (port 5050) – admin@admin.com / admin
* MongoDB (port 27017) – user/pass: `mongo`/`mongo`
* Mongo Express (port 8081) – admin / pass
* Zookeeper (port 2181)
* Kafka (port 9092) + Kafka UI (port 8080)
* MailDev (SMTP port 1025, Web UI port 1080)
* Zipkin (port 9411)

### Start infrastructure

```bash
cd e-commerce-app
docker compose up -d
```

### Stop infrastructure

```bash
docker compose down
# Preserve volumes
docker compose down -v  # DESTRUCTIVE: removes all data
```

### Verify containers are running

```bash
docker compose ps
```

---

## Build the Microservices

```bash
cd e-commerce-app
mvn clean package -DskipTests
```

This produces `*.jar` files under each service’s `target/` directory:

```
config-server/target/config-server-1.0.0-SNAPSHOT.jar
discovery-service/target/discovery-service-1.0.0-SNAPSHOT.jar
gateway-service/target/gateway-service-1.0.0-SNAPSHOT.jar
customer-service/target/customer-service-1.0.0-SNAPSHOT.jar
product-service/target/product-service-1.0.0-SNAPSHOT.jar
order-service/target/order-service-1.0.0-SNAPSHOT.jar
payment-service/target/payment-service-1.0.0-SNAPSHOT.jar
notification-service/target/notification-service-1.0.0-SNAPSHOT.jar
```

---

## Run Locally (JARs)

Start services **in this order** to satisfy dependency chains:

```bash
# 1. Config server
java -jar config-server/target/config-server-*.jar &

# 2. Discovery (Eureka)
java -jar discovery-service/target/discovery-service-*.jar &

# Wait ~30s for Eureka to be fully up, then:

# 3. Gateway
java -jar gateway-service/target/gateway-service-*.jar &

# 4. Business services (any order after gateway)
java -jar customer-service/target/customer-service-*.jar &
java -jar product-service/target/product-service-*.jar &
java -jar order-service/target/order-service-*.jar &
java -jar payment-service/target/payment-service-*.jar &
java -jar notification-service/target/notification-service-*.jar &
```

Verify registration at Eureka: [http://localhost:8761](http://localhost:8761)

---

## Run with Docker (optional)

Example `Dockerfile` for a Spring Boot service (multi-stage build):

```dockerfile
# ---------- BUILD ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# ---------- RUN ----------
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8888
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Build & run:

```bash
docker build -t ecom/config-server ./config-server
docker run -p 8888:8888 --network host ecom/config-server
```

> **Note:** all services currently point to `localhost` for DB/Kafka/Eureka/Config. When containerizing the Java apps, either use `--network host` (Linux) or replace `localhost` with service names in the config-server YML files and use a shared Docker network.

---

## Production Considerations

1. **Externalize secrets**: Move DB credentials, SMTP credentials, and Kafka endpoints out of the config-server into environment variables or a secrets manager (Vault / AWS Secrets Manager).
2. **Disable `ddl-auto=create`**: In `order-service` and `payment-service`, change `spring.jpa.hibernate.ddl-auto` to `validate` and adopt Flyway.
3. **Resource limits**: Add JVM memory limits (`-Xmx`, `-Xms`) and container resource limits.
4. **Health checks**: Expose only `/actuator/health` externally; secure other actuator endpoints.
5. **Kafka durability**: Set `min.insync.replicas=2` and `acks=all` on producers in production.
6. **TLS/HTTPS**: Terminate TLS at the gateway or load balancer.
7. **Centralized logging**: Ship logs to ELK / Loki / CloudWatch.

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| Services fail to start with *Config Server connection refused* | Start `config-server` first; it is `optional:` so ignoring is also possible |
| Eureka dashboard empty | Wait 30–60s; ensure `discovery-service` is running before gateway/clients |
| Kafka consumer not receiving messages | Check `KAFKA_ADVERTISED_LISTENERS`; use `localhost:9092` for host networking |
| MailDev not receiving email | Verify SMTP host/port in notification-service config (`localhost:1025`) |
