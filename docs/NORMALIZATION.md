# Normalization Analysis — Restaurant Table-Ordering System

This document walks `restaurant_db` (see `db/schema.sql`) through 1NF, 2NF, 3NF and BCNF.
Every column named below is real and appears in `db/schema.sql`. Where the project deliberately
denormalizes, the trade-off is stated and the divergence is verified against the actual schema
(not assumed).

Schema recap: 9 base tables — `staff`, `category`, `menu_item`, `restaurant_table`,
`dining_session`, `orders`, `order_item`, `payment`, `order_status_log`. Each table has a
single-column surrogate primary key. The only composite key anywhere is the **candidate key**
`uq_order_item_unique (order_id, item_id)` on `order_item`.

---

## 1NF — atomic values, no repeating groups

**Rule:** every column holds a single atomic value; no column stores a list/set; no repeating
column groups.

**Counter-example (before).** A naive single-table design would put a whole ticket on one `orders`
row:

```
orders(order_id, table_number, status, ordered_items, special_notes)
-- ordered_items = '1 x Paneer Tikka, 2 x Garlic Naan, 1 x Fresh Lime Soda'
```

`ordered_items` is a non-atomic, multi-valued cell. You cannot index an individual dish, you
cannot constrain `quantity > 0`, and querying "top selling items" means string parsing.

**Actual schema (after).** The repeating group is extracted into the child table `order_item`, one
row per dish:

```
order_item(order_item_id, order_id, item_id, quantity, unit_price, subtotal, item_notes)
```

- `quantity` is a single `INT UNSIGNED` (`CHECK (quantity > 0)`) instead of "2 x".
- `item_notes` is per line, distinct from `orders.special_notes` (per ticket).
- The candidate key `uq_order_item_unique (order_id, item_id)` guarantees at most one row per dish
  per order (quantities are multiplied, not repeated).
- The parent `orders(order_id, session_id, status, total_amount, special_notes, created_at,
  updated_at)` carries only ticket-level scalars.

All remaining columns are scalar: `DECIMAL(10,2)` money, `VARCHAR` text, `TIMESTAMP` dates, `ENUM`
codes, `BOOLEAN` flags.

**Verdict: 1NF satisfied.**

---

## 2NF — no partial dependency on a composite key

**Rule:** 2NF is only violatable when a table has a composite key and a non-key attribute depends
on *part* of it. A table with a single-column primary key is automatically in 2NF.

**Counter-example (before).** Suppose `order_item` reused the composite key `(order_id, item_id)`
directly and also stored menu attributes:

```
order_item(order_id, item_id, item_name, quantity, unit_price)
-- PK (order_id, item_id);  item_name depends on item_id ALONE  → PARTIAL dependency
```

Here `item_name` is determined by `item_id` alone, not by the full `(order_id, item_id)` key — a
classic 2NF violation (and an update anomaly: renaming a dish touches every line ever ordered).

**Actual schema (after).**

- Every table has a single-column surrogate key: `order_item.order_item_id`,
  `orders.order_id`, `menu_item.item_id`, etc. A single-attribute key cannot produce a partial
  dependency, so the violation above cannot occur.
- **`item_name`, `description`, `price`, `item_type`, `spice_level`, `volume_ml` live only in
  `menu_item`**, keyed by `item_id`. `order_item` stores only `item_id` (FK) plus the line's own
  facts `quantity`, `unit_price`, `subtotal`, `item_notes`.
- The composite key `(order_id, item_id)` survives only as the **unique constraint**
  `uq_order_item_unique`, not as the primary key, so it constrains uniqueness without creating a
  partial-dependency surface.
- `staff` correctly holds only `staff_id`, `username`, `password_hash`, `full_name`, `role`,
  `is_active`, `created_at` — each depends on the whole `staff_id`; role data is not duplicated
  onto `order_status_log` (which stores only `changed_by_staff_id`).

**Verdict: 2NF satisfied.**

