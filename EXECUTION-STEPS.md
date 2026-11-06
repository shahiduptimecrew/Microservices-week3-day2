# Execution Steps — run the project and test it step by step

This guide takes you from "nothing running" to the full system, one request at a time.
Every step gives the **method**, the **URL**, the **headers**, the **body** to send,
the **response you should see**, and **what happened behind the scenes**.

The concepts are explained in [README.md](README.md). This file is only about *doing*.

---

## Part 0 — How the pieces fit together (read this first)

### You never pay "for an order" yourself

The README lists the Payment API before the Order API because it describes each service on its own.
In real use, the order of events is different:

```text
YOU (Postman)                 ORDER SERVICE                 PRODUCT SERVICE        PAYMENT SERVICE
     │                              │                              │                      │
     │ POST /api/v1/orders ───────▶ │                              │                      │
     │ {productId:2, quantity:3}    │ 1. "Is product 2 in stock?" ▶│                      │
     │                              │ ◀──── yes, price 24.99 ──────│                      │
     │                              │ 2. saves order #1 (74.97 EUR)                       │
     │                              │ 3. "Charge 74.97 for order #1" ────────────────────▶│
     │                              │ ◀─────────────────────── payment #1 SUCCESS ────────│
     │                              │ 4. marks order #1 CONFIRMED                         │
     │ ◀── 201 order #1 CONFIRMED,  │                                                     │
     │     paymentId: 1             │                                                     │
```

* **Creating an order automatically creates its payment.** Order Service calls Payment Service
  itself. You do not call the payment endpoint to pay for an order.
* The order response tells you which payment belongs to it: **`paymentId`** and **`paymentOperationId`**.
* The payment record points back to the order: its **`orderId`** is the order's id, and its
  **`operationId`** is the order's `paymentOperationId`.
* `POST /api/v1/payments`, which you can call directly, exists so you can **test Payment Service on its
  own** (Part 7). A payment you create that way is not linked to a real order.

### Where requests go

| You call | Port | Used for |
|---|---|---|
| **API Gateway** | **8080** | **All business requests** (products, orders, payments) |
| Eureka | 8761 | Dashboard: which services are running |
| Order Service directly | 8082 | Only to watch the circuit breaker (`/actuator/...`) |

### Demo data that exists after start-up

Product Service creates these five products every time it starts:

| id | sku | name | price | stock |
|---|---|---|---|---|
| 1 | `LAPTOP-15` | Laptop 15" | 999.00 | 10 |
| 2 | `MOUSE-WL` | Wireless mouse | 24.99 | 150 |
| 3 | `KEYB-MECH` | Mechanical keyboard | 89.50 | 40 |
| 4 | `MONITOR-27` | 27" monitor | 329.00 | 5 |
| 5 | `HEADSET-BT` | Bluetooth headset | 149.00 | **0** (out of stock) |

All data is in memory: **restarting a service deletes its data** and the ids start again at 1.

---

## Part 1 — Start the project

### 1.1 Build once (first time, or after code changes)

Open PowerShell in the project root `D:\java code\Micro Services`. Stop any running services first,
because `clean` deletes the folders they run from:

```powershell
.\mvnw.cmd clean verify
```

Expected at the end: `BUILD SUCCESS` (75 tests run).

### 1.2 Start the services: one terminal each, in this order

For this walkthrough, start **only one Product Service instance** for now. You add the second one in
Part 6. With two instances, each has its own in-memory database, which makes the product
create/update/delete steps confusing.

| Order | Terminal | Commands | Wait until you see |
|---|---|---|---|
| 1 | Eureka | `cd eureka-server` then `..\mvnw.cmd spring-boot:run` | `Started EurekaServerApplication` |
| 2 | Product | `cd product-service` then `..\mvnw.cmd spring-boot:run` | `Seeded 5 demo products` and `Started ProductServiceApplication` |
| 3 | Payment | `cd payment-service` then `..\mvnw.cmd spring-boot:run` | `Started PaymentServiceApplication` |
| 4 | Order | `cd order-service` then `..\mvnw.cmd spring-boot:run` | `Started OrderServiceApplication` |
| 5 | Gateway | `cd api-gateway` then `..\mvnw.cmd spring-boot:run` | `Started ApiGatewayApplication` |

