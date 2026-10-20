# SQL Query Reference — Restaurant Table-Ordering System

This document catalogues every SQL statement the application executes, grouped by
the business capability it serves. Each entry lists the **purpose** (and the Java
class + REST route that triggers it), the **exact SQL** (with `?` bind placeholders
as written in the code), a **genuine sample output** captured from the live
`restaurant_db`, and a short note on why the statement is safe and fast.

## How the application talks to the database

* **Least-privilege account.** The service connects as `restaurant_app`, which holds
  only the privileges the application needs on `restaurant_db`:

  ```text
  GRANT SELECT, INSERT, UPDATE, DELETE, EXECUTE, SHOW VIEW ON `restaurant_db`.* TO `restaurant_app`@`localhost`
  ```

  There is **no DDL** grant (`CREATE`/`DROP`/`ALTER` are absent), so the running app
  cannot change or drop the schema at runtime; `EXECUTE` is required for the
  `fn_order_total` function and `sp_generate_bill` procedure.

* **Prepared statements only.** Every statement is issued through Spring JDBC
  (`JdbcTemplate` / `PreparedStatement`) with `?` placeholders. No SQL is ever built
  by concatenating user input, so parameter values cannot break out of their bind
  slot (SQL-injection safe) and the server can cache one execution plan per statement.
  The only dynamically assembled SQL is the *optional* date predicate in the report
  repository — and even there the values are still bound as parameters.

* **Environment.** MySQL-8-compatible schema running on MariaDB 13, database
  `restaurant_db` (10 tables, 29 menu items, 10 tables/QR tokens, 12 seed orders +
  app-generated rows). All samples below were produced with
  `mysql --table` against the live database.

---

## 1. Menu Retrieval

### 1.1 Customer menu grouped by category

* **Purpose:** Builds the whole customer-facing menu in **one** round trip: active
  categories, each with its available, non-deleted items, so a category with no
  items still appears. Called by `MenuService.getGroupedMenu()` →
  `JdbcMenuItemRepository.findMenuGroupedByCategory()`, served by
  `MenuController` at **`GET /api/menu`** (and reused by the admin catalog).
* **SQL:**

  ```sql
  SELECT c.category_id, c.name AS category_name, c.description AS category_description,
         c.display_order, c.is_active,
         mi.item_id, mi.name AS item_name, mi.description AS item_description,
         mi.price AS item_price, mi.item_type AS item_type,
         mi.is_available AS item_available, mi.spice_level AS item_spice_level,
         mi.volume_ml AS item_volume_ml
  FROM category c
  LEFT JOIN menu_item mi ON mi.category_id = c.category_id
       AND mi.is_available = TRUE AND mi.is_deleted = FALSE
  WHERE c.is_active = TRUE
  ORDER BY c.display_order, c.name, mi.name
  ```

* **Sample Output** (29 rows, one per available item across 6 categories):

  ```text
  +-------------+-------------------------+----------------------------------------------+---------------+-----------+---------+-------------------------------+------------+-----------+----------------+
  | category_id | category_name           | category_description                         | display_order | is_active | item_id | item_name                     | item_price | item_type | item_spice_level |
  +-------------+-------------------------+----------------------------------------------+---------------+-----------+---------+-------------------------------+------------+-----------+----------------+
  |           1 | Starters & Appetizers   | Tandoori, crispy grills and delightful apps. |             1 |         1 |       2 | Chicken Malai Tikka           |     340.00 | FOOD      | MILD             |
  |           1 | Starters & Appetizers   | Tandoori, crispy grills and delightful apps. |             1 |         1 |       3 | Crispy Corn Pepper Fry        |     220.00 | FOOD      | MEDIUM           |
  |           1 | Starters & Appetizers   | Tandoori, crispy grills and delightful apps. |             1 |         1 |       5 | Lamb Seekh Kebab              |     420.00 | FOOD      | EXTRA_HOT        |
  |           1 | Starters & Appetizers   | Tandoori, crispy grills and delightful apps. |             1 |         1 |       1 | Paneer Tikka                  |     280.00 | FOOD      | HOT              |
  |           1 | Starters & Appetizers   | Tandoori, crispy grills and delightful apps. |             1 |         1 |       4 | Tandoori Stuffed Mushrooms    |     260.00 | FOOD      | MEDIUM           |
  |           2 | Main Course             | Rich aromatic curries and chef specials      |             2 |         1 |       6 | Butter Chicken                |     420.00 | FOOD      | MILD             |
  |           2 | Main Course             | Rich aromatic curries and chef specials      |             2 |         1 |      12 | Chettinad Veg Curry           |     310.00 | FOOD      | EXTRA_HOT        |
  |           2 | Main Course             | Rich aromatic curries and chef specials      |             2 |         1 |       8 | Dal Makhani                   |     290.00 | FOOD      | MILD             |
  |           2 | Main Course             | Rich aromatic curries and chef specials      |             2 |         1 |       9 | Kadai Chicken                 |     410.00 | FOOD      | HOT              |
  |           2 | Main Course             | Rich aromatic curries and chef specials      |             2 |         1 |      10 | Kashmiri Rogan Josh           |     480.00 | FOOD      | HOT              |
  |           2 | Main Course             | Rich aromatic curries and chef specials      |             2 |         1 |      11 | Malai Kofta                   |     350.00 | FOOD      | MILD             |
  |           2 | Main Course             | Rich aromatic curries and chef specials      |             2 |         1 |       7 | Paneer Butter Masala          |     340.00 | FOOD      | MILD             |
  |           3 | Breads & Rice           | Freshly baked breads, rotis and biryanis     |             3 |         1 |      16 | Chicken Dum Biryani           |     380.00 | FOOD      | MEDIUM           |
  |           3 | Breads & Rice           | Freshly baked breads, rotis and biryanis     |             3 |         1 |      13 | Garlic Butter Naan            |      65.00 | FOOD      | NULL             |
  |           3 | Breads & Rice           | Freshly baked breads, rotis and biryanis     |             3 |         1 |      17 | Hyderabadi Veg Biryani        |     310.00 | FOOD      | MEDIUM           |
  |           3 | Breads & Rice           | Freshly baked breads, rotis and biryanis     |             3 |         1 |      15 | Laccha Paratha                |      55.00 | FOOD      | NULL             |
  |           3 | Breads & Rice           | Freshly baked breads, rotis and biryanis     |             3 |         1 |      18 | Steamed Basmati Rice          |     150.00 | FOOD      | NULL             |
  |           3 | Breads & Rice           | Freshly baked breads, rotis and biryanis     |             3 |         1 |      14 | Tandoori Roti                 |      35.00 | FOOD      | NULL             |
  |           4 | Accompaniments & Salads | Fresh salads, raitas and side relishes       |             4 |         1 |      19 | Boondi Raita                  |      90.00 | FOOD      | MILD             |
  |           4 | Accompaniments & Salads | Fresh salads, raitas and side relishes       |             4 |         1 |      20 | Fresh Garden Salad            |     110.00 | FOOD      | NULL             |
  |           5 | Desserts                | Traditional sweets and cold artisan delights |             5 |         1 |      21 | Gulab Jamun with Rabdi        |     130.00 | DESSERT   | NULL             |
  |           5 | Desserts                | Traditional sweets and cold artisan delights |             5 |         1 |      22 | Kesar Pista Rasmalai          |     150.00 | DESSERT   | NULL             |
  |           5 | Desserts                | Traditional sweets and cold artisan delights |             5 |         1 |      24 | Royal Kulfi Falooda           |     140.00 | DESSERT   | NULL             |
  |           5 | Desserts                | Traditional sweets and cold artisan delights |             5 |         1 |      23 | Sizzling Brownie with Vanilla |     220.00 | DESSERT   | NULL             |
  |           6 | Beverages & Mocktails   | Refreshing cold sips, hot teas and mocktails |             6 |         1 |      27 | Adrak Masala Chai             |      60.00 | BEVERAGE  | NULL             |
  |           6 | Beverages & Mocktails   | Refreshing cold sips, hot teas and mocktails |             6 |         1 |      25 | Alphonso Mango Lassi          |     130.00 | BEVERAGE  | NULL             |
  |           6 | Beverages & Mocktails   | Refreshing cold sips, hot teas and mocktails |             6 |         1 |      26 | Fresh Mint Lime Soda          |      90.00 | BEVERAGE  | NULL             |
  |           6 | Beverages & Mocktails   | Refreshing cold sips, hot teas and mocktails |             6 |         1 |      29 | Himalayan Spring Water        |      45.00 | BEVERAGE  | NULL             |
  |           6 | Beverages & Mocktails   | Refreshing cold sips, hot teas and mocktails |             6 |         1 |      28 | Virgin Green Apple Mojito     |     160.00 | BEVERAGE  | NULL             |
  +-------------+-------------------------+----------------------------------------------+---------------+-----------+---------+-------------------------------+------------+-----------+----------------+
  ```