---

## 3NF — no transitive dependency on a non-key attribute

**Rule:** every non-key attribute depends on the key, the whole key, and nothing but the key (no
non-key attribute determines another non-key attribute).

### Justification, table by table

- **`menu_item`** stores `category_id` (FK), never `category.name` or `category.description`.
  Those live in `category` keyed by `category_id`. Storing a category name in `menu_item` would
  create `item_id → category_id → category_name`, a transitive dependency. Instead a rename of a
  category updates exactly one `category` row. (This is also why "no repeating groups across rows"
  is honoured — category text is not retyped per dish.)
- **`order_status_log`** stores `changed_by_staff_id` (FK) and the two status codes, never the
  staff's `full_name` or `role` — those remain in `staff`. Storing them would make
  `log_id → changed_by_staff_id → full_name` transitive.
- **`orders`** stores `session_id` (FK), never `table_id`, `table_number`, `qr_token` or session
  timings — those belong to `dining_session` / `restaurant_table`. Storing `table_number` on
  `orders` would yield `order_id → session_id → table_id → table_number`.
- **`dining_session`** stores `table_id`, not duplicated table attributes (`capacity`, `qr_token`).
- **`payment`** stores `session_id` and `order_id` (FKs), not the session/order amounts other than
  its own `amount`.
- **`orders`** does store `status`, but the *history* of statuses is not stored on `orders` (only
  the current value); history is a separate relation, `order_status_log`. There is no dependency
  among `orders`' own non-key columns (`special_notes`, `created_at`, `updated_at` are mutually
  independent, keyed fully by `order_id`).

### The two deliberate exceptions (derived / snapshot values)

These are discussed in detail in the next section; both are handled so that they do **not** create
an update anomaly:

1. `order_item.subtotal` — a `STORED GENERATED` column (`quantity * unit_price`). Strictly a
   derived value, but the engine owns it; it is not independently writable, so it cannot drift.
2. `orders.total_amount` — a cached aggregate over a *different* relation (`order_item`), not a
   non-key column of `orders` determining another. It is a snapshot of an aggregate, not a
   transitive dependency inside `orders`.

**Verdict: 3NF satisfied** (no partial dependencies on the composite candidate key, no transitive
dependencies among non-key attributes of a table).

---

## BCNF

**Rule:** for every non-trivial functional dependency `X → Y`, `X` must be a superkey.

In practice each table's determinants are its keys and unique constraints, and every one of those
is a **candidate key**:

| Table | Candidate keys (determinants) |
|---|---|
| `category` | `category_id` (PK), `name` (`uq_category_name`) |
| `menu_item` | `item_id` (PK), `name` (`uq_menu_item_name`) |
| `staff` | `staff_id` (PK), `username` (`uq_staff_username`) |
| `restaurant_table` | `table_id` (PK), `table_number` (`uq_restaurant_table_number`), `qr_token` (`uq_restaurant_table_qr_token`) |
| `dining_session` | `session_id` (PK), `session_token` (`uq_dining_session_token`) |
| `orders` | `order_id` (PK) |
| `order_item` | `order_item_id` (PK), `(order_id, item_id)` (`uq_order_item_unique`) |
| `payment` | `payment_id` (PK), `transaction_ref` (`uq_payment_transaction_ref`, nullable) |
| `order_status_log` | `log_id` (PK) |

Every determinant is a candidate key, so **BCNF is met**. (The generated `subtotal` and the cached
`total_amount` are derived/snapshot values handled below and do not introduce a non-superkey
determinant; `transaction_ref` is unique but nullable, and MySQL/MariaDB permit multiple NULLs,
which does not affect the BCNF analysis of the non-null values.)

---

## Deliberate denormalizations

### 1. `order_item.unit_price` — an immutable price *snapshot*

`order_item.unit_price DECIMAL(10,2) NOT NULL`, `CHECK (unit_price > 0.00)`. This looks like it
"duplicates" `menu_item.price`, but it is **not** a redundant copy and does **not** violate 3NF,
because it is a *historical fact*, not a derived value:

