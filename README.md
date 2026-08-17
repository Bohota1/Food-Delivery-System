# Food Delivery System — Microservices Project

Group project: Food Delivery System built with Spring Boot microservices.

## Services & Ports
| Service | Port | Gateway path | Spring Boot |
|---|------|---|---|
| service-registry | 8761 | — | 4.1.0 |
| api-gateway | 8080 | — | 4.1.0 |
| user-service | 8081 | `/api/users/**` | 3.2.5 |
| restaurant-service | 9002 | `/restaurants/**` | 4.1.0 |
| order-service | 9013 | `/orders/**` | 4.1.0 |
| payment-service | 9004 | `/payments/**` | 4.1.0 |
| delivery-service | 8083 | `/api/deliveries/**`, `/api/riders/**` | 3.2.5 |

## Tech Stack
- Java 17, Spring Boot, Maven
- MongoDB Atlas (shared cluster, one database per service)
- Netflix Eureka (service discovery)
- Spring Cloud Gateway (API Gateway)
- RabbitMQ (async communication: Order → Payment → Delivery)

## Running the system on a fresh machine

### Prerequisites

| Need | Why | Check |
|---|---|---|
| **Java 17** | every service | `java -version` |
| **Docker Desktop** | runs RabbitMQ | `docker --version` |
| **Node.js** | serves the website | `node --version` |
| Atlas credentials | see *Database configuration* | ask the team |

Maven is **not** required — services use the `./mvnw` wrapper, which downloads Maven on
first run. Note `user-service` and `delivery-service` have no wrapper of their own; run
them with another service's, e.g. `../order-service/mvnw spring-boot:run`.

> **Atlas IP allowlist:** a new machine or network is blocked until its IP is allowed.
> In Atlas → Network Access, ensure `0.0.0.0/0` is present, or add the new IP. Symptom is
> a startup that hangs then fails with a timeout selecting a server.

### 1 — Configure credentials

Create these three **gitignored** files (see *Database configuration* below for content):

```
restaurant-service/src/main/resources/application.properties
order-service/src/main/resources/application.properties
payment-service/src/main/resources/application.properties
```

### 2 — Start RabbitMQ

```bash
docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management
```

Management UI at http://localhost:15672 (guest / guest). Already created once? Use
`docker start rabbitmq`.

### 3 — Start the services

Each in **its own terminal**, from inside the service folder. Start the registry first
and the gateway last; the five business services can start in any order between.

```bash
cd service-registry   && ./mvnw spring-boot:run     # 8761 - wait for it to load
cd restaurant-service && ./mvnw spring-boot:run     # 9002
cd order-service      && ./mvnw spring-boot:run     # 9013
cd payment-service    && ./mvnw spring-boot:run     # 9004
cd api-gateway        && ./mvnw spring-boot:run     # 8080 - start last
```

The two services without a wrapper also need `MONGODB_URI`:

```bash
cd user-service
MONGODB_URI="mongodb+srv://<user>:<pass>@fooddeliverycluster.vcthmp8.mongodb.net/customer_db?appName=FoodDeliveryCluster" ../order-service/mvnw spring-boot:run
```

```bash
cd delivery-service
MONGODB_URI="mongodb+srv://<user>:<pass>@fooddeliverycluster.vcthmp8.mongodb.net/delivery_db?appName=FoodDeliveryCluster" ../order-service/mvnw spring-boot:run
```

On Windows PowerShell use `$env:MONGODB_URI="..."` on its own line first, then `mvnw spring-boot:run`.

### 4 — Start the website

```bash
node frontend/serve.js
```

http://localhost:5173 — customer site. http://localhost:5173/console.html — API console.

### 5 — Check everything registered

http://localhost:8761 should list all six applications. Give Eureka ~30 seconds after a
service starts before routing through the gateway, or you'll get 503s.

## Database configuration

Shared Atlas cluster: `FoodDeliveryCluster`. Each service owns its own database:
`customer_db`, `restaurant_db`, `order_db`, `payment_db`, `delivery_db`.

**Credentials are never committed.** How you supply them depends on the service:

| Service | Mechanism |
|---|---|
| restaurant, order, payment | `src/main/resources/application.properties` (gitignored) |
| user, delivery | `MONGODB_URI` environment variable |

For restaurant / order / payment, create `application.properties`:

```properties
spring.application.name=<service-name>
spring.mongodb.uri=mongodb+srv://<user>:<password>@fooddeliverycluster.vcthmp8.mongodb.net/?appName=FoodDeliveryCluster
spring.mongodb.database=<service>_db
```