* **Why it is safe/fast:** Prepared statement with no binds; the `LEFT JOIN`'s
  non-null item predicate uses `idx_menu_item_category_id` and is filtered by the
  `category.is_active` primary-key scan, so the result is built in one pass with no
  per-category N+1 query.

### 1.2 Filter by category

* **Purpose:** Returns only the available items of a single category; used by
  `MenuService.getGroupedMenu(Long categoryId, String itemType)` (the in-memory
  `categoryId` filter still maps to this repository method) via
  `JdbcMenuItemRepository.findByCategory()`, exposed through **`GET /api/menu?categoryId=…`**.
* **SQL:**

  ```sql
  SELECT item_id, category_id, name, description, price, item_type,
         is_available, spice_level, volume_ml
  FROM menu_item
  WHERE category_id = ? AND is_available = TRUE AND is_deleted = FALSE
  ORDER BY name
  ```

* **Sample Output** (bound `category_id = 6`, 5 rows):

  ```text
  +---------+-------------+---------------------------+----------------------------------------------------------------+--------+-----------+--------------+-------------+-----------+
  | item_id | category_id | name                      | description                                                    | price  | item_type | is_available | spice_level | volume_ml |
  +---------+-------------+---------------------------+----------------------------------------------------------------+--------+-----------+--------------+-------------+-----------+
  |      27 |           6 | Adrak Masala Chai         | Robust Assam CTC black tea brewed with fresh crushed ginger... |  60.00 | BEVERAGE  |            1 | NULL        |       200 |
  |      25 |           6 | Alphonso Mango Lassi      | Sweet churned yogurt smoothie blended with ripe Ratnagiri...   | 130.00 | BEVERAGE  |            1 | NULL        |       350 |
  |      26 |           6 | Fresh Mint Lime Soda      | Squeezed Persian limes with garden mint leaves and chilled...  |  90.00 | BEVERAGE  |            1 | NULL        |       300 |
  |      29 |           6 | Himalayan Spring Water    | Pure bottled mineral water from the Himalayan foothills        |  45.00 | BEVERAGE  |            1 | NULL        |      1000 |
  |      28 |           6 | Virgin Green Apple Mojito | Crushed granny smith apples, mint sprigs, lime wedges and...   | 160.00 | BEVERAGE  |            1 | NULL        |       350 |
  +---------+-------------+---------------------------+----------------------------------------------------------------+--------+-----------+--------------+-------------+-----------+
  ```

* **Why it is safe/fast:** Prepared statement with a bound `category_id`; the
  predicate is served by the `idx_menu_item_category_id` B-tree index, so only the
  matching category slice is read.

### 1.3 Filter by item type (FOOD / BEVERAGE / DESSERT)

* **Purpose:** Narrows the menu to a single `item_type`. **Note:** in this codebase
  the type filter is applied **in memory** by `MenuService.getGroupedMenu()` on the
  already-grouped result (one DB call, no N+1) — there is **no** SQL `WHERE item_type = ?`
  in the code. The query below is the equivalent run directly against the database
  for reference, driven by **`GET /api/menu?itemType=DESSERT`**.
* **SQL (equivalent, not present as a literal in the code):**

  ```sql
  SELECT item_id, category_id, name, description, price, item_type,
         is_available, spice_level, volume_ml
  FROM menu_item
  WHERE is_available = TRUE AND is_deleted = FALSE AND item_type = ?
  ORDER BY name
  ```