- The functional dependency `menu_item.price → order_item.unit_price` does **not** hold over time.
  The seed data proves the divergence: `Himalayan Spring Water` (`item_id 29`) has
  `menu_item.price = 45.00` but was sold on order 1 at `unit_price = 50.00`; `Tandoori Roti`
  (`item_id 14`) has `menu_item.price = 35.00` but order 3 frozen it at `20.00`; `Gulab Jamun`
  (`item_id 21`) is priced `130.00` on the menu but order 9 froze `75.00`.
- Because the two relations disagree, storing the snapshot is storing an *independent* fact of the
  `order_item` key, not copying `menu_item`. There is no transitive dependency to remove.
- **Trade-off:** a small amount of duplicated-looking storage in exchange for immutable bills —
  a menu price change never rewrites past revenue, and the project rule "never trust prices from
  the client" (`CLAUDE.md` Rule 2) is satisfied by reading the price from the DB at order time.

`order_item.subtotal` complements this: it is `GENERATED ALWAYS AS (quantity * unit_price) STORED`.
It is a derived value, but because it is stored generated (not user-writable) it cannot become
inconsistent, so it introduces no update anomaly.

### 2. `orders.total_amount` — a cached aggregate (verified, not invented)

**What the schema actually does:** the schema stores the total. `orders.total_amount` is
`DECIMAL(10,2) NOT NULL DEFAULT 0.00` with `CHECK (total_amount >= 0.00)` — it is a real physical
column, **not** a generated column and **not** computed on read. It is written by the application
transaction; `db/advanced.sql` DEMO 1 shows the exact pattern:

```sql
INSERT INTO orders (session_id, status, total_amount, special_notes) VALUES (1, 'PLACED', 0.00, ...);
INSERT INTO order_item (...) VALUES (...);            -- line items
UPDATE orders SET total_amount = fn_order_total(id) WHERE order_id = id;   -- cached total
COMMIT;
```

So the truth is: this is a **cached/derived aggregate maintained by the app** (within the single
order-placement transaction required by Rule 4), not a value computed by the schema.

- It is `SUM(order_item.subtotal)` for the order — an aggregate over a **different relation**, so
  it is not a functional dependency *within* `orders` and therefore does not break 3NF. No non-key
  attribute of `orders` determines another.
- **Trade-off:** fast reads (kitchen/sales screens avoid re-aggregating line items) and a stable
  header, at the cost of a possible drift if two writers disagree.
- **Mitigation:** the audit view `v_order_totals` recomputes `SUM(oi.subtotal)` and emits
  `total_audit_status = 'MATCH' | 'MISMATCH'`, and `fn_order_total(order_id)` centralizes the
  calculation so Java repositories, views and batch jobs compute the same number. This is the
  "double-entry" check referenced in `advanced.sql`.

### Note on the Java model vs the stored schema (honest divergence)

The Java entities in `src/main/java/com/restaurant/model/` are *richer* than the tables in a few
places; this is in-memory projection, not storage denormalization:

- `OrderItem` carries `menuItemName`, `itemType`, `spiceLevel`, `volumeMl` "denormalised snapshot"
  fields (see its Javadoc). The **DB `order_item` table does not store these** — it stores only
  `item_id` and joins `menu_item` to build the view (e.g. `v_kitchen_active_orders`). The
  snapshot lives in the application object, not in the row.
- `Payment` exposes `subtotal`, `taxAmount`, `discountAmount`, `totalAmount`, whereas the DB
  `payment` table stores only a single `amount` plus `payment_method` / `payment_status`. The
  richer breakdown is an application-side computation (tax is applied in `sp_generate_bill` at 5%
  GST), not extra columns.

---

## Views are read-only projections, not storage denormalization