Run each `cd` from the project root, in its own terminal.

### 1.3 Check that everything is up

Open <http://localhost:8761> in a browser. Under *Instances currently registered with Eureka*, wait
(10–30 s) until you see:

```text
API-GATEWAY      UP (1)
ORDER-SERVICE    UP (1)
PAYMENT-SERVICE  UP (1)
PRODUCT-SERVICE  UP (1)
```

If the Gateway answers **503** in the next steps, a service is not registered yet. Wait a few seconds
and try again.

---

## Part 2 — Postman setup

1. Create a new collection, for example **Microservices manual test**.
2. For every request with a body:
   * open the **Body** tab, choose **raw**, then **JSON**;
   * Postman then adds the header `Content-Type: application/json` automatically.
3. Order requests also need the header **`Idempotency-Key`** (the **Headers** tab). Its value is any
   unique text. The steps below use simple values like `order-key-001`; you can also type
   `{{$guid}}`, which makes Postman generate a new random value on every send.

> Shortcut: the ready-made collection in `postman/` contains all of these requests with automatic
> checks (README section 11). This guide is the manual version, so you see each step.

---

## Part 3 — Products (Product Service, through the Gateway)

### Step 3.1 — List all products

| | |
|---|---|
| Method | `GET` |
| URL | `http://localhost:8080/api/v1/products` |
| Body | none |

Expected: **200 OK**

```json
{
  "content": [
    { "id": 1, "sku": "LAPTOP-15", "name": "Laptop 15\"", "price": 999.00, "stockQuantity": 10, "...": "..." },
    { "id": 2, "sku": "MOUSE-WL",  "name": "Wireless mouse", "price": 24.99, "stockQuantity": 150, "...": "..." }
  ],
  "page": 0, "size": 20, "totalElements": 5, "totalPages": 1, "first": true, "last": true
}
```

Behind the scenes: the Gateway asked Eureka where `PRODUCT-SERVICE` runs and forwarded the request to port 8081.

### Step 3.2 — Paging and sorting

| | |
|---|---|
| Method | `GET` |
| URL | `http://localhost:8080/api/v1/products?page=0&size=2&sort=price,desc` |

Expected: **200 OK**, the 2 most expensive products (laptop 999.00, monitor 329.00), `"totalPages": 3`.

### Step 3.3 — Get one product

| | |
|---|---|
| Method | `GET` |
| URL | `http://localhost:8080/api/v1/products/2` |

Expected: **200 OK**, the wireless mouse, price `24.99`, stock `150`.
**Remember this product: you order it in Part 4.**

### Step 3.4 — Create a product

| | |
|---|---|
| Method | `POST` |
| URL | `http://localhost:8080/api/v1/products` |
| Body | see below |

```json
{
  "sku": "TABLET-10",
  "name": "Tablet 10 inch",
  "description": "10-inch tablet, 128 GB",
  "price": 299.00,
  "stockQuantity": 25
}
```

Expected: **201 Created**, a new product with `"id": 6`. Open the response **Headers** tab:
`Location: http://localhost:8080/api/v1/products/6`.

### Step 3.5 — Create the same product again (duplicate)

Send exactly the same request as 3.4 again.

Expected: **409 Conflict**

```json
{
  "type": "https://example.com/problems/duplicate-sku",
  "title": "Duplicate SKU",
  "status": 409,
  "detail": "A product with SKU 'TABLET-10' already exists",
  "instance": "/api/v1/products",
  "sku": "TABLET-10"
}
```

### Step 3.6 — Invalid product (validation)

| | |
|---|---|
| Method | `POST` |
| URL | `http://localhost:8080/api/v1/products` |

