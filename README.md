# Strategy Algorithmic Trading

Multi-user, multi-broker, event-driven algorithmic trading platform for cryptocurrency (**Bybit V5**) and equity/ETF (**Interactive Brokers**) markets.

Users compose custom trading strategies in a no-code **Strategy Builder** (combining technical indicators into disjunctive normal form `AND`/`OR` condition groups) and selectively share proprietary strategies with other users without exposing underlying indicator parameters.

---

## Key Features

- **Low-Latency Execution on Java 25 Virtual Threads:** Dedicated Virtual Thread per active bot instance with `ReentrantLock` synchronization (zero `synchronized` blocks on hot paths to prevent carrier-thread pinning).
- **Modular Technical Analysis Engine (`ta4j-core`):** 11 stateless indicator evaluators (`SMA_CROSS`, `MACD`, `CMF`, `SAR`, `AO`, `ULTOSC`, `CCI`, `ROC`, `KAMA`, `STOCH`, `STOCH_RVI` including custom Ehlers Stochastic RVI) evaluated into `java.util.BitSet` direction masks.
- **Flexible Exit & Risk Management:** Supports signal-based market exits, exchange-native atomic Trailing Stop-Loss updates on candle close (e.g., Parabolic SAR), hybrid exits, and risk-percentage position sizing.
- **Proprietary Strategy Sharing (RBAC):** Fine-grained access control allowing authors to share live executable strategies while keeping indicator formulas private (`allow_copy` flag).
- **Strict Microservice Table Ownership:** Each PostgreSQL table has a single owning service; cross-service reads use internal REST contracts (`@HttpExchange` + `RestClient`) secured via constant-time `X-Internal-Token` authentication.

---

## Architecture

| Module | Port | Responsibility | Owned Tables |
| :--- | :---: | :--- | :--- |
| **`common-contracts`** | — | Shared DTO records, wire enums, Kafka events, common Spring properties | — |
| **`portal-api`** | `8080` | Flyway schema owner, Thymeleaf + HTMX UI, Spring Security RBAC, Strategy Builder & sharing | `users`, `broker*`, `instrument*`, `indicator*`, `strategy*`, `algotrading` |
| **`market-collector`** | `8081` | Exchange WebSocket/REST market data ingestion, candle aggregation, Kafka producers | `candle` |
| **`trading-engine`** | `8082` | Virtual Thread workers, `ta4j-core` indicators, `BitSet` DNF evaluator, broker adapters | `orders`, `position`, `signal` |

---

## Tech Stack

- **Core & Concurrency:** Java 25 LTS (Project Loom Virtual Threads), Maven Multi-Module
- **Backend & Web:** Spring Boot 4.2, Spring Web (`@HttpExchange` / `RestClient`), Spring Data JPA, Spring Security, Spring Validation, Spring Actuator
- **UI:** Thymeleaf, HTMX, Spring Security Extras
- **Messaging & Data:** Apache Kafka (KRaft mode), PostgreSQL 16 (`JSONB`, `NUMERIC(20, 8)`), Flyway, Caffeine Cache
- **Quant & Mapping:** `ta4j-core`, `java.util.BitSet`, `BigDecimal`, MapStruct, Lombok
- **Testing:** JUnit 5, AssertJ, Mockito, Instancio, MockMvc, WireMock, Testcontainers (PostgreSQL)
- **Infrastructure & CI/CD:** Docker Compose, GitHub Actions (`ghcr.io`), Kubernetes (`k3s` / Helm)

---

## Engineering Conventions

- **Persistence & Mapping:** Anemic JPA entities validated via `hibernate.ddl-auto=validate` against Flyway migrations; all Entity-to-DTO mappings generated at compile time via MapStruct (`unmappedTargetPolicy = ERROR`).
- **Monetary Precision:** Strict `BigDecimal` (`NUMERIC(20, 8)`) for prices, volumes, PnL, and fees; primitive `double` isolated inside `ta4j-core` math calculations.