* **Sample Output** (bound `item_type = 'DESSERT'`, 4 rows):

  ```text
  +---------+-------------+-------------------------------+--------+-----------+--------------+-------------+-----------+
  | item_id | category_id | name                          | price  | item_type | is_available | spice_level | volume_ml |
  +---------+-------------+-------------------------------+--------+-----------+--------------+-------------+-----------+
  |      21 |           5 | Gulab Jamun with Rabdi        | 130.00 | DESSERT   |            1 | NULL        |      NULL |
  |      22 |           5 | Kesar Pista Rasmalai          | 150.00 | DESSERT   |            1 | NULL        |      NULL |
  |      24 |           5 | Royal Kulfi Falooda           | 140.00 | DESSERT   |            1 | NULL        |      NULL |
  |      23 |           5 | Sizzling Brownie with Vanilla | 220.00 | DESSERT   |            1 | NULL        |      NULL |
  +---------+-------------+-------------------------------+--------+-----------+--------------+-------------+-----------+
  ```

* **Why it is safe/fast:** Prepared statement with a bound `item_type`. There is no
  index on `item_type`, so this form scans the small `menu_item` table (29 rows);
  the running application avoids even that by filtering the cached grouped result
  in Java.

---

## 2. Order Placement (Transactional)

Placing an order is **one** database transaction (`OrderService.placeOrder()` is
`@Transactional`, `JdbcOrderRepository.save()` is `@Transactional`): the `orders`
header and all `order_item` rows commit or roll back together. The header insert and
the line inserts use the prepared statements below; `unit_price` is a **snapshot**
read from `menu_item` by the service (Rule 2 — never trust a client-supplied price).
Triggered by **`POST /api/orders`** (`OrderController.placeOrder`).

> The write samples in this section were executed inside a `START TRANSACTION … ROLLBACK`
> block so the sample is **genuine** while leaving the database unchanged.

### 2.1 Insert order header

* **Purpose:** Creates the order header row (status `PLACED`, cached
  `total_amount` = sum of line totals, optional `special_notes`) and returns the
  generated key. `JdbcOrderRepository.save()` → **`POST /api/orders`**.
* **SQL:**

  ```sql
  INSERT INTO orders (session_id, status, total_amount, special_notes) VALUES (?, ?, ?, ?)
  ```

* **Sample Output** (executed, then rolled back):

  ```text
  +----------+------------+--------+--------------+------------------------------+
  | order_id | session_id | status | total_amount | special_notes                |
  +----------+------------+--------+--------------+------------------------------+
  |       17 |          1 | PLACED |       345.00 | Docs demo only - rolled back |
  +----------+------------+--------+--------------+------------------------------+
  ```

* **Why it is safe/fast:** Prepared statement with four bound parameters; a single
  primary-key insert, protected by the `fk_orders_session` FK and
  `chk_orders_total_amount >= 0`.

### 2.2 Insert order line items (batch, unit_price snapshot)

* **Purpose:** Inserts each cart line with the `unit_price` snapshot captured at
  order time; `subtotal` is a stored generated column
  (`quantity * unit_price`). `JdbcOrderRepository.save()` loops this prepared
  statement once per line — **`POST /api/orders`**.
* **SQL:**

  ```sql
  INSERT INTO order_item (order_id, item_id, quantity, unit_price, item_notes)
  VALUES (?, ?, ?, ?, ?)
  ```

* **Sample Output** (two lines inserted into the demo header, then rolled back):

  ```text
  +---------------+----------+---------+----------+------------+----------+--------------+
  | order_item_id | order_id | item_id | quantity | unit_price | subtotal | item_notes   |
  +---------------+----------+---------+----------+------------+----------+--------------+
  |            46 |       17 |       1 |        1 |     280.00 |   280.00 | Paneer Tikka |
  |            47 |       17 |      13 |        1 |      65.00 |    65.00 | Garlic Naan  |
  +---------------+----------+---------+----------+------------+----------+--------------+
  ```

* **Why it is safe/fast:** Prepared statement with five bound parameters; the frozen
  `unit_price` is DB-sourced and guarded by `chk_order_item_unit_price > 0` and
  `uq_order_item_unique (order_id, item_id)`, and `subtotal` can never drift from
  `quantity * unit_price`.

### 2.3 Read-back: order header

* **Purpose:** Reloads the freshly written order header immediately after the two
  inserts so the response reflects exactly what the database stored.
  `JdbcOrderRepository.findById()` → **`POST /api/orders`** / **`GET /api/orders/{id}`**.
* **SQL:**

  ```sql
  SELECT o.order_id, o.session_id, o.status, o.special_notes, o.created_at, o.updated_at
  FROM orders o WHERE o.order_id = ?
  ```

* **Sample Output** (bound `order_id = 1`):

  ```text
  +----------+------------+--------+----------------------------------------+---------------------+---------------------+
  | order_id | session_id | status | special_notes                          | created_at          | updated_at          |
  +----------+------------+--------+----------------------------------------+---------------------+---------------------+
  |        1 |          7 | PLACED | Please serve the soup extra piping hot | 2026-10-19 03:57:20 | 2026-10-19 03:57:20 |
  +----------+------------+--------+----------------------------------------+---------------------+---------------------+
  ```

* **Why it is safe/fast:** Prepared statement with a bound `order_id`; a single-row
  lookup on the `orders` primary key.

### 2.4 Read-back: order line items

* **Purpose:** Loads the joined line items (dish name, type, attributes, snapshot
  price and quantity) for the order. `JdbcOrderRepository.loadItems()` →
  `findById()` / `findAll()` / `findBySessionId` → **`GET /api/orders/{id}`**.
* **SQL:**

  ```sql
  SELECT oi.order_item_id, oi.order_id, oi.item_id, mi.name, mi.item_type,
         mi.spice_level, mi.volume_ml, oi.unit_price, oi.quantity, oi.item_notes
  FROM order_item oi JOIN menu_item mi ON oi.item_id = mi.item_id WHERE oi.order_id = ?
  ```

