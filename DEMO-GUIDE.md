# Demo Guide — 90-minute session

A minute-by-minute script for the trainer. Concepts are explained in [README.md](README.md)
(section numbers in brackets); this guide says **what to show and when**.

## Before the session (15 minutes, the day before)

1. Build once so dependencies are cached: `.\mvnw.cmd clean verify` in the project root (all tests green).
2. Import the Postman collection and environment from `postman/`, select **E-Commerce local**.
3. Prepare six terminals (Windows Terminal tabs work well), one per application, each already
   `cd`-ed into its folder:

   | Tab | Folder | Command |
   |---|---|---|
   | 1 | `eureka-server` | `..\mvnw.cmd spring-boot:run` |
   | 2 | `product-service` | `..\mvnw.cmd spring-boot:run` |
   | 3 | `product-service` | `..\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=8084"` |
   | 4 | `payment-service` | `..\mvnw.cmd spring-boot:run` |
   | 5 | `order-service` | `..\mvnw.cmd spring-boot:run` |
   | 6 | `api-gateway` | `..\mvnw.cmd spring-boot:run` |

4. Browser tabs: <http://localhost:8761> (Eureka), <http://localhost:8082/actuator/circuitbreakers>,
   <http://localhost:8081/swagger-ui/index.html>.
5. IDE open on: `ProductController`, `OrderServiceImpl`, `IdempotencyService`, `PaymentClient`,
   `CircuitBreakerConfiguration`, `api-gateway/application.yml`.

**On the day:** start tabs 1–6 in order ~5 minutes before the session. Check the Eureka dashboard
shows 5 instances and that `00 Discovery and gateway` in Postman is green. Set payment failure mode
to `NONE` if anything was tested earlier.

---

## Timeline

| Time | Block | Show |
|---|---|---|
| 0–10 | Monolith vs microservices, the architecture | README diagram |
| 10–20 | Service discovery and the API Gateway | Eureka dashboard, gateway routes, load balancing |
| 20–40 | REST API design on Product Service | Postman folder 01, controller code, Swagger |
| 40–55 | Order flow, idempotency, HATEOAS | Postman folders 03 and 04 |
| 55–75 | Failure handling: timeout, circuit breaker, safe PENDING | Postman folder 05, actuator |
| 75–85 | Versioning, spec-first OpenAPI, testing | v2 call, contract test |
| 85–90 | Recap and questions | Trade-offs table |

---

### 0–10 · Architecture

* Open the README architecture diagram. Walk the solid arrows (requests) and the dotted ones (registry).
* Point out **database per service**: Order Service stores only `productId`, not a foreign key, and copies
  the price at order time.
* Ask: *"What breaks when Payment is down?"* — leave the question open; the answer comes at minute 55.

### 10–20 · Service discovery and Gateway [10.3, 10.4]

1. Eureka dashboard: 5 instances, two of them `PRODUCT-SERVICE`.
2. Postman → `00 Discovery and gateway` → *Gateway - route table*: three routes, all `lb://SERVICE-ID`.
   Show `api-gateway/application.yml`: no host or port anywhere.
3. Postman → `02 Load balancing`: send 4 times. Point at the `X-Served-By` header: 8081, 8084, 8081, 8084.
   Show the gateway console: one log line per request with the correlation id.
4. **Live failure:** stop Product #2 (Ctrl+C in tab 3). Send the request again a few times.
   For a short moment a request may still be routed to 8084 and fail with a 503 problem+json from
   the Gateway; after a few seconds only 8081 answers.
   Explain: heartbeat expiry 15 s + registry fetch 5 s + LoadBalancer cache 5 s = discovery is
   *eventually* consistent. Restart tab 3 (it is needed again later).

### 20–40 · REST API design [7]

Run `01 Products` request by request:

| Request | Talking point |
|---|---|
| List with `page=0&size=3&sort=price,desc` | Paging is mandatory for collections; size capped at 100; stable `PageResponse` shape |
| Create → 201 | `201 Created` + `Location` header. Note it says `localhost:8080`: forwarded headers (`trusted-proxies`) |
| PUT vs PATCH | PUT replaces everything (idempotent), PATCH changes only what is sent |
| Availability `quantity=11` → 200 `available:false` | "Not enough stock" is a valid **answer**, not an error |
| Duplicate SKU → 409 | 409 = valid request, conflicts with current state. Different from 400 |
| Validation → 400 | All 4 invalid fields in one problem+json response |
| Delete → 204, then GET → 404 | 204 has no body; DELETE is idempotent in effect |

