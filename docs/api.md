# API Reference

Base URL through the Gateway:

```
http://localhost:8222
```

All business endpoints should be accessed via the gateway (`:8222`). Direct service access is possible for debugging (see ports below), but not recommended.

| Service | Direct Port | Base Path |
|---|---|---|
| customer-service | 8090 | `/api/v1/customers` |
| product-service | 8050 | `/api/v1/products` |
| order-service | 8070 | `/api/v1/orders` |
| payment-service | 8060 | `/api/v1/payments` |

---

## Customer Service

### `POST /api/v1/customers`

Create a new customer.

**Request Body**

```json
{
  "firstname": "John",
  "lastname": "Doe",
  "email": "john.doe@example.com",
  "address": {
    "street": "Main Street",
    "houseNumber": "10",
    "zipCode": "1000"
  }
}
```

**Response**: `200 OK` → `String` (customer ID)

```json
"65f1a2b3c4d5e6f789012345"
```

### `GET /api/v1/customers`

Retrieve all customers.

**Response**: `200 OK` → `List<CustomerResponse>`

```json
[
  {
    "id": "65f1a2b3c4d5e6f789012345",
    "firstname": "John",
    "lastname": "Doe",
    "email": "john.doe@example.com",
    "address": {
      "street": "Main Street",
      "houseNumber": "10",
      "zipCode": "1000"
    }
  }
]
```

### `GET /api/v1/customers/{id}`

Retrieve a single customer by ID.

**Response**: `200 OK` → `CustomerResponse`

### `PUT /api/v1/customers/{id}`

Update an existing customer (full replacement).

**Request Body**: same as `POST`.

**Response**: `200 OK` → `String` (customer ID)

### `DELETE /api/v1/customers/{id}`

Delete a customer.

**Response**: `204 No Content`

---

## Product Service

### `POST /api/v1/products`

Create a new product.

**Request Body**

```json
{
  "name": "Wireless Mouse",
  "description": "Ergonomic wireless mouse",
  "availableQuantity": 100,
  "price": 25.99,
  "categoryId": 1
}
```

**Response**: `200 OK` → `Integer` (product ID)

### `GET /api/v1/products`

Retrieve all products.

**Response**: `200 OK` → `List<ProductResponse>`

```json
[
  {
    "id": 1,
    "name": "Wireless Mouse",
    "description": "Ergonomic wireless mouse",
    "availableQuantity": 100,
    "price": 25.99,
    "category": {
      "id": 1,
      "name": "Electronics",
      "description": "Electronic devices"
    }
  }
]
```

### `GET /api/v1/products/{id}`

Retrieve a single product by ID.

**Response**: `200 OK` → `ProductResponse`

### `POST /api/v1/products/purchase`

Internal endpoint used by `order-service` to validate stock and decrement inventory.

*Not intended for public consumption.*

---

## Order Service

### `POST /api/v1/orders`

Create an order. Orchestrates customer validation, stock check, payment, and notification.

**Request Body**

```json
{
  "reference": "ORD-2026-001",
  "amount": 125.97,
  "paymentMethod": "CREDIT_CARD",
  "customerId": "65f1a2b3c4d5e6f789012345",
  "products": [
    {
      "productId": 1,
      "quantity": 3
    },
    {
      "productId": 2,
      "quantity": 2
    }
  ]
}
```

**Response**: `200 OK` → `Integer` (order ID)

**Side effects**:
- Calls `customer-service` to validate `customerId`.
- Calls `product-service` `/purchase` to reserve inventory.
- Persists order + order lines.
- Calls `payment-service` to create payment.
- Publishes `OrderConfirmation` to Kafka topic `order-topic`.

### `GET /api/v1/orders`

Retrieve all orders with their order lines.

**Response**: `200 OK` → `List<OrderResponse>`

```json
[
  {
    "id": 1,
    "reference": "ORD-2026-001",
    "totalAmount": 125.97,
    "paymentMethod": "CREDIT_CARD",
    "customerId": "65f1a2b3c4d5e6f789012345",
    "orderLines": [
      {
        "id": 1,
        "orderId": 1,
        "productId": 1,
        "quantity": 3
      }
    ]
  }
]
```

### `GET /api/v1/orders/{id}`

Retrieve a single order by ID.

**Response**: `200 OK` → `OrderResponse`

---

## Payment Service

### `POST /api/v1/payments`

Create a payment record. Called by `order-service` during checkout.

**Request Body**

```json
{
  "orderId": 1,
  "amount": 125.97,
  "paymentMethod": "CREDIT_CARD",
  "orderReference": "ORD-2026-001",
  "customer": {
    "id": "65f1a2b3c4d5e6f789012345",
    "firstname": "John",
    "lastname": "Doe",
    "email": "john.doe@example.com"
  }
}
```

**Response**: `200 OK` → `Integer` (payment ID)

**Side effects**:
- Persists payment record.
- Publishes `PaymentNotificationRequest` to Kafka topic `payment-topic` (consumed by `notification-service` to send an email).

---

## Gateway Routes

The gateway (`:8222`) forwards requests using service discovery:

| Path | Service |
|---|---|
| `/api/v1/customers/**` | `customer-service` |
| `/api/v1/products/**` | `product-service` |
| `/api/v1/orders/**` | `order-service` |
| `/api/v1/order-lines/**` | `order-service` |
| `/api/v1/payments/**` | `payment-service` |

---

## Error Handling

All services use `@ControllerAdvice` for consistent error payloads.

### Validation Error (400 Bad Request)

```json
{
  "errors": {
    "firstname": "Firstname is required",
    "email": "Email should be valid"
  }
}
```

### Business Exception (400 Bad Request)

```json
{
  "error": "Product with ID:: 99 not found"
}
```

### Resource Not Found (404 / 400 depending on service)

Services may return `404 Not Found` or `400 Bad Request` with a plain-text message depending on the controller advice implementation.
