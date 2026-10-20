# 5-Minute Viva Demonstration Script

Restaurant Table-Ordering System — Spring Boot 3 + Spring JDBC (`JdbcTemplate`) + MySQL.
Keep this page open on a second monitor. Times are cumulative targets.

---

## Pre-demo checklist (do this 10 minutes before)

- [ ] **Database imported** — `restaurant` DB exists with `schema.sql`, then `seed.sql`, then
      `advanced.sql` applied (tables, views, triggers, `sp_generate_bill`, `fn_order_total`,
      grants).
- [ ] **App started** — running on `http://localhost:8080`; confirm with
      `GET http://localhost:8080/api/health` returning `{"status":"UP","database":"CONNECTED"}`.
- [ ] **Browser windows/tabs pre-opened** (one window, 4 tabs):
      1. `http://localhost:8080/` (landing)
      2. `http://localhost:8080/customer/index.html?table=tok_tbl_05_e5b4a32109876543`
      3. `http://localhost:8080/kitchen/`
      4. `http://localhost:8080/admin/`
- [ ] **DevTools mobile emulation** ready for the customer tab (toggle device toolbar, pick a
      phone-sized viewport ~390px wide).
- [ ] **Sound enabled** on the machine and headphones/speakers on — the kitchen board plays a chime.
- [ ] **Staff credentials** to hand: `chef_mario` / `ChefPassword123!` (CHEF),
      `waiter_john` / `WaiterPassword123!` (WAITER), `manager_alice` / `ManagerPassword123!` (MANAGER).
- [ ] **A terminal** open at `d:\project-lab` (fallback for billing via curl).
- [ ] **Table 5 is free** — no ACTIVE session on T05 (if there is one, that's fine; the demo
      reuses it).

---

## 0:00 – 0:30 — Intro

- **Do:** Show tab 1 (`http://localhost:8080/`) and gesture at the three cards.
- **Say:** "This is a restaurant table-ordering system built as an RDBMS plus Java OOP project.
  Spring Boot 3 with Spring JDBC, backed by MySQL. Guests order from a QR code, the kitchen drives
  a live ticket board, and managers run the back office — all three front-ends talk to one REST API,
  and every rule lives in the database and the Java model."
- **Do:** Point at the note at the bottom of the page.
- **Say:** "All SQL is hand-written with prepared statements, money is `DECIMAL`/`BigDecimal`, and
  placing an order is a single transaction."

## 0:30 – 1:30 — Customer flow (phone-sized browser)

- **Do:** Switch to tab 2
  (`http://localhost:8080/customer/index.html?table=tok_tbl_05_e5b4a32109876543`), DevTools mobile
  view on.
- **Say:** "The QR code carries a table token. The page resolves it to Table T05, capacity four —
  no login needed for a guest."
- **Do:** Tap a couple of **category tabs** (e.g. Starters, then Main Course) and type something in
  the **search** box, then clear it.
- **Say:** "The menu is one grouped query for all categories; tabs and search filter without extra
  round-trips."
- **Do:** Add two items to the cart (tap **Add**); open the cart drawer; type a note in
  **Special notes** (e.g. "Less spicy").
- **Say:** "The cart lives in `sessionStorage`, so it survives a reload. Prices are never sent from
  the browser — only item id and quantity; the server reads the price from the database."
- **Do:** Tap **Place order** and point at the button while it's in flight.
- **Say:** "Notice the button is disabled and shows 'Placing…' — that stops a double submit. The
  order comes back as `201 Created` with a `PLACED` status."
- **Do:** Note the created order's **session id** (used later for the bill); the tracking screen
  appears.

## 1:30 – 2:45 — Kitchen screen

- **Do:** Switch to tab 3 (`http://localhost:8080/kitchen/`). Log in as `chef_mario` /
  `ChefPassword123!`.
- **Say:** "The board is role-protected — only CHEF or MANAGER can see it. If I log in as the waiter,
  this API returns `403 Forbidden`."
- **Do:** Point at the new ticket in the **PLACED** column; turn on **Enable sound**.
- **Say:** "New tickets arrive within about three seconds — the board polls. Each card shows elapsed
  time; it turns amber at 10 minutes and red at 20, and a chime plays for new tickets."
- **Do:** Move the ticket **Accept** (PLACED→ACCEPTED), then **Start preparing**
  (ACCEPTED→PREPARING), then **Mark ready** (PREPARING→READY).