`v_kitchen_active_orders` and `v_order_totals` (`db/advanced.sql`, `CREATE OR REPLACE VIEW`) are
**query-time projections**. They persist no rows: the first flattens a 5-table join
(`orders ⋈ dining_session ⋈ restaurant_table ⋈ order_item ⋈ menu_item`) filtered to in-flight
tickets and adds a derived `elapsed_minutes`; the second aggregates `order_item.subtotal` per
order and compares it to `orders.total_amount`. Because nothing is physically stored, they add
zero redundancy to the normalized schema — they are conveniences over the 3NF/BCNF base tables,
not denormalized copies of them.

---

## Worked example — seed order 3, distributed across tables

Using real rows from `db/seed.sql` (order 3, session 3, table T03):

| Table | Row(s) | Key / relationship |
|---|---|---|
| `restaurant_table` | `(3, 'T03', capacity=4, qr_token='tok_tbl_03_c7d6c5b4a3210987', is_active=TRUE)` | referenced by session 3 |
| `dining_session` | `(3, table_id=3, session_token='sess_03_9c5d4e3f217a9f8b', status='ACTIVE', end_time=NULL)` | referenced by order 3 |
| `orders` | `(3, session_id=3, status='ACCEPTED', total_amount=740.00, special_notes='Table celebrating an anniversary')` | header |
| `order_item` | `(3, 7,  qty=1, unit_price=340.00, subtotal=340.00)`<br>`(3, 16, qty=1, unit_price=380.00, subtotal=380.00)`<br>`(3, 14, qty=1, unit_price=20.00,  subtotal=20.00)`  | 3 atomic line rows (`subtotal` is generated) |
| `order_status_log` | `(order 3, NULL→'PLACED')` and `(order 3, 'PLACED'→'ACCEPTED', changed_by_staff_id=1)` | 2 audit rows |
| `payment` | *(none)* | session 3 is still ACTIVE |
| `menu_item` | item 7 `Paneer Butter Masala` (price 340.00, cat 2), item 16 `Chicken Dum Biryani` (380.00, cat 3), item 14 `Tandoori Roti` (35.00, cat 3) | referenced by `order_item.item_id` |
| `category` | cat 2 `Main Course`, cat 3 `Breads & Rice` | referenced by `menu_item.category_id` |

Observations:

- **No repeating groups.** The three dishes are three `order_item` rows, not a multi-valued cell.
  The same dish can appear in many orders without any change to `menu_item`.
- **No redundancy.** The dish's *current* name and price exist once (`menu_item`); the category
  text exists once (`category`). The order line holds only what is unique to that sale
  (`quantity`, `unit_price` snapshot, `item_notes`).
- **Consistency check.** `340.00 + 380.00 + 20.00 = 740.00`, which equals the cached
  `orders.total_amount = 740.00`; `v_order_totals` would report `total_audit_status = 'MATCH'`.
- **Snapshot visible.** `order_item.item_id = 14` sold at `unit_price = 20.00` while
  `menu_item.item_id = 14` now carries `price = 35.00` — the snapshot is a historical fact, not a
  copy.

---

## Summary

| Normal form | Status | Note |
|---|---|---|
| 1NF | ✅ Satisfied | Line items extracted to `order_item`; all columns atomic. |
| 2NF | ✅ Satisfied | Single-column surrogate PKs; the sole composite key is a unique constraint, not a PK. |
| 3NF | ✅ Satisfied | FKs (`category_id`, `table_id`, `session_id`, `item_id`, `changed_by_staff_id`) keep foreign attributes in their home tables; no transitive dependencies. |
| BCNF | ✅ Met | Every determinant (`PK` and each `uq_*` unique column) is a candidate key. |
| Denormalization | Intentional, 3NF-safe | `order_item.unit_price` (immutable snapshot), `order_item.subtotal` (stored generated), `orders.total_amount` (cached aggregate, audited by `v_order_totals`). |
| Views | Read-only | `v_kitchen_active_orders`, `v_order_totals` store nothing. |