```json
{
  "sku": "bad sku",
  "name": "",
  "price": -1
}
```

Expected: **400 Bad Request**, with an `errors` list of 4 entries: `sku` (pattern), `name` (blank),
`price` (minimum 0.01) and `stockQuantity` (missing).

### Step 3.7 — Replace the product (PUT: send every field)

| | |
|---|---|
| Method | `PUT` |
| URL | `http://localhost:8080/api/v1/products/6` |

```json
{
  "sku": "TABLET-10",
  "name": "Tablet Pro",
  "description": "10-inch tablet, 256 GB",
  "price": 349.00,
  "stockQuantity": 20
}
```

Expected: **200 OK**, every field changed.

### Step 3.8 — Change only the price (PATCH)

| | |
|---|---|
| Method | `PATCH` |
| URL | `http://localhost:8080/api/v1/products/6` |

```json
{
  "price": 249.00
}
```

Expected: **200 OK**, `price` is `249.00`; the name stays `Tablet Pro`.

### Step 3.9 — Check stock (this is what Order Service calls internally)

| | |
|---|---|
| Method | `GET` |
| URL | `http://localhost:8080/api/v1/products/1/availability?quantity=11` |

Expected: **200 OK**

```json
{ "productId": 1, "requestedQuantity": 11, "availableQuantity": 10, "available": false, "unitPrice": 999.00 }
```

Change `quantity=11` to `quantity=5`: `"available": true`. With `quantity=0`: **400**.

### Step 3.10 — Version 2 of the same product

| | |
|---|---|
| Method | `GET` |
| URL | `http://localhost:8080/api/v2/products/1` |

Expected: **200 OK**, a different shape:

```json
{
  "id": 1, "sku": "LAPTOP-15", "name": "Laptop 15\"", "description": "15-inch laptop, 16 GB RAM",
  "price": { "amount": 999.00, "currency": "EUR" },
  "stock": { "quantity": 10, "inStock": true }
}
```

### Step 3.11 — Delete the product, then read it

| | |
|---|---|
| Method | `DELETE` |
| URL | `http://localhost:8080/api/v1/products/6` |

Expected: **204 No Content** (empty body).

Then `GET http://localhost:8080/api/v1/products/6` → **404 Not Found**, title `Product not found`.

---

## Part 4 — Place an order and find its payment (the main flow)

Make sure the payment failure mode is normal. Send this once:

| | |
|---|---|
| Method | `POST` |
| URL | `http://localhost:8080/api/v1/payments/test/failure-mode` |

```json
{ "mode": "NONE" }
```

### Step 4.1 — Create an order

| | |
|---|---|
| Method | `POST` |
| URL | `http://localhost:8080/api/v1/orders` |
| Headers | `Idempotency-Key: order-key-001` |

```json
{
  "productId": 2,
  "quantity": 3
}
```

Expected: **201 Created**. Response headers: `Location: http://localhost:8080/api/v1/orders/1` and
`Idempotent-Replayed: false`. Body:

```json
{
  "id": 1,
  "productId": 2,
  "quantity": 3,
  "unitPrice": 24.99,
  "totalAmount": 74.97,
  "currency": "EUR",
  "status": "CONFIRMED",
  "paymentStatus": "SUCCESS",
  "paymentId": 1,
  "paymentOperationId": "order-0b9e0a3c-6f8a-4a51-9a47-1d3c2f2a8f10",
  "failureReason": null,
  "createdAt": "...",
  "updatedAt": "...",
  "_links": {
    "self":    { "href": "http://localhost:8080/api/v1/orders/1" },
    "orders":  { "href": "http://localhost:8080/api/v1/orders" },
    "product": { "href": "http://localhost:8080/api/v1/products/2" },
    "payment": { "href": "http://localhost:8080/api/v1/payments/1" }
  }
}
```