- **Say:** "Each move is a `PATCH` with `from` and `to`. The transition is validated against the
  `OrderStatus` state machine and the database trigger."
- **Do:** Rapidly **double-click** one action on the same ticket.
- **Say:** "The second click fails with `409 Conflict` — the guarded
  `UPDATE ... WHERE order_id = ? AND status = ?` matched zero rows because someone already moved it.
  The board shows a toast and refreshes. That's optimistic concurrency, not a lock."

## 2:45 – 3:30 — Customer tracking screen

- **Do:** Switch back to tab 2; the tracking screen shows the stepper.
- **Say:** "The customer view polls every four seconds and walks the stepper
  PLACED → ACCEPTED → PREPARING → READY → SERVED."
- **Do:** Watch the stepper advance as the kitchen moves the ticket (or move it in tab 3 to show
  the live change).
- **Say:** "Same data, different view — the customer only ever sees their own order by table token."

## 3:30 – 4:15 — Billing and reports

- **Do:** In tab 2 tap **Request bill** — the panel previews the subtotal and 5% GST.
- **Say:** "The guest sees an estimate. The authoritative bill is generated server-side by the
  `sp_generate_bill` stored procedure."
- **Do (primary):** Open `http://localhost:8080/swagger-ui/index.html`, authenticate as
  `waiter_john` / `WaiterPassword123!`, call **POST /api/sessions/{id}/bill** with the session id
  from 0:30, then **POST /api/sessions/{id}/pay** with
  `{"paymentMethod":"UPI","amount":<grandTotal>,"transactionRef":"UPI/DEMO/1"}`.
- **Say:** "The bill sums every non-cancelled order, adds 5% GST, and the payment must match the
  outstanding total exactly — otherwise it's a `400`. On success the session flips to COMPLETED and
  the table is freed."
- **Do:** Switch to tab 4 (`http://localhost:8080/admin/`), log in as `manager_alice` /
  `ManagerPassword123!`, open **Reports**, run **Daily sales** and **Top selling items**.
- **Say:** "Managers get sales, top items, busiest tables and average prep time — all read-only
  aggregate queries."

## 4:15 – 5:00 — Q&A hook

- **Say:** "That's the full loop: guest orders, kitchen prepares, bill is generated and paid,
  manager reports. The interesting parts to ask me about are the state machine and the 409
  concurrency guard, the `sp_generate_bill` GST logic, the OOP hierarchy
  `MenuItem`→`Food`/`Beverage`/`Dessert`, and the Factory and Strategy patterns."
- **Do:** Stop talking and invite questions; keep this page and the code open.

---

## If something breaks (fallbacks)

| Symptom | Likely cause | Fallback |
|---|---|---|
| Customer tab shows an error / blank | Bad or missing `?table=` token | Re-open `http://localhost:8080/customer/index.html?table=tok_tbl_05_e5b4a32109876543` |
| Kitchen board stuck on login | Wrong credentials | Use `chef_mario` / `ChefPassword123!`; note `manager_alice` also has access |
| No chime on the kitchen board | Browser blocked autoplay | Click **Enable sound** once (this creates the AudioContext on a user gesture) |
| Kitchen ticket doesn't move | Session expired or wrong role | Re-login as CHEF; if it toasts a conflict, just click again |
| Bill returns `400` | Outstanding total changed or amount mismatch | Re-run **POST /api/sessions/{id}/bill**, copy the exact `grandTotal`, then pay with that amount |
| Swagger UI fails to authenticate | Cookie blocked | Use the terminal fallback below |
| Reports empty | Date filter excludes the data | Use `startDate`/`endDate` around today (e.g. `startDate=2026-10-01&endDate=2026-10-31`); note `from`/`to` are ignored |
| App not responding at all | Server down | Restart `.\mvnw.cmd spring-boot:run` from `d:\project-lab` and re-run the health check |

**Terminal fallback for billing** (replace `{id}` with the session id and `{total}` with the bill's
grand total):

```
curl -c cj.txt -H "Content-Type: application/json" -d "{\"username\":\"waiter_john\",\"password\":\"WaiterPassword123!\"}" http://localhost:8080/api/auth/login
curl -b cj.txt -X POST http://localhost:8080/api/sessions/{id}/bill
curl -b cj.txt -H "Content-Type: application/json" -d "{\"paymentMethod\":\"UPI\",\"amount\":{total},\"transactionRef\":\"UPI/DEMO/1\"}" http://localhost:8080/api/sessions/{id}/pay
```
