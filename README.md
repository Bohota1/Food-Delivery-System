# Food Delivery System — Microservices Project

Group project: Food Delivery System built with Spring Boot microservices.

## Services & Ports
| Service | Port |
|---|------|
| service-registry | 8761 |
| api-gateway | 8080 |
| user-service | 9001 |
| restaurant-service | 9002 |
| order-service | 9013 |
| payment-service | 9004 |
| delivery-service | 9005 |

## Tech Stack
- Java 17, Spring Boot, Maven
- MongoDB Atlas (shared cluster, one database per service)
- Netflix Eureka (service discovery)
- Spring Cloud Gateway (API Gateway)
- RabbitMQ (async communication: Order → Payment → Delivery)

## Setup for teammates
1. Clone this repo.
2. Create your branch: `git checkout -b feature/<your-service-name>`
3. Build your assigned service inside its folder using Spring Initializr (Java 17, Spring Web, Spring Data MongoDB, Lombok, Eureka Discovery Client).
4. Use your own MongoDB Atlas username/password (ask the team for your credentials) in `application.properties` — do NOT commit real passwords.
5. Push your branch and open a Pull Request into `main`.

## MongoDB
Shared Atlas cluster: `FoodDeliveryCluster`. Each service has its own database:
customer_db, restaurant_db, order_db, payment_db, delivery_db.