* **Sample Output** (bound `order_id = 1`, 4 lines):

  ```text
  +---------------+----------+---------+------------------------+-----------+-------------+-----------+------------+----------+------------------------+
  | order_item_id | order_id | item_id | name                   | item_type | spice_level | volume_ml | unit_price | quantity | item_notes             |
  +---------------+----------+---------+------------------------+-----------+-------------+-----------+------------+----------+------------------------+
  |             1 |        1 |       1 | Paneer Tikka           | FOOD      | HOT         |      NULL |     280.00 |        1 | Medium spicy marinade  |
  |             2 |        1 |      13 | Garlic Butter Naan     | FOOD      | NULL        |      NULL |      65.00 |        1 | Crispy butter naan     |
  |             3 |        1 |      26 | Fresh Mint Lime Soda   | BEVERAGE  | NULL        |       300 |      90.00 |        1 | Sweet and salted blend |
  |             4 |        1 |      29 | Himalayan Spring Water | BEVERAGE  | NULL        |      1000 |      50.00 |        1 | Chilled bottle         |
  +---------------+----------+---------+------------------------+-----------+-------------+-----------+------------+----------+------------------------+
  ```

* **Why it is safe/fast:** Prepared statement with a bound `order_id`; the line
  lookup uses `idx_order_item_order_id` and the `menu_item` join uses the item
  primary key.

---

## 3. Kitchen Board

### 3.1 Live kitchen queue (view-backed)

* **Purpose:** The kitchen display polls every 3 s for in-flight tickets
  (`PLACED`/`ACCEPTED`/`PREPARING`/`READY`) with per-dish detail and elapsed wait
  minutes. `KitchenService.getActiveTickets()` →
  `JdbcOrderRepository.findActiveForKitchen()` → **`GET /api/kitchen/orders`**
  (`KitchenController.getActiveOrders`; optional `?status=` is filtered in memory).
* **SQL:**

  ```sql
  SELECT v.order_id, v.table_number, v.order_status, v.order_placed_at, v.elapsed_minutes,
         v.order_item_id, v.item_name, v.item_type, v.spice_level, v.volume_ml,
         v.quantity, v.item_notes, v.order_special_notes, oi.item_id, oi.unit_price
  FROM v_kitchen_active_orders v
  JOIN order_item oi ON oi.order_item_id = v.order_item_id
  ORDER BY v.order_placed_at ASC, v.order_id ASC, v.order_item_id ASC
  ```

* **Sample Output** (26 rows; first rows shown):

  ```text
  +----------+--------------+--------------+---------------------+-----------------+---------------+----------------------------+-----------+----------+-----------+
  | order_id | table_number | order_status | order_placed_at     | elapsed_minutes | order_item_id | item_name                  | item_type | quantity | unit_price |
  +----------+--------------+--------------+---------------------+-----------------+---------------+----------------------------+-----------+----------+-----------+
  |        7 | T01          | READY        | 2026-10-19 03:20:20 |              51 |            21 | Tandoori Stuffed Mushrooms | FOOD      |        1 |     260.00 |
  |        7 | T01          | READY        | 2026-10-19 03:20:20 |              51 |            22 | Hyderabadi Veg Biryani     | FOOD      |        1 |     310.00 |
  |        7 | T01          | READY        | 2026-10-19 03:20:20 |              51 |            23 | Boondi Raita               | FOOD      |        1 |      95.00 |
  |        6 | T02          | PREPARING    | 2026-10-19 03:30:20 |              41 |            17 | Butter Chicken             | FOOD      |        1 |     420.00 |
  |        6 | T02          | PREPARING    | 2026-10-19 03:30:20 |              41 |            18 | Dal Makhani                | FOOD      |        1 |     290.00 |
  |        5 | T03          | PREPARING    | 2026-10-19 03:36:20 |              35 |            13 | Kashmiri Rogan Josh        | FOOD      |        1 |     480.00 |
  |        1 | T07          | PLACED       | 2026-10-19 03:57:20 |              14 |             1 | Paneer Tikka               | FOOD      |        1 |     280.00 |
  |        2 | T06          | PLACED       | 2026-10-19 03:58:20 |              13 |             5 | Crispy Corn Pepper Fry     | FOOD      |        1 |     220.00 |
  |       13 | T01          | PLACED       | 2026-10-19 04:00:20 |              11 |            38 | Paneer Tikka               | FOOD      |        1 |     280.00 |
  |       13 | T01          | PLACED       | 2026-10-19 04:00:20 |              11 |            39 | Alphonso Mango Lassi       | BEVERAGE  |        2 |     130.00 |
  +----------+--------------+--------------+---------------------+-----------------+---------------+----------------------------+-----------+----------+-----------+
  ```

* **Why it is safe/fast:** Read-only prepared statement. The `v_kitchen_active_orders`
  view filters orders by status (backed by the composite `idx_orders_kitchen
  (status, created_at)`) and joins sessions/tables/items by their FK indexes, so only
  active tickets and their dishes are materialised.

### 3.2 Optimistic-concurrency status update

* **Purpose:** Moves a ticket along the state machine (`PLACED → ACCEPTED → PREPARING
  → READY → SERVED`, or `→ CANCELLED`) but **only** if the row still holds the
  caller's expected `from` status, so two chefs clicking at once cannot both win.
  Runs the `SET @current_staff_id = ?` session variable and this `UPDATE` on the same
  connection so the audit trigger records the acting staff id.
  `KitchenService.updateStatus()` → `JdbcOrderRepository.updateStatus()` →
  **`PATCH /api/orders/{id}/status`**.
* **SQL:**

  ```sql
  UPDATE orders SET status = ? WHERE order_id = ? AND status = ?
  ```

* **Sample Output** (executed in a transaction, then rolled back; staff id 1):

  ```text
  -- expected status still current: 1 row changed
  +-------------------------+
  | rows_changed_expected_1 |
  +-------------------------+
  |                       1 |
  +-------------------------+
  +----------+------------+----------+
  | order_id | session_id | status   |
  +----------+------------+----------+
  |        1 |          7 | ACCEPTED |
  +----------+------------+----------+

  -- stale expected status (row already moved): 0 rows changed -> HTTP 409
  +----------------------+
  | rows_changed_stale_0 |
  +----------------------+
  |                    0 |
  +----------------------+
  ```

