# Testing Strategy

## Overview

The platform follows a pragmatic testing approach:

1. **Unit tests** for service-layer logic and mappers.
2. **Integration tests** with Spring Boot Test (full application context).
3. **Context smoke tests** on every module.

Every service contains a `*ApplicationTests` class that asserts the Spring application context loads successfully.

---

## Running Tests

### Prerequisites

- There is **no Maven aggregator POM**: run the Maven wrapper from inside each
  module directory (`./$module/mvnw -f $module/pom.xml`).
- The `*ApplicationTests` context smoke tests boot the full Spring context and
  therefore need `config-server` (and usually the infrastructure from
  `docker-compose.yml`). The unit tests listed below need none of that.
- No `test` Spring profile is configured; tests run with each module's default
  configuration.

### Run the unit tests of a module (no infrastructure required)

```bash
(cd product && ./mvnw -o test -Dtest='ProductServiceTest,ProductMapperTest')
(cd order   && ./mvnw -o test -Dtest='OrderServiceTest,OrderMapperTest,OrderLineMapperTest')
```

### Run every test of one module

```bash
# From the repository root
./config-server/mvnw -f config-server/pom.xml test
./customer/mvnw      -f customer/pom.xml      test
# ... repeat for discovery, gateway, product, order, payment, notification
```

### Run every module's tests

```bash
for d in config-server discovery gateway customer product order payment notification; do
  ./$d/mvnw -f $d/pom.xml test || exit 1
done
```

### Skip tests during package

```bash
./customer/mvnw -f customer/pom.xml clean package -DskipTests
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
class CustomerServiceTest {

    @Mock CustomerRepository repository;
    @Spy  CustomerMapper mapper = new CustomerMapper();
    @InjectMocks CustomerService service;

    @Test
    void shouldCreateCustomer() {
        // CustomerCreateRequest(firstname, lastname, email, address)
        var request = new CustomerCreateRequest("John", "Doe", "john@test.com", null);
        var saved = Customer.builder().id("123").firstname("John").lastname("Doe").email("john@test.com").build();
        when(repository.save(any())).thenReturn(saved);

        String id = service.createCustomer(request);

        assertEquals("123", id);
        verify(repository, times(1)).save(any());
    }
}
```

---

## Integration Testing Notes

* `product-service` uses **Flyway**; integration tests that hit the DB will execute migrations.
* `order-service` and `payment-service` use `ddl-auto=update`; the schema is created automatically in H2 or PostgreSQL depending on the test profile.
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