What happened, in order:
1. Order Service checked the key `order-key-001`: new, so it continued.
2. It asked Product Service: *is product 2 available, 3 units?* Yes, at 24.99 each.
3. It saved order 1 with total 3 × 24.99 = **74.97** and gave it a payment operation id (`order-<uuid>`).
4. **It called Payment Service itself** to charge 74.97 with that operation id. Payment Service created
   **payment 1** with status `SUCCESS`.
5. It marked the order `CONFIRMED` and stored `paymentId: 1`.

**Write down** the `id` (order id), `paymentId` and `paymentOperationId`: you need them in the next steps.

### Step 4.2 — Look at the payment of this order

Use the `payment` link from the response (click it in Postman, or type it):

| | |
|---|---|
| Method | `GET` |
| URL | `http://localhost:8080/api/v1/payments/1` |

Expected: **200 OK**

```json
{
  "id": 1,
  "operationId": "order-0b9e0a3c-6f8a-4a51-9a47-1d3c2f2a8f10",
  "orderId": 1,
  "amount": 74.97,
  "currency": "EUR",
  "status": "SUCCESS",
  "failureReason": null,
  "createdAt": "..."
}
```

Compare with the order: `orderId` = order `id`, `operationId` = order `paymentOperationId`,
`amount` = order `totalAmount`. **This is how you see which payment belongs to which order.**

You can also search the payments of an order:

| | |
|---|---|
| Method | `GET` |
| URL | `http://localhost:8080/api/v1/payments?orderId=1` |

Expected: **200 OK**, a list with exactly one payment.

### Step 4.3 — Read the order again

| | |
|---|---|
| Method | `GET` |
| URL | `http://localhost:8080/api/v1/orders/1` |

Expected: **200 OK**, the same order as in 4.1.

### Step 4.4 — List all orders

| | |
|---|---|
| Method | `GET` |
| URL | `http://localhost:8080/api/v1/orders` |

Expected: **200 OK**, `_embedded.orders` (the list), `page` (totals) and `_links` (navigation).

---

## Part 5 — Idempotency: what happens if the client sends the order twice

Imagine the network dropped the response of step 4.1 and Postman (the "client") retries.

### Step 5.1 — Same key, same body again

Send **exactly** the request from step 4.1 again (same `Idempotency-Key: order-key-001`, same body).

Expected: **201 Created** with **the same order `"id": 1`** and the response header
**`Idempotent-Replayed: true`**.

Check: `GET http://localhost:8080/api/v1/payments?orderId=1` still returns **one** payment.
No second order and no second charge.

### Step 5.2 — Same key, different body

| | |
|---|---|
| Method | `POST` |
| URL | `http://localhost:8080/api/v1/orders` |
| Headers | `Idempotency-Key: order-key-001` |

```json
{ "productId": 2, "quantity": 4 }
```

Expected: **409 Conflict**, title `Idempotency-Key reused`.

### Step 5.3 — No key at all

Same URL and body, but **remove the `Idempotency-Key` header**.

Expected: **400 Bad Request**, `Required header 'Idempotency-Key' is not present.`

### Step 5.4 — Product out of stock

| | |
|---|---|
| Method | `POST` |
| URL | `http://localhost:8080/api/v1/orders` |
| Headers | `Idempotency-Key: order-key-002` |

```json
{ "productId": 5, "quantity": 1 }
```

Expected: **409 Conflict**, title `Insufficient stock`, with `"requested": 1, "available": 0`.
No order and no payment are created, and Payment Service is never called.

### Step 5.5 — Product does not exist

Headers `Idempotency-Key: order-key-003`, body `{ "productId": 999, "quantity": 1 }`.

Expected: **404 Not Found**, title `Product not found`.

### Step 5.6 — Retry payment on a paid order

| | |
|---|---|
| Method | `POST` |
| URL | `http://localhost:8080/api/v1/orders/1/retry-payment` |
| Body | none |

Expected: **409 Conflict**, `Order 1 is CONFIRMED; only PENDING orders can retry payment`.

---

## Part 6 — Load balancing (two Product instances)