For user / delivery, set the environment variable before starting (otherwise they
fall back to a **local** MongoDB at `localhost:27017`, not Atlas):

```bash
export MONGODB_URI="mongodb+srv://<user>:<password>@fooddeliverycluster.vcthmp8.mongodb.net/customer_db?appName=FoodDeliveryCluster"
```

## Customer website

```bash
node frontend/serve.js
```

Then open **http://localhost:5173** — browse restaurants, build a cart, sign in, place an
order and watch it move through payment and delivery. A developer API console for testing
every endpoint directly lives at **http://localhost:5173/console.html**.

### Setting up data

There is no seed script — everything is entered by hand. With all services running, open
the **Admin** tab and work down it:

1. **Add a restaurant** — name, address, cuisine, phone.
2. **Add a menu item** — pick the restaurant, then name, price, category.
3. **Register a rider** — name, phone, vehicle. At least one rider must exist before an
   order is placed, otherwise the delivery is created but stays `PENDING` with no rider.

Below the forms, **Manage restaurants & menus** lists everything already created. From
there an Admin can open/close a restaurant, delete it, rename a dish or change its price,
mark a dish out of stock, and remove dishes. Closing a restaurant or marking a dish out of
stock is visible to the customer straight away.

Then use the customer side normally: register, browse, add to cart, checkout.

The website only ever calls the API Gateway on :8080, never a service port. The gateway
therefore has CORS enabled — browsers preflight JSON requests, which Postman never does.

## Asynchronous workflow (RabbitMQ)

```
Order placed    ──order_exchange / order_routingKey──────►  Payment Service
Payment done    ──payment_exchange / payment_routingKey──►  Order Service    (order CONFIRMED)
Order CONFIRMED ──order.exchange / order.confirmed───────►  Delivery Service (delivery created,
                                                                              rider auto-assigned)
Delivery moves  ──delivery.exchange / delivery.status────►  Order Service    (order DELIVERED
                                                                              once completed)

Order cancelled ──refund_exchange / refund_routingKey────►  Payment Service  (payment REFUNDED)
Order cancelled ──order.exchange / order.cancelled───────►  Delivery Service (delivery cancelled,
                                                                              rider freed)
```

Rider assignment is owned by Delivery Service: consuming `order.confirmed` creates the
delivery **and** assigns the first available rider, so no external call is needed. If no
rider is free the delivery stays `PENDING` and can be assigned manually later.

Exchange and routing-key names must match exactly on both sides. They are defined in
each service's `Constants.java` (or `RabbitMQConfig.java` for delivery-service).

## Use cases covered

| Use case | Where |
|---|---|
| Register / Login | `POST /api/users/register`, `/login` |
| Manage profile & addresses | `/api/users/profile`, `/api/users/addresses` |
| Manage restaurant profile | `/restaurants/**` |
| Manage menu items | `/restaurants/{id}/menu/**` |
| Browse restaurants & menus | `/restaurants/available`, `/restaurants/search` |
| Place new order | `POST /orders/` |
| Track order status | `GET /orders/{id}`, `GET /orders/customer/{customerId}` |
| **Cancel order** | `POST /orders/{id}/cancel` — refunds if paid, cancels the delivery |
| Make payment | asynchronous, via `order_queue` |
| **Request refund** | `POST /payments/order/{orderId}/refund` |
| **Assign rider** | automatic, on the `order.confirmed` event — `POST /api/deliveries/{id}/assign` only for manual override |
| Update delivery status | `PUT /api/deliveries/{id}/status` — `PICKED_UP` |
| **Complete delivery** | `POST /api/deliveries/{id}/complete` — marks DELIVERED and frees the rider |
| View assigned deliveries | `GET /api/deliveries/rider/{riderId}` |
| Register rider | `POST /api/riders` |

## Setup for teammates
1. Clone this repo.
2. Create your branch: `git checkout -b feature/<your-service-name>`
3. Build your assigned service inside its folder using Spring Initializr (Java 17, Spring Web, Spring Data MongoDB, Lombok, Eureka Discovery Client).
4. Configure your database credentials as described above — do NOT commit real passwords.
5. Push your branch and open a Pull Request into `main`.

## API testing
Postman collections live in `postman/`. Import them into a Postman workspace and set
the `baseUrl` variable to either the service's own port or the gateway (`http://localhost:8080`).