Then open `ProductController`: no business logic, only HTTP. Open `GlobalExceptionHandler`: every
error becomes RFC 9457 problem+json, and 500s never leak stack traces.

> If folder 01 shows a 404 right after a create, the two Product instances answered different calls.
> That is the in-memory demo shortcut (README section 1) — a good moment to say *"in production both
> instances share one database"*. To avoid it, run folder 01 before restarting tab 3.

### 40–55 · Order flow, idempotency, HATEOAS [9, 10.1, 10.8]

1. Show the README sequence diagram.
2. Postman `03` → *Create order*: 201, `CONFIRMED`, `_links` (self, orders, product, payment).
3. *Repeat same request*: same id, header `Idempotent-Replayed: true`. Ask: *"Why does a client retry a
   POST at all?"* → the response was lost, not the request.
4. *Same key, different body* → 409. *Missing key* → 400.
5. Open `IdempotencyService`: the key is the **primary key** in `idempotency_records`; the IN_PROGRESS row
   is committed before the remote calls. Show it in the H2 console (8082, `jdbc:h2:mem:orderdb`):
   `SELECT * FROM idempotency_records`.
6. Postman `04`: the same idea one level down — Payment deduplicates by `operationId`: 201, then 200 with
   the same id, then 409 for a different amount.
7. Open `OrderServiceImpl.create`: **no `@Transactional` around remote calls** and why.

### 55–75 · Failure handling [10.5]

Keep <http://localhost:8082/actuator/circuitbreakers> visible next to Postman. Run `05 Resilience`
**one request at a time**:

1. *DECLINE* → create order → `FAILED`. A declined card is a **business** outcome; the breaker stays CLOSED.
2. *ERROR* → create order 3–4 times → `PENDING` each time. Refresh the actuator: failure rate rises,
   state becomes **OPEN**. The next order comes back in a few ms with *"circuit breaker is OPEN: payment
   was not attempted"* — Payment is not even called.
3. Show the `retry-payment` link in the PENDING response (HATEOAS: the link exists because of the state).
4. *NONE* → wait 15 s → actuator shows **HALF_OPEN** → *Retry payment* → `CONFIRMED`; after two successful
   trial calls the state is **CLOSED**. Show the transitions in the Order Service console and in
   `/actuator/circuitbreakerevents`.
5. *SLOW 3000* → create order → `PENDING` after ~2 s: the read timeout. *Payments for that order* → **1**:
   the provider finished late. *Retry payment* → `CONFIRMED`, still **one** payment: the operationId made
   the retry safe. This is the key moment of the session — timeout + idempotency together.
6. *NONE* (reset).

Answer the minute-10 question: Payment down ⇒ orders are accepted as PENDING, never shown as paid,
and resolved later without double charges.

Open `PaymentClient.classify` and `CircuitBreakerConfiguration` (4xx ignored, timeouts counted).

### 75–85 · Versioning, OpenAPI, tests [10.9, 10.10, 14]

1. `GET /api/v2/products/1` next to `/api/v1/products/1`: breaking change ⇒ new version; v1 untouched.
2. Swagger UI on 8081: switch between *spec-first* and *generated from code*.
3. Show `OpenApiContractTest`. Live: comment out `@PatchMapping` in `ProductController`, run
   `..\mvnw.cmd test -Dtest=OpenApiContractTest` in `product-service` → red, the message lists
   `PATCH /api/v1/products/{id}`. Undo.
4. Show `PaymentClientCircuitBreakerTest`: the full state machine verified in about one second
   without starting any server.

### 85–90 · Recap

| We used | Because | Price we pay |
|---|---|---|
| Database per service | Independent change and deployment | No joins/transactions across services |
| Synchronous REST | Simple, immediate answer | Temporal coupling → needs timeouts and circuit breaker |
| Client-side discovery | No central load balancer to run | Eventually consistent instance list |
| Idempotency keys | Safe retries for POST | One extra table and header |
| PENDING state | Never lie about payment | The client must handle a third outcome |

---

## Reset between runs

* Payment failure mode back to `NONE`.
* Restart Order Service and Payment Service to clear their in-memory data (optional).
* Circuit breaker: wait 15 s and send one successful order, or restart Order Service.

## If something goes wrong live

See README section 13. The two most common: a 503 from the Gateway right after starting a service
(wait 10–30 s for registration), and every order PENDING (failure mode not `NONE`, or the circuit is
still OPEN — wait 15 s).
