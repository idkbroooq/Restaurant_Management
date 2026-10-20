# Restaurant Table-Ordering System (College Lab: RDBMS + Java OOP)

A QR-code based restaurant table ordering system built with Java 17, Spring Boot 3, and MySQL 8.

Customers scan a QR code on the table, order from a mobile menu, and the order appears live on the kitchen display. Staff move it `PLACED → ACCEPTED → PREPARING → READY → SERVED`, then take the bill and settle the payment.

---

## Quick Start (5 steps)

> Prerequisites: **JDK 17+**, **MySQL 8 / MariaDB** running on `localhost:3306`, and the bundled Maven wrapper (no separate Maven install needed).

**1. Import the schema** (creates the database, 9 tables, constraints and indexes)

```bash
mysql -u root -p < db/schema.sql
```

**2. Load the seed data** (6 categories, 29 menu items, 10 tables, 3 staff accounts, sample orders)

```bash
mysql -u root -p < db/seed.sql
```

**3. Install the database objects** (views, triggers, function, stored procedure, and the least-privilege `restaurant_app` user)

```bash
mysql -u root -p < db/advanced.sql
```

**4. Build the application**

```powershell
.\mvnw.cmd clean compile
```

**5. Run it**

```powershell
.\mvnw.cmd spring-boot:run
```

The application starts on **<http://localhost:8080>**. Open it and you will land on a page linking to all three UIs, the API docs and the demo credentials.

### Where to go next

| URL | What it is |
| :--- | :--- |
| <http://localhost:8080/> | Landing page with links + demo credentials |
| <http://localhost:8080/customer/index.html?table=tok_tbl_05_e5b4a32109876543> | Customer ordering UI (mobile-first; `?table=` is the QR token) |
| <http://localhost:8080/kitchen/> | Kitchen display board (sign in as a CHEF) |
| <http://localhost:8080/admin/> | Manager console (sign in as a MANAGER) |
| <http://localhost:8080/swagger-ui.html> | Interactive Swagger UI |
| <http://localhost:8080/api-docs> | Raw OpenAPI document |

### Documentation index

| Document | Contents |
| :--- | :--- |
| [docs/openapi.yaml](docs/openapi.yaml) | The API contract — single source of truth |
| [docs/API.md](docs/API.md) | Every endpoint with a `curl` example and the real observed response |
| [docs/ER_DIAGRAM.md](docs/ER_DIAGRAM.md) | Entity-relationship diagram, relationships, indexes |
| [docs/NORMALIZATION.md](docs/NORMALIZATION.md) | 1NF → 3NF/BCNF walkthrough and deliberate denormalizations |
| [docs/SQL_QUERIES.md](docs/SQL_QUERIES.md) | Each significant query with its purpose and real sample output |
| [docs/TEST_CASES.md](docs/TEST_CASES.md) | Test case table (id / scenario / steps / expected / actual / pass-fail) |
| [docs/CODE_REVIEW.md](docs/CODE_REVIEW.md) | Strict code review + QA audit, fixes and the manual end-to-end run |
| [docs/VIVA_QUESTIONS.md](docs/VIVA_QUESTIONS.md) | 40 likely viva questions with short answers (DB / Java OOP / architecture) |
| [docs/DEMO_SCRIPT.md](docs/DEMO_SCRIPT.md) | Timed 5-minute demonstration script |

---

## Technology Stack

* **Java**: Version 17
* **Framework**: Spring Boot 3 (`spring-boot-starter-web`, `spring-boot-starter-jdbc`, `spring-boot-starter-validation`)
* **Persistence**: Spring JDBC `JdbcTemplate` (**Strictly NO JPA / Hibernate**)
* **Database**: MySQL 8 (InnoDB, `utf8mb4_unicode_ci`)
* **Security**: `spring-security-crypto` (`BCryptPasswordEncoder` for password hashing)
* **API Documentation**: SpringDoc OpenAPI 3 (`/swagger-ui.html` and `docs/openapi.yaml`)
* **Frontend**: Plain HTML, CSS, JavaScript in `src/main/resources/static` (Kitchen display polls every 3s)