* **Why it is safe/fast:** Prepared statement with three bound parameters; the
  `order_id` predicate is the `orders` primary key, so the guard is a single-row
  read-modify-write. A return value of 0 is surfaced by the service as a conflict
  (409), and the `BEFORE UPDATE` trigger rejects any illegal transition.

---

## 4. Table & Session

### 4.1 Resolve table by QR token

* **Purpose:** Turns a scanned QR token into the concrete table (and validates it is
  active) before any ordering is allowed. `TableService.getTableByToken()` /
  `OrderService.placeOrder()` → `JdbcTableRepository.findByToken()` →
  **`GET /api/tables/{token}`** and **`POST /api/orders`**.
* **SQL:**

  ```sql
  SELECT table_id, table_number, qr_token, capacity, is_active, created_at
  FROM restaurant_table WHERE qr_token = ?
  ```

* **Sample Output** (bound token `tok_tbl_01_a9f8e7d6c5b4a321`):

  ```text
  +----------+--------------+-----------------------------+----------+-----------+---------------------+
  | table_id | table_number | qr_token                    | capacity | is_active | created_at          |
  +----------+--------------+-----------------------------+----------+-----------+---------------------+
  |        1 | T01          | tok_tbl_01_a9f8e7d6c5b4a321 |        2 |         1 | 2026-10-19 04:00:20 |
  +----------+--------------+-----------------------------+----------+-----------+---------------------+
  ```

* **Why it is safe/fast:** Prepared statement with a bound token; the lookup is a
  unique-index hit on `uq_restaurant_table_qr_token`, i.e. at most one row.

### 4.2 Find the open ACTIVE dining session

* **Purpose:** Reuses the guest's current sitting so incremental orders attach to the
  same `dining_session` (or signals that none exists). `TableService.getTableByToken()` /
  `OrderService.placeOrder()` / `getOrdersByTableToken()` / `BillingService` →
  `JdbcSessionRepository.findOpenSessionByTableId()` → **`GET /api/tables/{token}`**,
  **`PATCH /api/orders/{id}/status`**, **`POST /api/sessions/{id}/pay`**.
* **SQL:**

  ```sql
  SELECT session_id, table_id, session_token, status, start_time, end_time
  FROM dining_session WHERE table_id = ? AND status = 'ACTIVE' LIMIT 1
  ```

* **Sample Output** (bound `table_id = 1`):

  ```text
  +------------+----------+--------------------------+--------+---------------------+----------+
  | session_id | table_id | session_token            | status | start_time          | end_time |
  +------------+----------+--------------------------+--------+---------------------+----------+
  |          1 |        1 | sess_01_7a9f8b6c5d4e3f21 | ACTIVE | 2026-10-19 03:15:20 | NULL     |
  +------------+----------+--------------------------+--------+---------------------+----------+
  ```

* **Why it is safe/fast:** Prepared statement with a bound `table_id`; relies on
  `idx_dining_session_table_id` plus the `status` predicate and `LIMIT 1`, so at most
  one row is read per table.

### 4.3 Open a new dining session

* **Purpose:** Starts a fresh sitting when a table has no open session (a guest scans
  the QR and orders for the first time). The opaque `session_token` is generated in
  Java. `OrderService.placeOrder()` → `JdbcSessionRepository.save()` →
  **`POST /api/orders`**.
* **SQL:**

  ```sql
  INSERT INTO dining_session (table_id, session_token, status) VALUES (?, ?, ?)
  ```

* **Sample Output** (executed in a transaction, then rolled back):

  ```text
  +------------+----------+---------------------+--------+---------------------+----------+
  | session_id | table_id | session_token       | status | start_time          | end_time |
  +------------+----------+---------------------+--------+---------------------+----------+
  |         11 |        9 | sess_docs_demo_0001 | ACTIVE | 2026-10-19 04:11:39 | NULL     |
  +------------+----------+---------------------+--------+---------------------+----------+
  ```

* **Why it is safe/fast:** Prepared statement with three bound parameters; a
  primary-key insert whose `session_token` is protected by
  `uq_dining_session_token` and whose `table_id` is checked by
  `fk_dining_session_table`. The generated key is returned immediately.

### 4.4 Close a dining session

* **Purpose:** Transitions the session out of `ACTIVE`; when the new status is
  `COMPLETED`/`CANCELLED` the `end_time` is stamped with `NOW()`, otherwise it is left
  untouched. Called by `BillingService.processPayment()` →
  `JdbcSessionRepository.updateStatus()` → **`POST /api/sessions/{id}/pay`**.
* **SQL:**

  ```sql
  UPDATE dining_session SET status = ?,
         end_time = CASE WHEN ? IN ('COMPLETED','CANCELLED') THEN NOW() ELSE end_time END
  WHERE session_id = ?
  ```

* **Sample Output** (executed on the demo session, then rolled back):

  ```text
  +------------+----------+-----------+---------------------+
  | session_id | table_id | status    | end_time            |
  +------------+----------+-----------+---------------------+
  |         11 |        9 | COMPLETED | 2026-10-19 04:11:39 |
  +------------+----------+-----------+---------------------+
  ```

* **Why it is safe/fast:** Prepared statement with three bound parameters; the
  `session_id` predicate is the `dining_session` primary key, so it targets exactly
  one row and the `CASE` keeps `end_time` logic entirely inside the database.

---

## 5. Billing & Payments

### 5.1 Generate the bill (stored procedure)

* **Purpose:** Computes a session invoice — subtotal of non-cancelled orders, 5 % GST,
  grand total, table number and active order count — inside the database engine.
  `BillingService.generateBill()` / `processPayment()` → `JdbcOrderRepository.generateBill()`
  → **`POST /api/sessions/{id}/bill`** (and **`…/pay`**).
* **SQL:**

  ```sql
  CALL sp_generate_bill(?)
  ```

