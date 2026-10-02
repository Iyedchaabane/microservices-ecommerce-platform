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

*Managed by Hibernate `ddl-auto: create` (schema auto-generated).*

#### `customer_order`

| Column | Type | Constraints |
|---|---|---|
| `id` | SERIAL | PK |
| `reference` | VARCHAR | UNIQUE |
| `total_amount` | DECIMAL(38,2) | |
| `payment_method` | INTEGER | |
| `customer_id` | VARCHAR | |

#### `customer_line`

| Column | Type | Constraints |
|---|---|---|
| `id` | SERIAL | PK |
| `order_id` | INTEGER | FK → `customer_order(id)` |
| `product_id` | INTEGER | |
| `quantity` | DOUBLE | |

### payment-service

*Managed by Hibernate `ddl-auto: create` (schema auto-generated).*

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

```json
{
  "_id": "ObjectId",
  "firstname": "String",
  "lastname": "String",
  "email": "String",
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
product-service/src/main/resources/db/migration/
```

Execution order at startup:

1. `V1__Create_category.sql`
2. `V2__Create_product.sql`
3. `V3__Insert_categories.sql`
4. `V4__Insert_products.sql`

Flyway metadata table: `flyway_schema_history` in the `product` database.

### Adding a new migration

1. Create a new file following the pattern `V{next}__{Description}.sql`.
2. Restart `product-service`; Flyway will apply pending migrations automatically.
3. **Never edit an existing migration file.** Always create a new one.

### Hibernate DDL-Auto (order-service, payment-service)

`order-service` and `payment-service` use `spring.jpa.hibernate.ddl-auto=create`, which drops and recreates the schema on every startup. **This is intended for local development only.**

For production, switch to `validate` and manage schema via Flyway (following the `product-service` pattern).
