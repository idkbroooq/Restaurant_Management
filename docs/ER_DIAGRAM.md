# ER Diagram — Restaurant Table-Ordering System

Entity-relationship reference for the `restaurant_db` schema. All facts below are taken
directly from `db/schema.sql`, `db/advanced.sql` and `db/seed.sql`.

- Engine / charset: `InnoDB`, `utf8mb4` / `utf8mb4_unicode_ci` (every table).
- Base tables: **9** — `staff`, `category`, `menu_item`, `restaurant_table`,
  `dining_session`, `orders`, `order_item`, `payment`, `order_status_log`.
- Every table uses a single-column `AUTO_INCREMENT` surrogate primary key
  (`INT UNSIGNED`, except `order_status_log` which uses `BIGINT UNSIGNED`).

> **Notation note:** Mermaid does not accept spaces inside an attribute type token, so the
> SQL type `INT UNSIGNED` is rendered as `INT_UNSIGNED` (and `BIGINT UNSIGNED` as
> `BIGINT_UNSIGNED`). All other type tokens are literal. `ENUM` is shown as the type; the
> exact enum value lists are given in the [ENUM columns](#enum-columns) section.

```mermaid
erDiagram
    CATEGORY ||--o{ MENU_ITEM : "classifies"
    RESTAURANT_TABLE ||--o{ DINING_SESSION : "hosts"
    DINING_SESSION ||--o{ ORDERS : "contains"
    ORDERS ||--o{ ORDER_ITEM : "includes"
    MENU_ITEM ||--o{ ORDER_ITEM : "ordered as"
    DINING_SESSION ||--o| PAYMENT : "settled by"
    ORDERS |o--o{ PAYMENT : "optional payment"
    ORDERS ||--o{ ORDER_STATUS_LOG : "audited by"
    STAFF |o--o{ ORDER_STATUS_LOG : "changed by"

    CATEGORY {
        INT_UNSIGNED category_id PK
        VARCHAR(50) name
        VARCHAR(255) description
        INT display_order
        BOOLEAN is_active
    }
    MENU_ITEM {
        INT_UNSIGNED item_id PK
        INT_UNSIGNED category_id FK
        VARCHAR(100) name
        TEXT description
        DECIMAL(10,2) price
        ENUM item_type
        ENUM spice_level
        INT_UNSIGNED volume_ml
        BOOLEAN is_available
        BOOLEAN is_deleted
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }
    STAFF {
        INT_UNSIGNED staff_id PK
        VARCHAR(50) username
        VARCHAR(255) password_hash
        VARCHAR(100) full_name
        ENUM role
        BOOLEAN is_active
        TIMESTAMP created_at
    }
    RESTAURANT_TABLE {
        INT_UNSIGNED table_id PK
        VARCHAR(20) table_number
        INT_UNSIGNED capacity
        VARCHAR(64) qr_token
        BOOLEAN is_active
        TIMESTAMP created_at
    }
    DINING_SESSION {
        INT_UNSIGNED session_id PK
        INT_UNSIGNED table_id FK
        VARCHAR(64) session_token
        ENUM status
        TIMESTAMP start_time
        TIMESTAMP end_time
    }
    ORDERS {
        INT_UNSIGNED order_id PK
        INT_UNSIGNED session_id FK
        ENUM status
        DECIMAL(10,2) total_amount
        VARCHAR(255) special_notes
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }
    ORDER_ITEM {
        INT_UNSIGNED order_item_id PK
        INT_UNSIGNED order_id FK
        INT_UNSIGNED item_id FK
        INT_UNSIGNED quantity
        DECIMAL(10,2) unit_price
        DECIMAL(10,2) subtotal
        VARCHAR(255) item_notes
    }
    PAYMENT {
        INT_UNSIGNED payment_id PK
        INT_UNSIGNED session_id FK
        INT_UNSIGNED order_id FK
        DECIMAL(10,2) amount
        ENUM payment_method
        ENUM payment_status
        VARCHAR(100) transaction_ref
        TIMESTAMP paid_at
        TIMESTAMP created_at
    }
    ORDER_STATUS_LOG {
        BIGINT_UNSIGNED log_id PK
        INT_UNSIGNED order_id FK
        ENUM from_status
        ENUM to_status
        INT_UNSIGNED changed_by_staff_id FK
        VARCHAR(255) notes
        TIMESTAMP created_at
    }
```

`subtotal` in `ORDER_ITEM` is a `GENERATED ALWAYS AS (quantity * unit_price) STORED`
column: it is not supplied by inserts and is physically maintained by the engine.

---

## Relationships (foreign keys)

Every FK below is declared with an explicit `ON DELETE` / `ON UPDATE` clause in `schema.sql`.

| Parent table | Child table | FK column (child) | Referenced column | ON DELETE | ON UPDATE | Cardinality |
|---|---|---|---|---|---|---|
| `category` | `menu_item` | `menu_item.category_id` | `category.category_id` | RESTRICT | CASCADE | 1 category → 0..* menu items |
| `restaurant_table` | `dining_session` | `dining_session.table_id` | `restaurant_table.table_id` | RESTRICT | CASCADE | 1 table → 0..* sessions |
| `dining_session` | `orders` | `orders.session_id` | `dining_session.session_id` | RESTRICT | CASCADE | 1 session → 0..* orders |
| `orders` | `order_item` | `order_item.order_id` | `orders.order_id` | CASCADE | CASCADE | 1 order → 1..* line items |
| `menu_item` | `order_item` | `order_item.item_id` | `menu_item.item_id` | RESTRICT | CASCADE | 1 item → 0..* line items |
| `dining_session` | `payment` | `payment.session_id` | `dining_session.session_id` | RESTRICT | CASCADE | 1 session → 0..1 payment |
| `orders` | `payment` | `payment.order_id` (NULLable) | `orders.order_id` | RESTRICT | CASCADE | 1 order → 0..* payments |
| `orders` | `order_status_log` | `order_status_log.order_id` | `orders.order_id` | CASCADE | CASCADE | 1 order → 0..* log rows |
| `staff` | `order_status_log` | `order_status_log.changed_by_staff_id` (NULLable) | `staff.staff_id` | SET NULL | CASCADE | 1 staff → 0..* log rows |

Notes verifiable in `schema.sql`:

- The only `ON DELETE CASCADE` links are the two "child belongs to parent" links
  (`orders → order_item`, `orders → order_status_log`). All other FKs use `RESTRICT` so that
  history is preserved, and `order_status_log.changed_by_staff_id` uses `SET NULL` to keep the
  audit trail when a staff account is removed.
- `dining_session 1 → 0..1 payment` is the **modelled** cardinality (a sitting is settled by a
  single bill). The schema does **not** enforce it: `payment.session_id` carries no `UNIQUE`
  constraint, so the DB technically permits several payments per session. Only
  `payment.transaction_ref` and `payment.payment_id` are unique.
- `payment.order_id` is the only nullable FK that is not the audit-trail field; a payment may be
  recorded against a session without being tied to one specific order.

---

## Design decisions

**Why `order_item.unit_price` is stored (price snapshot).**
`order_item.unit_price` is `DECIMAL(10,2) NOT NULL` with `CHECK (unit_price > 0.00)`. It records
the agreed price **at the moment the line was ordered**, not a live reference to
`menu_item.price`. If the menu price later changes, reprints/re-audits of an old bill still show
the price the guest actually paid. This is the DB-side expression of the project rule "never
trust prices from the client; read the price from the DB" (`CLAUDE.md` Rule 2). `subtotal =
quantity * unit_price` is then a `STORED GENERATED` column so the line total can never drift from
its inputs.

**Why `dining_session` exists separately from `orders`.**
A physical table (`restaurant_table`) can host many sittings over time, and one sitting can place
**multiple incremental orders** ("another round of naan"). `dining_session` models the *visit*
(seating → payment → departure) with `start_time`, `end_time`, `status`
(`ACTIVE|COMPLETED|CANCELLED`) and a unique `session_token` that backs the QR flow
(`menu.html?table=<token>`). `orders` models an individual *kitchen ticket* within that visit.
Collapsing the two would either force one giant order per visit or repeat table/seat/timing data
on every ticket. `sp_generate_bill(session_id)` and queries 3 and 9 confirm the session is the
billing/turnover unit.

**Why `order_status_log` is an audit table.**
`order_status_log` is append-only history: one row per status change with `from_status`,
`to_status`, `changed_by_staff_id`, `notes` and `created_at`. It is populated automatically by the
`AFTER UPDATE` trigger `trg_orders_log_status_change` in `db/advanced.sql`, so no application code
needs to remember to log. `orders.status` holds only the *current* value; the log holds the
*history*. This enables operational KPIs such as average kitchen prep time (Query 7, a self-join
on `to_status = 'ACCEPTED'` and `to_status = 'READY'`) and staff accountability. The matching
`BEFORE UPDATE` trigger `trg_orders_validate_status_transition` enforces the state machine
(`PLACED → ACCEPTED → PREPARING → READY → SERVED`, with `CANCELLED` reachable from any
non-terminal state) and forbids changes once a terminal state is reached.

**What the two views expose (`db/advanced.sql`).**

- `v_kitchen_active_orders` — a **read-only projection** over
  `orders ⋈ dining_session ⋈ restaurant_table ⋈ order_item ⋈ menu_item`, filtered to in-flight
  tickets (`status IN ('PLACED','ACCEPTED','PREPARING','READY')`). It flattens the join topology
  for the kitchen display (which polls every 3 s) and adds a derived `elapsed_minutes =
  TIMESTAMPDIFF(MINUTE, o.created_at, NOW())` for colour-coding.
- `v_order_totals` — an **auditing projection** that aggregates `order_item.subtotal` per order
  (`SUM`, `COUNT`, `SUM(quantity)`) and compares it against the cached `orders.total_amount`,
  emitting `total_audit_status = 'MATCH' | 'MISMATCH'`. This is the double-entry check that
  detects any drift between the stored header total and the sum of its frozen line items.

Neither view stores data — they are `CREATE OR REPLACE VIEW` definitions executed on read, so they
are not a schema-level denormalization.

---

## Indexes

### Performance indexes (explicit `CREATE INDEX`)

| Index | Columns | Query it speeds up |
|---|---|---|
| `idx_orders_kitchen` | `orders(status, created_at)` | Kitchen-display polling: `SELECT * FROM orders WHERE status IN ('PLACED','ACCEPTED','PREPARING') ORDER BY created_at ASC`. The composite B-tree satisfies both the filter and the sort, avoiding a filesort. |
| `idx_menu_item_category_id` | `menu_item(category_id)` | Menu listing per category (Query 2) and quick FK validation on `fk_menu_item_category`. |
| `idx_dining_session_table_id` | `dining_session(table_id)` | Table-utilisation / turnover reports (Query 9) and FK validation on `fk_dining_session_table`. |
| `idx_orders_session_id` | `orders(session_id)` | Session bill aggregation and high-value-session reporting (Queries 3 & `sp_generate_bill`) plus FK validation on `fk_orders_session`. |
| `idx_order_item_order_id` | `order_item(order_id)` | Itemised receipt / order-detail traversal (Query 1) and `fn_order_total` / `v_order_totals` aggregation per order. |
| `idx_order_item_item_id` | `order_item(item_id)` | Top-selling-items ranking and "items never ordered" anti-join (Queries 5 & 8) plus FK validation on `fk_order_item_menu_item`. |
| `idx_payment_session_id` | `payment(session_id)` | Finding the bill for a session and FK validation on `fk_payment_session`. |
| `idx_payment_order_id` | `payment(order_id)` | Joining payments back to orders and FK validation on `fk_payment_order`. |
| `idx_order_status_log_order_id` | `order_status_log(order_id)` | Per-order status history and prep-time self-join (Query 7). |
| `idx_order_status_log_staff_id` | `order_status_log(changed_by_staff_id)` | Staff-activity grouping and FK validation on `fk_order_status_log_staff`. |

### Unique constraints (also built as unique indexes)

| Constraint | Table (column) | Purpose |
|---|---|---|
| `uq_restaurant_table_number` | `restaurant_table(table_number)` | Duplicate table numbers impossible; fast lookup by number. |
| `uq_restaurant_table_qr_token` | `restaurant_table(qr_token)` | QR scan resolves a table: `menu.html?table=<qr_token>`. |
| `uq_category_name` | `category(name)` | Prevent duplicate category names. |
| `uq_menu_item_name` | `menu_item(name)` | Prevent duplicate dish names. |
| `uq_staff_username` | `staff(username)` | Login lookup by unique username. |
| `uq_dining_session_token` | `dining_session(session_token)` | Resolve the active session from its token. |
| `uq_order_item_unique` | `order_item(order_id, item_id)` | At most one line per item per order (repeat quantities go in `quantity`). |
| `uq_payment_transaction_ref` | `payment(transaction_ref)` | Unique payment reference (nullable; multiple NULLs allowed). |

Every `AUTO_INCREMENT` primary key is itself an index (`PRIMARY` clustered index in InnoDB).
Composite `CHECK`/domain rules (`chk_menu_item_type_attributes`, `chk_menu_item_price`,
`chk_restaurant_table_capacity`, `chk_orders_total_amount`, `chk_order_item_quantity`,
`chk_order_item_unit_price`, `chk_payment_amount`) are validation rules, not indexes.

---

## ENUM columns

| Table | Column | ENUM values (in definition order) | Nullable | Default |
|---|---|---|---|---|
| `staff` | `role` | `'CHEF'`, `'WAITER'`, `'MANAGER'` | NO | — |
| `menu_item` | `item_type` | `'FOOD'`, `'BEVERAGE'`, `'DESSERT'` | NO | — |
| `menu_item` | `spice_level` | `'MILD'`, `'MEDIUM'`, `'HOT'`, `'EXTRA_HOT'` | YES | `NULL` |
| `dining_session` | `status` | `'ACTIVE'`, `'COMPLETED'`, `'CANCELLED'` | NO | `'ACTIVE'` |
| `orders` | `status` | `'PLACED'`, `'ACCEPTED'`, `'PREPARING'`, `'READY'`, `'SERVED'`, `'CANCELLED'` | NO | `'PLACED'` |
| `payment` | `payment_method` | `'CASH'`, `'CARD'`, `'UPI'` | NO | — |
| `payment` | `payment_status` | `'PENDING'`, `'COMPLETED'`, `'FAILED'`, `'REFUNDED'` | NO | `'PENDING'` |
| `order_status_log` | `from_status` | `'PLACED'`, `'ACCEPTED'`, `'PREPARING'`, `'READY'`, `'SERVED'`, `'CANCELLED'` | YES | `NULL` |
| `order_status_log` | `to_status` | `'PLACED'`, `'ACCEPTED'`, `'PREPARING'`, `'READY'`, `'SERVED'`, `'CANCELLED'` | NO | — |

### Comparison with the Java enums (`src/main/java/com/restaurant/enums/`)

Each DB ENUM was compared literally against its Java counterpart.

| DB column | Java enum | Java constants (declaration order) | Match |
|---|---|---|---|
| `staff.role` | `StaffRole` | `CHEF`, `WAITER`, `MANAGER` | ✅ exact |
| `menu_item.item_type` | `ItemType` | `FOOD`, `BEVERAGE`, `DESSERT` | ✅ exact |
| `menu_item.spice_level` | `SpiceLevel` | `MILD`, `MEDIUM`, `HOT`, `EXTRA_HOT` | ✅ exact |
| `dining_session.status` | `SessionStatus` | `ACTIVE`, `COMPLETED`, `CANCELLED` | ✅ exact |
| `orders.status` | `OrderStatus` | `PLACED`, `ACCEPTED`, `PREPARING`, `READY`, `SERVED`, `CANCELLED` | ✅ exact |
| `payment.payment_method` | `PaymentMethodType` | `CASH`, `CARD`, `UPI` | ✅ exact |
| `payment.payment_status` | `PaymentStatus` | `PENDING`, `COMPLETED`, `FAILED`, `REFUNDED` | ✅ exact |
| `order_status_log.from_status` / `to_status` | `OrderStatus` | `PLACED`, `ACCEPTED`, `PREPARING`, `READY`, `SERVED`, `CANCELLED` | ✅ exact |

**No mismatch found.** All names, spelling and ordering agree between the database ENUMs and the
Java enums. `OrderStatus` additionally carries the transition logic (`getNextStatuses()`,
`canTransitionTo()`, `isTerminal()`), which mirrors the state machine enforced by
`trg_orders_validate_status_transition`.

---

## Sample data volumes (`db/seed.sql`)

| Table | Rows seeded |
|---|---|
| `category` | 6 |
| `restaurant_table` | 10 |
| `staff` | 3 |
| `menu_item` | 29 (5 starters, 7 mains, 6 breads/rice, 2 accompaniments, 4 desserts, 5 beverages) |
| `dining_session` | 10 (7 ACTIVE, 3 COMPLETED) |
| `orders` | 12 (spanning PLACED, ACCEPTED, PREPARING, READY, SERVED, CANCELLED) |
| `order_item` | 37 lines across the 12 orders |
| `payment` | 3 (all COMPLETED: CARD, UPI, CASH) |
| `order_status_log` | 37 audit rows |
