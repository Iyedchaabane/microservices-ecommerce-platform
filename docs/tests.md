# Testing Strategy

##Overview

The platform follows a pragmatic testing approach:

1. **Unit tests** for service-layer logic and mappers.
2. **Integration tests** with Spring Boot Test (full application context).
3. **Context smoke tests** on every module.

Every service contains a `*ApplicationTests` class that asserts the Spring application context loads successfully.

---

## Running Tests

### Prerequisites

- Config-server must be running (or tests may fail if they eagerly load Eureka clients). For pure unit tests, this is not required.
- Active profile: `test` (default) or `dev`.

### Run tests for a single module

```bash
# From repo root
mvn test -pl config-server
mvn test -pl discovery
mvn test -pl gateway
mvn test -pl customer-service
mvn test -pl product-service
mvn test -pl order-service
mvn test -pl payment-service
mvn test -pl notification-service
```

### Run tests for all modules

```bash
mvn clean test
```

### Skip tests during package

```bash
mvn clean package -DskipTests
```

---

## Test Structure

### Context Smoke Test (all modules)

```java
@SpringBootTest
class OrderServiceApplicationTests {
    @Test
    void contextLoads() {}
}
```

These tests verify that:
- All beans are wired correctly.
- Configuration is valid.
- The datasource/Eureka configuration does not break startup.

### Unit Test Example (service layer)

```java
@ExtendWith(MockitoExtension.class)
class CustomerServiceImplTest {

    @Mock CustomerRepository repository;
    @InjectMocks CustomerServiceImpl service;

    @Test
    void shouldCreateCustomer() {
        CustomerRequest req = new CustomerRequest("John", "Doe", "john@test.com", null);
        Customer saved = Customer.builder().id("123").firstname("John").lastname("Doe").email("john@test.com").build();
        when(repository.save(any())).thenReturn(saved);

        String id = service.createCustomer(req);

        assertEquals("123", id);
        verify(repository, times(1)).save(any());
    }
}
```

---

## Integration Testing Notes

* `product-service` uses **Flyway**; integration tests that hit the DB will execute migrations.
* `order-service` and `payment-service` use `ddl-auto=create`; the schema is created automatically in H2 or PostgreSQL depending on the test profile.
* Kafka-dependent tests should use **EmbeddedKafka** (`@EmbeddedKafka`) or `spring-kafka-test`.

Example embedded Kafka test:

```java
@EmbeddedKafka(topics = {"order-topic", "payment-topic"}, partitions = 1)
@SpringBootTest
class OrderKafkaIntegrationTest {
    // ...
}
```

---

## Test Containers (recommended next step)

For true production-like testing, integrate **Testcontainers**:

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>kafka</artifactId>
    <scope>test</scope>
</dependency>
```

This allows running real PostgreSQL and Kafka instances inside Docker during tests.