* **Sample Output** (bound `session_id = 1`):

  ```text
  +------------+--------------+----------------+--------------------+----------+----------------+------------+-------------+
  | session_id | table_number | session_status | active_order_count | subtotal | tax_percentage | tax_amount | grand_total |
  +------------+--------------+----------------+--------------------+----------+----------------+------------+-------------+
  |          1 | T01          | ACTIVE         |                  2 |  1205.00 |           5.00 |      60.25 |     1265.25 |
  +------------+--------------+----------------+--------------------+----------+----------------+------------+-------------+
  ```

* **Why it is safe/fast:** Prepared call with a bound `session_id`; the procedure's
  order aggregation is served by `idx_orders_session_id`, and centralising the tax
  maths guarantees one consistent 5 % GST rule everywhere.

### 5.2 Order total helper function

* **Purpose:** Returns the exact decimal sum of an order's line-item subtotals;
  used as the single source of truth for an order's total (e.g. by the ACID demo and
  views). `fn_order_total(order_id)` is called from SQL / the `advanced.sql`
  transaction demo rather than directly from Java.
* **SQL:**

  ```sql
  SELECT fn_order_total(?)
  ```

* **Sample Output** (bound orders 1, 5, 7):

  ```text
  +---------------+---------------+---------------+
  | order_1_total | order_5_total | order_7_total |
  +---------------+---------------+---------------+
  |        485.00 |        915.00 |        665.00 |
  +---------------+---------------+---------------+
  ```

* **Why it is safe/fast:** Deterministic `READS SQL DATA` function; its inner
  `SUM(subtotal) … WHERE order_id = ?` uses `idx_order_item_order_id`, so the total is
  computed from indexed line items only.

### 5.3 Billing line-item summaries (via `v_order_totals`)

* **Purpose:** Lists a session's orders with a SQL-computed line-item count for the
  invoice breakdown. `BillingService.generateBill()` / `OrderService.getOrdersByTableToken()`
  → `JdbcOrderRepository.findSummariesBySessionId()` → **`POST /api/sessions/{id}/bill`**,
  **`GET /api/tables/{token}/orders`**.
* **SQL:**

  ```sql
  SELECT o.order_id, o.session_id, o.status, o.total_amount, o.created_at,
         vt.line_item_count
  FROM orders o JOIN v_order_totals vt ON vt.order_id = o.order_id
  WHERE o.session_id = ? ORDER BY o.created_at
  ```

* **Sample Output** (bound `session_id = 1`):

  ```text
  +----------+------------+--------+--------------+---------------------+-----------------+
  | order_id | session_id | status | total_amount | created_at          | line_item_count |
  +----------+------------+--------+--------------+---------------------+-----------------+
  |        7 |          1 | READY  |       665.00 | 2026-10-19 03:20:20 |               3 |
  |       13 |          1 | PLACED |       540.00 | 2026-10-19 04:00:20 |               2 |
  +----------+------------+--------+--------------+---------------------+-----------------+
  ```

* **Why it is safe/fast:** Prepared statement with a bound `session_id`; the session
  filter uses `idx_orders_session_id` and the `v_order_totals` view aggregates each
  order's items through `idx_order_item_order_id`, so no per-order round trip is needed.

### 5.4 Record a payment

* **Purpose:** Inserts the settlement row (amount = grand total, method, status,
  transaction reference) for a session; `paid_at` is stamped by the next statement.
  `BillingService.processPayment()` → `JdbcPaymentRepository.save()` →
  **`POST /api/sessions/{id}/pay`**.
* **SQL:**

  ```sql
  INSERT INTO payment (session_id, amount, payment_method, payment_status, transaction_ref)
  VALUES (?, ?, ?, ?, ?)
  ```

* **Sample Output** (executed in a transaction, then rolled back):

  ```text
  +------------+------------+---------+----------------+----------------+-------------------+---------+---------------------+
  | payment_id | session_id | amount  | payment_method | payment_status | transaction_ref   | paid_at | created_at          |
  +------------+------------+---------+----------------+----------------+-------------------+---------+---------------------+
  |          5 |          1 | 1035.00 | CARD           | PENDING        | TXN_DOCS_DEMO_001 | NULL    | 2026-10-19 04:11:47 |
  +------------+------------+---------+----------------+----------------+-------------------+---------+---------------------+
  ```

* **Why it is safe/fast:** Prepared statement with five bound parameters; the amount
  is a server-side `BigDecimal` checked by `chk_payment_amount > 0`, and the
  `transaction_ref` is protected against replay by `uq_payment_transaction_ref`
  (a duplicate reference raises a `DataIntegrityViolationException` the service maps
  to HTTP 409).

### 5.5 Confirm / stamp the payment

* **Purpose:** Marks a just-inserted payment `COMPLETED`, persists the channel
  reference and stamps `paid_at = NOW()`. `BillingService.processPayment()` →
  `JdbcPaymentRepository.updateStatus()` → **`POST /api/sessions/{id}/pay`**.
* **SQL:**

  ```sql
  UPDATE payment SET payment_status = ?, transaction_ref = ?, paid_at = NOW()
  WHERE payment_id = ?
  ```

* **Sample Output** (the row from §5.4, after the update, then rolled back):

  ```text
  +------------+------------+---------+----------------+-------------------+---------------------+
  | payment_id | session_id | amount  | payment_status | transaction_ref   | paid_at             |
  +------------+------------+---------+----------------+-------------------+---------------------+
  |          5 |          1 | 1035.00 | COMPLETED      | TXN_DOCS_DEMO_001 | 2026-10-19 04:11:47 |
  +------------+------------+---------+----------------+-------------------+---------------------+
  ```

* **Why it is safe/fast:** Prepared statement with three bound parameters; the
  `payment_id` predicate is the `payment` primary key, so exactly one row is touched,
  and the whole settle (payment + session close) commits as one transaction.

### 5.6 Close the session as part of settlement

The settlement flow ends by closing the dining session with the **same** statement
documented in **§4.4** (`UPDATE dining_session SET status = ?, end_time = CASE … WHERE
session_id = ?`), executed with `status = 'COMPLETED'`. In `BillingService.processPayment()`
the payment row (§5.4/§5.5) and this session close (§4.4) run inside **one**
`@Transactional` unit, so a session can never be closed without a matching payment
record. See §4.4 for the SQL, sample output and indexing notes.

---