---

## Architectural Rules

1. **SQL Encapsulation**: SQL queries live **only** in repository classes using prepared statements (`?` placeholders).
2. **Server-Side Pricing**: Never trust prices from the client; prices are strictly read from the database.
3. **Monetary Precision**: Money is stored as `DECIMAL(10,2)` in MySQL and represented as `BigDecimal` in Java.
4. **Transaction Atomicity**: Placing an order runs inside **ONE** database transaction (`@Transactional`).
5. **Strict Layering**: `Controller` $\longrightarrow$ `Service` $\longrightarrow$ `Repository` $\longrightarrow$ `MySQL Database`.
6. **OOP & Design Patterns**: Demonstrates Encapsulation, Inheritance, Abstraction, Polymorphism, Enums, Custom Exceptions, Generics/Collections, Composition, DAO Pattern, Factory Pattern, and Strategy Pattern. Marked with `// OOP: <concept>`.
7. **Secrets Management**: Secrets are provided via environment variables, never committed in source code.
8. **Schema Integrity**: Any database schema modification must be executed in `db/schema.sql`.

---

## Database Setup

1. **Create Database & Tables**:
   ```bash
   mysql -u root -p < db/schema.sql
   ```
2. **Seed Initial Data** (Categories, Menu items, Tables, Staff with BCrypt hashes, Sample orders):
   ```bash
   mysql -u root -p < db/seed.sql
   ```
3. **Install Views, Triggers, Functions & App User Privileges**:
   ```bash
   mysql -u root -p < db/advanced.sql
   ```

---

## Environment Variables

Credentials are **never committed**. Each developer keeps them in the git-ignored `config/local.properties`:

```bash
cp config/local.properties.example config/local.properties   # then edit the password
```

```properties
# config/local.properties  (git-ignored)
spring.datasource.username=restaurant_app
spring.datasource.password=<the password you set in db/advanced.sql>
```

The application imports that file automatically (`spring.config.import` in `application.properties`), so simply running the app picks it up. Environment variables take precedence and can be used instead (e.g. in CI):

| Variable | Description | Default |
| :--- | :--- | :--- |
| `DB_URL` | JDBC connection URL. `connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true` pins the MySQL **session** zone to UTC so every timestamp the API emits with a trailing `Z` really is UTC. | `jdbc:mysql://localhost:3306/restaurant_db?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&characterEncoding=UTF-8` |
| `DB_USER` | MySQL username (least-privilege app user) | `restaurant_app` |
| `DB_PASSWORD` | MySQL password for the application user | *(none — set in `config/local.properties`)* |
| `PORT` | Spring Boot HTTP server port | `8080` |

---

## Running the Application

### 1. Build & Compile with Maven
```powershell
.\mvnw.cmd clean compile
```

### 2. Run the Application
```powershell
.\mvnw.cmd spring-boot:run
```

The application will start on `http://localhost:8080`.

---

## Verification & Testing

### 1. Health Probe
Execute a `GET` request to verify Spring Boot and MySQL connectivity:
```bash
curl -i http://localhost:8080/api/health
```

Expected Response (`200 OK`):
```json
{
  "status": "UP",
  "database": "CONNECTED",
  "timestamp": "2026-10-19T02:45:00Z"
}
```

### 2. Interactive Swagger UI
Open in your browser:
* **Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
* **OpenAPI Raw Specification**: [http://localhost:8080/api-docs](http://localhost:8080/api-docs)

---

## Seed Staff Accounts

| Username | Plain-Text Password | Role |
| :--- | :--- | :--- |
| `chef_mario` | `ChefPassword123!` | `CHEF` |
| `waiter_john` | `WaiterPassword123!` | `WAITER` |
| `manager_alice` | `ManagerPassword123!` | `MANAGER` |

See [STAFF_ACCOUNTS.md](STAFF_ACCOUNTS.md) for the full list of staff roles, credentials, and access.