### Step 6.1 — Start the second Product instance

New terminal:

```powershell
cd product-service
..\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=8084"
```

Wait until the Eureka dashboard shows `PRODUCT-SERVICE UP (2)`, then about 10 s more.

### Step 6.2 — Call the same product several times

| | |
|---|---|
| Method | `GET` |
| URL | `http://localhost:8080/api/v1/products/1` |

Send it 4 times. Each time, open the response **Headers** tab and look at **`X-Served-By`**:
it alternates between `<your-ip>:8081` and `<your-ip>:8084`. The Gateway's console logs one line per
request showing the same instance.

> From now on, products you create may exist on only one of the two instances (each has its own
> in-memory database). Orders for products 1–5 work on both.

---

## Part 7 — Payment Service on its own (deduplication)

These calls go to Payment Service directly. The `orderId` here is just a number and no real order is
involved. The goal is to see that the same `operationId` is **never charged twice**.

### Step 7.1 — New payment

| | |
|---|---|
| Method | `POST` |
| URL | `http://localhost:8080/api/v1/payments` |

```json
{
  "operationId": "manual-test-1",
  "orderId": 9999,
  "amount": 10.00
}
```

Expected: **201 Created**, a payment with status `SUCCESS` and currency `EUR` (the default). Note its `id`.

### Step 7.2 — Exactly the same request again

Expected: **200 OK** (not 201) and **the same `id`**: the stored payment is returned and no new charge is made.

### Step 7.3 — Same operationId, different amount

```json
{
  "operationId": "manual-test-1",
  "orderId": 9999,
  "amount": 99.00
}
```

Expected: **409 Conflict**, title `operationId conflict`.

### Step 7.4 — Find a payment by operationId

| | |
|---|---|
| Method | `GET` |
| URL | `http://localhost:8080/api/v1/payments?operationId=manual-test-1` |

Expected: **200 OK**, a list with one payment.

---

## Part 8 — What happens when payment goes wrong

Payment Service has a demo switch that simulates a payment provider with problems:

| | |
|---|---|
| Method | `POST` |
| URL | `http://localhost:8080/api/v1/payments/test/failure-mode` |
| Body | `{ "mode": "NONE" }` or `"DECLINE"` or `"ERROR"` or `{ "mode": "SLOW", "delayMs": 3000 }` |

To see the current mode: `GET http://localhost:8080/api/v1/payments/test/failure-mode`.

Every order in this part uses a **new** `Idempotency-Key` (or `{{$guid}}`) and this body:

```json
{ "productId": 2, "quantity": 1 }
```

### 8A — Card declined → order FAILED

1. Failure mode: `{ "mode": "DECLINE" }`
2. `POST http://localhost:8080/api/v1/orders` with header `Idempotency-Key: decline-001`

Expected: **201 Created**, but

```json
"status": "FAILED", "paymentStatus": "FAILED", "failureReason": "Card declined by provider", "paymentId": <a number>
```

`GET http://localhost:8080/api/v1/payments/<that paymentId>` shows `"status": "FAILED"`.
Payment Service answered normally; it just said "no". This is a business result, not an outage.

3. Failure mode back to `{ "mode": "NONE" }`

### 8B — Payment provider down → order PENDING → circuit breaker OPEN

Keep this page open in a browser tab and refresh it after each request:
**<http://localhost:8082/actuator/circuitbreakers>**. It shows `"state": "CLOSED"` now.

1. Failure mode: `{ "mode": "ERROR" }`
2. Create an order with header `Idempotency-Key: error-001`:

   Expected: **201 Created**, `"status": "PENDING"`, `"paymentStatus": "PENDING"`,
   `"failureReason": "Payment Service returned 503 SERVICE_UNAVAILABLE"`, `"paymentId": null`, and in
   `_links` a **`retry-payment`** link instead of a `payment` link.
   **Write down this order id.**

   *PENDING means "we don't know yet whether the customer paid". The order is never shown as paid.*