## 6. Reports

All four reports are read-only aggregates served by `AdminReportController` under
**`/api/admin/reports/*`** (MANAGER-only), backed by `JdbcReportRepository`.

### 6.1 Daily sales aggregation

* **Purpose:** Per-calendar-day revenue, ticket counts and average ticket size, with
  an optional date range. `ReportService.dailySales(startDate, endDate)` →
  `JdbcReportRepository.dailySales()` → **`GET /api/admin/reports/daily-sales`**.
* **SQL** (optional date predicates are appended and bound only when supplied):

  ```sql
  SELECT DATE(o.created_at) AS sales_date,
         COUNT(o.order_id) AS total_tickets_placed,
         SUM(CASE WHEN o.status <> 'CANCELLED' THEN 1 ELSE 0 END) AS completed_tickets,
         SUM(CASE WHEN o.status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled_tickets,
         IFNULL(SUM(CASE WHEN o.status <> 'CANCELLED' THEN o.total_amount ELSE 0.00 END), 0.00) AS daily_gross_revenue,
         IFNULL(ROUND(AVG(CASE WHEN o.status <> 'CANCELLED' THEN o.total_amount ELSE NULL END), 2), 0.00) AS avg_ticket_size
  FROM orders o
  WHERE 1 = 1
    AND DATE(o.created_at) >= ?   -- only when startDate supplied
    AND DATE(o.created_at) <= ?   -- only when endDate supplied
  GROUP BY DATE(o.created_at)
  ORDER BY sales_date DESC
  ```

* **Sample Output** (bound `startDate = '2026-10-19'`, `endDate = '2026-10-19'`):

  ```text
  +------------+----------------------+-------------------+-------------------+---------------------+-----------------+
  | sales_date | total_tickets_placed | completed_tickets | cancelled_tickets | daily_gross_revenue | avg_ticket_size |
  +------------+----------------------+-------------------+-------------------+---------------------+-----------------+
  | 2026-10-19 |                   15 |                14 |                 1 |            10250.00 |          732.14 |
  +------------+----------------------+-------------------+-------------------+---------------------+-----------------+
  ```

* **Why it is safe/fast:** Every bound value (dates) is a JDBC parameter, never
  string-interpolated; the aggregate scans the `orders` table once (`DATE()` on the
  column means the `(status, created_at)` index is not usable, so this is an
  intentional small-table full scan shared by all dashboard dates).

### 6.2 Top selling items

* **Purpose:** Ranks menu items by units sold and revenue for sales-velocity
  analysis; the `LIMIT` is a bound parameter (defaulted/clamped to 1–100 by the
  service). `ReportService.topItems(limit)` → `JdbcReportRepository.topItems()` →
  **`GET /api/admin/reports/top-items`**.
* **SQL:**

  ```sql
  SELECT mi.item_id, mi.name AS item_name, c.name AS category, mi.item_type,
         SUM(oi.quantity) AS total_units_sold, SUM(oi.subtotal) AS total_revenue_generated
  FROM order_item oi
  JOIN menu_item mi ON oi.item_id = mi.item_id
  JOIN category c ON mi.category_id = c.category_id
  JOIN orders o ON oi.order_id = o.order_id
  WHERE o.status <> 'CANCELLED'
  GROUP BY mi.item_id, mi.name, c.name, mi.item_type
  ORDER BY total_units_sold DESC, total_revenue_generated DESC LIMIT ?
  ```

* **Sample Output** (bound `LIMIT = 5`):

  ```text
  +---------+----------------------+-----------------------+-----------+------------------+-------------------------+
  | item_id | item_name            | category              | item_type | total_units_sold | total_revenue_generated |
  +---------+----------------------+-----------------------+-----------+------------------+-------------------------+
  |      13 | Garlic Butter Naan   | Breads & Rice         | FOOD      |                7 |                  455.00 |
  |      25 | Alphonso Mango Lassi | Beverages & Mocktails | BEVERAGE  |                6 |                  780.00 |
  |       6 | Butter Chicken       | Main Course           | FOOD      |                4 |                 1680.00 |
  |      15 | Laccha Paratha       | Breads & Rice         | FOOD      |                4 |                  220.00 |
  |       9 | Kadai Chicken        | Main Course           | FOOD      |                3 |                 1230.00 |
  +---------+----------------------+-----------------------+-----------+------------------+-------------------------+
  ```

* **Why it is safe/fast:** Prepared statement with a bound `LIMIT`; joins resolve via
  `idx_order_item_item_id`, `idx_order_item_order_id` and the item/category primary
  keys, and the aggregate is computed entirely in the database.

### 6.3 Busiest tables

* **Purpose:** Table utilisation and turnover: sessions hosted, orders placed and
  gross revenue per table. `ReportService.busiestTables()` →
  `JdbcReportRepository.busiestTables()` → **`GET /api/admin/reports/busiest-tables`**.
* **SQL:**

  ```sql
  SELECT rt.table_id, rt.table_number, rt.capacity,
         COUNT(DISTINCT ds.session_id) AS sessions_hosted,
         COUNT(o.order_id) AS total_orders_placed,
         IFNULL(SUM(CASE WHEN o.status <> 'CANCELLED' THEN o.total_amount ELSE 0.00 END), 0.00) AS gross_revenue_earned
  FROM restaurant_table rt
  LEFT JOIN dining_session ds ON rt.table_id = ds.table_id
  LEFT JOIN orders o ON ds.session_id = o.session_id
  GROUP BY rt.table_id, rt.table_number, rt.capacity
  ORDER BY gross_revenue_earned DESC, total_orders_placed DESC
  ```

