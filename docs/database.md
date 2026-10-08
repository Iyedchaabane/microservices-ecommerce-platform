# Database Guide

The platform uses the **Database-per-Service** pattern:

| Service | Database | Technology |
|---|---|---|
| `product-service` | `product` | PostgreSQL |
| `payment-service` | `payment` | PostgreSQL |
| `order-service` | `order` | PostgreSQL |
| `customer-service` | `customer` | MongoDB |
| `notification-service` | `notification` | MongoDB |

---

## PostgreSQL Schemas

### product-service

*Managed by Flyway. Hibernate `ddl-auto: validate`.*

#### `category`

| Column | Type | Constraints |
|---|---|---|
| `id` | SERIAL | PK |
| `name` | VARCHAR | NOT NULL |
| `description` | VARCHAR | |

#### `product`

| Column | Type | Constraints |
|---|---|---|
| `id` | SERIAL | PK |
| `name` | VARCHAR | NOT NULL |
| `description` | VARCHAR | |
| `available_quantity` | DOUBLE | NOT NULL |
| `price` | DECIMAL(38,2) | NOT NULL |
| `category_id` | INTEGER | FK → `category(id)` |

### order-service

*Managed by Hibernate `ddl-auto: update` (schema kept in sync at startup; `validate` backed by Flyway is the target state).*

#### `customer_order`

| Column | Type | Constraints |
|---|---|---|
| `id` | SERIAL | PK |
| `reference` | VARCHAR | UNIQUE |
| `total_amount` | DECIMAL(38,2) | |
| `payment_method` | INTEGER | |
| `customer_id` | VARCHAR | |

#### `order_line`

| Column | Type | Constraints |
|---|---|---|
| `id` | SERIAL | PK |
| `order_id` | INTEGER | FK → `customer_order(id)` |
| `product_id` | INTEGER | |
| `quantity` | DOUBLE | |

### payment-service

*Managed by Hibernate `ddl-auto: update` (schema kept in sync at startup; `validate` backed by Flyway is the target state).*

#### `payment`

| Column | Type | Constraints |
|---|---|---|
| `id` | SERIAL | PK |
| `amount` | DECIMAL(38,2) | |
| `payment_method` | INTEGER | |
| `order_id` | INTEGER | |

---

## MongoDB Collections

### customer-service → `customer`

The `email` field carries a **unique index** (declared with `@Indexed(unique = true)` on `Customer`); duplicates are rejected with `409 CONFLICT` before reaching the database.

```json
{
  "_id": "ObjectId",
  "firstname": "String",
  "lastname": "String",
  "email": "String (unique)",
  "address": {
    "street": "String",
    "houseNumber": "String",
    "zipCode": "String"
  }
}
```

### notification-service → `notification`

```json
{
  "_id": "ObjectId",
  "type": "ORDER_CONFIRMATION | PAYMENT_CONFIRMATION",
  "notificationDate": "ISODate",
  "orderConfirmation": { ... },
  "paymentConfirmation": { ... }
}
```

---

## Migrations

### Flyway (product-service)

Flyway is enabled **only** for `product-service`. Migration scripts live in:

```
product/src/main/resources/db/migration/
```

Execution order at startup:

1. `V1__init_database.sql` (creates the `category` and `product` tables)
2. `V2__insert_data.sql` (seed categories and products)

Flyway metadata table: `flyway_schema_history` in the `product` database.

### Adding a new migration

1. Create a new file following the pattern `V{next}__{Description}.sql`.
2. Restart `product-service`; Flyway will apply pending migrations automatically.
3. **Never edit an existing migration file.** Always create a new one.

### Hibernate DDL-Auto (order-service, payment-service)

`order-service` and `payment-service` use `spring.jpa.hibernate.ddl-auto=update`, which keeps the schema in sync without dropping existing rows. It is still not a substitute for versioned migrations: for production, switch to `validate` and manage the schema via Flyway (following the `product-service` pattern).