3. Repeat with keys `error-002`, `error-003`, `error-004`, refreshing the circuit breaker page each time.
   The breaker looks at the **last 4 payment calls**. As soon as half of them failed, the state becomes
   **`OPEN`** (with earlier successful orders, that can happen after only 2 failures).
4. Create one more order (`error-005`) while it is OPEN:

   Expected: returns almost instantly, `"failureReason": "Payment circuit breaker is OPEN: payment was not attempted"`.
   Order Service did not even call Payment Service; it protects itself and gives Payment Service time to recover.

5. Failure mode: `{ "mode": "NONE" }` (the provider has recovered)
6. **Wait 15 seconds.** Refresh the circuit breaker page: `"state": "HALF_OPEN"`.
7. Retry the payment of the first PENDING order (the id from step 2):

   | | |
   |---|---|
   | Method | `POST` |
   | URL | `http://localhost:8080/api/v1/orders/<pending order id>/retry-payment` |
   | Body | none |

   Expected: **200 OK**, `"status": "CONFIRMED"`, `"paymentStatus": "SUCCESS"`, now with a `paymentId`.

8. Retry a second PENDING order the same way. After 2 successful trial calls the circuit breaker page
   shows **`"state": "CLOSED"`** again. Retry the remaining PENDING orders too.

To see the whole history (every call, error and state change):
`GET http://localhost:8082/actuator/circuitbreakerevents`. The Order Service console also logs
`State transition from CLOSED to OPEN`, and so on.

### 8C — Payment provider slow → timeout → safe retry, charged only once

This is the most important scenario: the provider **did** take the money, but too late.

1. Failure mode: `{ "mode": "SLOW", "delayMs": 3000 }` (each payment takes 3 s)
2. Create an order with header `Idempotency-Key: slow-001`:

   Expected after about **2 seconds**: **201 Created**, `"status": "PENDING"`,
   `"failureReason": "Payment Service did not answer in time"`. Order Service waits at most 2 s.
   **Write down `id` and `paymentOperationId`.**

3. Wait 2 seconds, then look at Payment Service:

   | | |
   |---|---|
   | Method | `GET` |
   | URL | `http://localhost:8080/api/v1/payments?operationId=<paymentOperationId from step 2>` |

   Expected: **one** payment with `"status": "SUCCESS"`. The provider finished after Order Service had
   stopped waiting, so the customer **was** charged, but the order still says PENDING.

4. Failure mode: `{ "mode": "NONE" }`
5. `POST http://localhost:8080/api/v1/orders/<order id>/retry-payment`

   Expected: **200 OK**, `"status": "CONFIRMED"`.

6. Repeat step 3: still **exactly one** payment.

   The retry sent the same `operationId`, so Payment Service recognised it, returned the existing payment
   and did **not** charge again.

7. Failure mode back to `{ "mode": "NONE" }` (if not already).

---

## Part 9 — Gateway errors

### Step 9.1 — A path no service handles

| | |
|---|---|
| Method | `GET` |
| URL | `http://localhost:8080/api/v1/nothing-here` |

Expected: **404 Not Found** as `application/problem+json`, detail `No route for /api/v1/nothing-here`.
This answer comes from the Gateway itself, not from a service.

### Step 9.2 — A service is down

Stop **Payment Service** (Ctrl+C in its terminal), wait about 20 s, then:

| | |
|---|---|
| Method | `GET` |
| URL | `http://localhost:8080/api/v1/payments/1` |

Expected: **503 Service Unavailable** from the Gateway. Now create an order (new key): it still returns
**201** with `"status": "PENDING"`, because Order Service handles the missing Payment Service safely.
Start Payment Service again afterwards (its data is gone after the restart).

### Step 9.3 — Gateway route table

| | |
|---|---|
| Method | `GET` |
| URL | `http://localhost:8080/actuator/gateway/routes` |

Expected: **200 OK**, three routes: `product-service`, `order-service`, `payment-service`, each with a
`lb://...` URI.