* **Sample Output** (10 rows):

  ```text
  +----------+--------------+----------+-----------------+---------------------+----------------------+
  | table_id | table_number | capacity | sessions_hosted | total_orders_placed | gross_revenue_earned |
  +----------+--------------+----------+-----------------+---------------------+----------------------+
  |        5 | T05          |        4 |               1 |                   2 |              1980.00 |
  |        1 | T01          |        2 |               1 |                   2 |              1205.00 |
  |        3 | T03          |        4 |               1 |                   2 |              1185.00 |
  |        6 | T06          |        6 |               1 |                   2 |              1180.00 |
  |        8 | T08          |        8 |               1 |                   1 |              1035.00 |
  |        9 | T09          |        8 |               1 |                   1 |               970.00 |
  |        2 | T02          |        2 |               1 |                   1 |               835.00 |
  |       10 | T10          |        4 |               1 |                   1 |               785.00 |
  |        4 | T04          |        4 |               1 |                   2 |               590.00 |
  |        7 | T07          |        6 |               1 |                   1 |               485.00 |
  +----------+--------------+----------+-----------------+---------------------+----------------------+
  ```

* **Why it is safe/fast:** Read-only prepared statement (no binds); the `LEFT JOIN`s
  use `idx_dining_session_table_id` and `idx_orders_session_id` so every table row is
  retained even with no sessions/orders.

### 6.4 Average kitchen prep time

* **Purpose:** KPI measuring kitchen efficiency — average, fastest and slowest
  minutes between a ticket becoming `ACCEPTED` and `READY`, self-joined from the
  audit log. `ReportService.avgPrepTime()` → `JdbcReportRepository.avgPrepTime()` →
  **`GET /api/admin/reports/avg-prep-time`**.
* **SQL:**

  ```sql
  SELECT COUNT(o.order_id) AS orders_measured,
         IFNULL(ROUND(AVG(TIMESTAMPDIFF(MINUTE, l_acc.created_at, l_ready.created_at)), 2), 0.00) AS avg_kitchen_prep_minutes,
         IFNULL(MIN(TIMESTAMPDIFF(MINUTE, l_acc.created_at, l_ready.created_at)), 0) AS fastest_prep_minutes,
         IFNULL(MAX(TIMESTAMPDIFF(MINUTE, l_acc.created_at, l_ready.created_at)), 0) AS slowest_prep_minutes
  FROM orders o
  JOIN order_status_log l_acc ON o.order_id = l_acc.order_id AND l_acc.to_status = 'ACCEPTED'
  JOIN order_status_log l_ready ON o.order_id = l_ready.order_id AND l_ready.to_status = 'READY'
  ```

* **Sample Output** (single row):

  ```text
  +-----------------+--------------------------+----------------------+----------------------+
  | orders_measured | avg_kitchen_prep_minutes | fastest_prep_minutes | slowest_prep_minutes |
  +-----------------+--------------------------+----------------------+----------------------+
  |               7 |                    14.86 |                    0 |                   27 |
  +-----------------+--------------------------+----------------------+----------------------+
  ```

* **Why it is safe/fast:** Read-only prepared statement (no binds); the self-join on
  `order_status_log` uses `idx_order_status_log_order_id`. There is no index on
  `to_status`, so this reads the (small) audit table to pick the `ACCEPTED`/`READY`
  milestones — acceptable for an on-demand dashboard KPI.

---

## Closing summary

The application user is the least-privilege `restaurant_app` account (SELECT, INSERT,
UPDATE, DELETE, EXECUTE, SHOW VIEW only — no DDL), and **every** statement above is a
`PreparedStatement` with `?` bind placeholders; no value is ever concatenated into SQL.

| Query | File | Index / view used | Typical rows scanned |
|---|---|---|---|
| 1.1 Grouped customer menu | `JdbcMenuItemRepository.java` | `idx_menu_item_category_id` (LEFT JOIN) | 6 categories + 29 items |
| 1.2 Menu by category | `JdbcMenuItemRepository.java` | `idx_menu_item_category_id` | 1 category slice |
| 1.3 Menu by item type (in-memory in app) | `JdbcMenuItemRepository.java` / `MenuService.java` | none (small-table scan) | 29 |
| 2.1 Insert order header | `JdbcOrderRepository.java` | PK insert (`fk_orders_session`) | 1 |
| 2.2 Insert order items | `JdbcOrderRepository.java` | PK insert (`uq_order_item_unique`) | 1 per line |
| 2.3 Read-back order header | `JdbcOrderRepository.java` | `orders` PK | 1 |
| 2.4 Read-back order items | `JdbcOrderRepository.java` | `idx_order_item_order_id` + item PK | lines of order |
| 3.1 Kitchen queue | `JdbcOrderRepository.java` | `v_kitchen_active_orders` + `idx_orders_kitchen` | active tickets × dishes |
| 3.2 Status update (optimistic) | `JdbcOrderRepository.java` | `orders` PK (+ `trg_orders_validate_status_transition`) | 1 |
| 4.1 Table by QR token | `JdbcTableRepository.java` | `uq_restaurant_table_qr_token` | 1 |
| 4.2 Open session by table | `JdbcSessionRepository.java` | `idx_dining_session_table_id` | ≤1 |
| 4.3 Open new session | `JdbcSessionRepository.java` | PK insert (`uq_dining_session_token`) | 1 |
| 4.4 Close session | `JdbcSessionRepository.java` | `dining_session` PK | 1 |
| 5.1 Generate bill | `JdbcOrderRepository.java` | `sp_generate_bill` + `idx_orders_session_id` | session's orders |
| 5.2 Order total function | `db/advanced.sql` (`fn_order_total`) | `idx_order_item_order_id` | order's items |
| 5.3 Billing summaries | `JdbcOrderRepository.java` | `idx_orders_session_id` + `v_order_totals` | session's orders |
| 5.4 Insert payment | `JdbcPaymentRepository.java` | PK insert + `uq_payment_transaction_ref` | 1 |
| 5.5 Confirm payment | `JdbcPaymentRepository.java` | `payment` PK | 1 |
| 5.6 Close session (settlement) | `JdbcSessionRepository.java` | `dining_session` PK | 1 |
| 6.1 Daily sales | `JdbcReportRepository.java` | small-table scan of `orders` | 15 |
| 6.2 Top items | `JdbcReportRepository.java` | `idx_order_item_item_id` / `_order_id` | order_item rows |
| 6.3 Busiest tables | `JdbcReportRepository.java` | `idx_dining_session_table_id` / `idx_orders_session_id` | 10 tables × sessions |
| 6.4 Avg prep time | `JdbcReportRepository.java` | `idx_order_status_log_order_id` | order_status_log rows |
