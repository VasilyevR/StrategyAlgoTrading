# Strategy Algorithmic Trading

**Strategy Algorithmic Trading** is a multi-user, multi-broker event-driven trading platform designed to execute customizable technical analysis strategies across cryptocurrency and equity markets by combining indicator signal bitmasks with real-time exchange positions, orders, and automated trailing stop-losses.

## Technology Stack

Built as a **Maven multi-module** microservices system on **Java 25 LTS** (leveraging Virtual Threads) and **Spring Boot**, the stack includes **Apache Kafka (KRaft)** for real-time market data and control streams, **PostgreSQL** with **Flyway** for persistence and schema migrations, **Thymeleaf + HTMX** for the client and admin portal, and **Docker Compose** (with a planned migration path to **Kubernetes / k3s**) automated via **GitHub Actions**.

## Mapping Convention

Use MapStruct interfaces for entity-to-DTO mapping across services; do not add hand-written field-by-field mapper classes. The parent Maven build configures Lombok and MapStruct annotation processing with unmapped target properties treated as errors.