---

## Part 10 — Look inside the databases (optional)

Each service has a browser console for its own database:

| Service | URL | JDBC URL | Try |
|---|---|---|---|
| Product | <http://localhost:8081/h2-console> | `jdbc:h2:mem:productdb` | `SELECT * FROM products` |
| Order | <http://localhost:8082/h2-console> | `jdbc:h2:mem:orderdb` | `SELECT * FROM orders` and `SELECT * FROM idempotency_records` |
| Payment | <http://localhost:8083/h2-console> | `jdbc:h2:mem:paymentdb` | `SELECT * FROM payments` |

User `sa`, empty password. Compare `orders.payment_operation_id` with `payments.operation_id`: it is the
same value for each order and its payment.

---

## Quick reference — every endpoint

All through `http://localhost:8080`. Send bodies as raw JSON.

| # | Method | URL | Headers | Body | Success |
|---|---|---|---|---|---|
| 1 | GET | `/api/v1/products?page=0&size=20&sort=id,asc` | – | – | 200 |
| 2 | GET | `/api/v1/products/{id}` | – | – | 200 |
| 3 | POST | `/api/v1/products` | – | `{"sku":"TABLET-10","name":"Tablet 10 inch","description":"10-inch tablet","price":299.00,"stockQuantity":25}` | 201 |
| 4 | PUT | `/api/v1/products/{id}` | – | `{"sku":"TABLET-10","name":"Tablet Pro","description":"256 GB","price":349.00,"stockQuantity":20}` | 200 |
| 5 | PATCH | `/api/v1/products/{id}` | – | `{"price":249.00}` | 200 |
| 6 | DELETE | `/api/v1/products/{id}` | – | – | 204 |
| 7 | GET | `/api/v1/products/{id}/availability?quantity=5` | – | – | 200 |
| 8 | GET | `/api/v2/products/{id}` | – | – | 200 |
| 9 | POST | `/api/v1/orders` | `Idempotency-Key: <unique>` | `{"productId":2,"quantity":3}` | 201 |
| 10 | GET | `/api/v1/orders?page=0&size=20` | – | – | 200 |
| 11 | GET | `/api/v1/orders/{id}` | – | – | 200 |
| 12 | POST | `/api/v1/orders/{id}/retry-payment` | – | – | 200 (only for PENDING orders) |
| 13 | POST | `/api/v1/payments` | – | `{"operationId":"manual-test-1","orderId":9999,"amount":10.00}` | 201 new / 200 repeat |
| 14 | GET | `/api/v1/payments/{id}` | – | – | 200 |
| 15 | GET | `/api/v1/payments?orderId={orderId}` | – | – | 200 |
| 16 | GET | `/api/v1/payments?operationId={operationId}` | – | – | 200 |
| 17 | GET | `/api/v1/payments/test/failure-mode` | – | – | 200 |
| 18 | POST | `/api/v1/payments/test/failure-mode` | – | `{"mode":"NONE"}` / `"DECLINE"` / `"ERROR"` / `{"mode":"SLOW","delayMs":3000}` | 200 |

Not through the Gateway:

| Method | URL | Shows |
|---|---|---|
| GET | `http://localhost:8761` (browser) | Eureka dashboard |
| GET | `http://localhost:8082/actuator/circuitbreakers` | Circuit breaker state |
| GET | `http://localhost:8082/actuator/circuitbreakerevents` | Circuit breaker history |
| GET | `http://localhost:8080/actuator/gateway/routes` | Gateway routes |
| GET | `http://localhost:808x/swagger-ui/index.html` | Swagger UI per service (8081, 8082, 8083) |

## Reset to a clean state

* Failure mode: `POST /api/v1/payments/test/failure-mode` with `{"mode":"NONE"}`.
* Circuit breaker: wait 15 s and create one successful order, or restart Order Service.
* All data: restart the services (Ctrl+C, then `..\mvnw.cmd spring-boot:run`). Order and payment ids
  start again at 1.
