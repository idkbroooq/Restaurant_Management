# Viva Questions & Answers — Restaurant Table-Ordering System

40 questions, numbered continuously, in three sections:

- **A. Database / SQL** — questions 1–13
- **B. Java / OOP** — questions 14–26
- **C. Architecture / Spring** — questions 27–40

Answers are deliberately short (2–5 sentences) and reference real classes, tables and files in
this repository.

---

## A. Database / SQL

**1. Explain 1NF, 2NF and 3NF. How does this schema satisfy them?**
1NF means every column holds a single atomic value and rows are uniquely keyed — here every table
has a surrogate primary key (`order_id`, `item_id`, …) and no repeating groups. 2NF means no
partial dependency on part of a composite key — the only composite key is `order_item`'s
`(order_id, item_id)`, and the other columns depend on the whole pair. 3NF means no transitive
dependency on a non-key column — for example `menu_item` stores `category_id` (a foreign key) rather
than duplicating the category name, so category data lives only in `category`. See
`db/schema.sql` and `docs/NORMALIZATION.md`.

**2. Which keys exist — primary, foreign, candidate/unique — and how is referential integrity enforced?**
Every table has a surrogate primary key (`AUTO_INCREMENT`), and business keys are enforced as
unique constraints: `restaurant_table.table_number`, `restaurant_table.qr_token`,
`staff.username`, `category.name`, `menu_item.name`, `dining_session.session_token` and
`payment.transaction_ref`. Foreign keys link the tables (`menu_item.category_id`,
`orders.session_id`, `order_item.order_id`, `order_item.item_id`, `payment.session_id`, …) and
declare `ON DELETE RESTRICT` (or `CASCADE` for `order_item`), so the database itself prevents
orphan rows.

**3. What indexes exist and why?**
Every foreign key gets an index (MySQL creates them automatically, and they are declared
explicitly in `db/schema.sql`) so joins and `WHERE fk = ?` lookups are fast. The most important
custom one is `idx_orders_kitchen (status, created_at)`, a composite index that serves the kitchen
board query (filter by status, order by creation time). Unique constraints (e.g. `qr_token`,
`session_token`) are backed by unique indexes too.

**4. What are the views `v_kitchen_active_orders` and `v_order_totals` for?**
A view is a stored `SELECT`, not a table — it holds no data of its own.
`v_kitchen_active_orders` joins `orders`, `dining_session`, `restaurant_table`, `order_item` and
`menu_item`, filters to non-terminal statuses, and computes `elapsed_minutes`; it backs
`GET /api/kitchen/orders`. `v_order_totals` re-computes each order's total from `order_item` rows
so the stored `orders.total_amount` can be audited (`MATCH`/`MISMATCH`).

**5. What do the triggers `trg_orders_validate_status_transition` and `trg_orders_log_status_change` do?**
`trg_orders_validate_status_transition` is a `BEFORE UPDATE` trigger that reads the old and new
status and raises `SIGNAL SQLSTATE '45000'` if the change is illegal or tries to re-open a terminal
order — a database-level safety net behind the Java state machine.
`trg_orders_log_status_change` is an `AFTER UPDATE` trigger that inserts an audit row into
`order_status_log`, recording the acting staff id from the `@current_staff_id` session variable.

**6. What is the difference between a stored procedure and a stored function? Give an example of each here.**
A stored procedure is invoked with `CALL`, may perform side effects, and can return a result set;
a stored function is invoked inside an expression, must return exactly one value, and is meant to be
side-effect free. Here `sp_generate_bill(p_session_id)` is the procedure that returns the whole bill
result set, and `fn_order_total(order_id)` is the function that returns a single order total used in
queries.

**7. What is a transaction and what does ACID mean? Where do transactions matter here?**
A transaction is a group of statements that all succeed or all fail together. ACID = Atomicity
(all-or-nothing), Consistency (constraints hold before and after), Isolation (concurrent
transactions don't corrupt each other) and Durability (committed data survives crashes).
Placing an order is one transaction: the session insert, the order header and all `order_item` rows
must commit together — `OrderService.placeOrder(...)` is annotated `@Transactional`.

**8. What isolation level is used and what anomalies are possible?**
The default InnoDB isolation level is `REPEATABLE READ`, which prevents dirty reads and
non-repeatable reads; phantom rows are largely prevented by gap/next-key locking. Because two
kitchen clients can read the same ticket and then both try to move it, we do not rely on isolation
alone for that race — we add an explicit optimistic-concurrency guard (question 12).

**9. Explain the GST logic in `sp_generate_bill`.**
`sp_generate_bill` looks up the session and its table, sums the totals of all non-cancelled orders
in the session, and stores that as the subtotal. It then declares `v_tax_rate DECIMAL(5,2)
DEFAULT 5.00`, computes `tax = ROUND(subtotal * v_tax_rate / 100, 2)`, and returns subtotal, the
5% GST amount and `grandTotal = subtotal + tax` as a result set. `BillingService` simply reads and
returns that result set.

**10. Why is `unit_price` snapshotted into `order_item` instead of reading `menu_item.price` at bill time?**
So the historical bill never changes: if a manager later edits the menu price, past orders must still
show the price the customer actually agreed to. `order_item.unit_price` is frozen when the order is
placed (`OrderService` copies `menuItem.getFinalPrice()`), and `subtotal` is a generated column
(`quantity * unit_price`). This is also why the client cannot send prices — they come from the DB.

**11. Why use `ENUM` columns such as `orders.status`, `staff.role` and `payment.payment_method`? What are the trade-offs?**
Enums enforce a fixed, type-safe set of values at the database level, so illegal values (e.g.
`status='DONE'`) are rejected and storage is compact. They mirror the Java enums
(`OrderStatus`, `StaffRole`, `PaymentMethodType`), keeping code and schema aligned. The trade-off is
that adding or renaming a value requires an `ALTER TABLE`, which is a schema change.

**12. How does the guarded `UPDATE` implement optimistic concurrency?**
Instead of locking the row when reading, `JdbcOrderRepository.updateStatus` runs
`UPDATE orders SET status = ? WHERE order_id = ? AND status = ?`, passing the status the caller
*believed* was current. If another client already changed it, the `WHERE status = ?` matches zero
rows; `KitchenService` treats `0` rows as a conflict and throws `StatusConflictException`, which the
API maps to `409 Conflict` with `expectedStatus`/`actualStatus`. No locks are held between the read
and the write.

**13. How is the N+1 query problem avoided?**
The menu and reports are loaded with a single grouped `JOIN` rather than one query per parent row.
For example `JdbcMenuItemRepository.SQL_MENU_GROUPED` fetches categories and their items together
and `MenuService.getGroupedMenu()` groups them in memory, so rendering 6 categories and 29 items
takes one round trip instead of 30+. The kitchen board similarly reads one view instead of a query
per ticket.

---

## B. Java / OOP

**14. What is encapsulation and how is it demonstrated?**
Encapsulation is hiding an object's internal state behind private fields and exposing only
controlled access. `MenuItem`, `Order`, `Payment` and `User` keep their fields `private final` and
expose getters; constructors and setters validate input (e.g. `MenuItem` rejects a blank name or a
non-positive price). Callers can never set an illegal state directly.

**15. Explain inheritance with the `MenuItem` hierarchy.**
`MenuItem` is the abstract superclass holding the common fields (id, category, name, price, type,
availability, spice level, volume) and shared behaviour such as `equals`/`hashCode` and
`getFinalPrice()`. `Food`, `Beverage` and `Dessert` extend it and supply their own
`getPreparationTimeMinutes()` (15, 5 and 10 minutes respectively); `Food` also overrides
`getFinalPrice()` to add a surcharge for `EXTRA_HOT`. This removes duplication because every subtype
reuses the base validation and accessors.

**16. Explain inheritance with the `User` hierarchy.**
`User` is the abstract superclass storing id, username, `passwordHash`, full name and active flag,
and it declares `abstract String getRole()`. `Chef`, `Waiter` and `Manager` extend it and each
returns `"CHEF"`, `"WAITER"` or `"MANAGER"`. The role becomes polymorphic behaviour instead of an
if/else on a type field.

**17. How are abstraction and interfaces used?**
Abstraction means exposing *what* something does, not *how*. Abstract classes capture shared state
and partial behaviour (`MenuItem`, `User`, `RestaurantException`), while interfaces capture pure
contracts with multiple possible implementations (`Repository<T, ID>`, `DiscountStrategy`,
`PaymentMethod`, `HealthService`). For example `HealthService` is an interface and
`HealthServiceImpl` is the concrete implementation injected into `HealthController`.

**18. Explain polymorphism via `MenuItemFactory`.**
`MenuItemFactory.create(...)` switches on the `ItemType` and returns the appropriate subclass
(`Food`, `Beverage` or `Dessert`) as a `MenuItem` reference. The repository's row mapper calls it, so
the caller works only against the `MenuItem` supertype and the correct subclass is chosen at
runtime — the classic Factory + polymorphism combination.

**19. Explain polymorphism via `PaymentFactory`.**
`PaymentFactory.create(PaymentMethodType)` returns a `CashPayment`, `CardPayment` or `UpiPayment`,
each of which implements the `PaymentMethod` interface (`pay(...)`, `getMethodType()`).
`BillingService` calls `method.pay(...)` without knowing which concrete channel it got, so adding a
new payment method means adding a class and one switch branch, not editing the service.

**20. How are Generics used in the repository layer?**
`Repository<T, ID>` is a generic interface declaring `Optional<T> findById(ID id)` and
`List<T> findAll()`. Concrete repositories such as `JdbcOrderRepository` bind the type parameters
(`Repository<Order, Long>`), giving compile-time type safety without casts and letting the rest of
the code depend on the abstraction rather than a specific implementation.

**21. Why use enums, and how does `OrderStatus` behave like a state machine?**
Enums give a fixed, type-safe set of constants, so an invalid status is a compile error rather than
a typo. `OrderStatus` goes further: each constant declares its legal next statuses
(`PLACED -> {ACCEPTED, CANCELLED}`, `ACCEPTED -> {PREPARING, CANCELLED}`, …), and the methods
`canTransitionTo(next)` and `isTerminal()` encode the workflow. That keeps the transition rules in
one place used by both `Order` and `KitchenService`.

**22. Describe the custom exception hierarchy.**
`RestaurantException` is an abstract `RuntimeException` that carries an `errorCode` string, and all
domain failures extend it: `TableNotFoundException`, `OrderNotFoundException`,
`SessionNotFoundException` (404); `AuthenticationException` (401); `ForbiddenException` (403);
`StatusConflictException` / `ConflictException` (409); `ItemUnavailableException`,
`EmptyOrderException`, `InvalidOrderStateException`, `BadRequestException` (400). Because they share
one root, `GlobalExceptionHandler` can map whole families to HTTP statuses in one place.

**23. Why use `BigDecimal` for money instead of `double`?**
`double` is binary floating point and cannot represent values like `0.10` exactly, so repeated
rounding produces errors a few paise off. Money columns are `DECIMAL(10,2)` and every Java amount is
a `BigDecimal` (prices, totals, tax); arithmetic uses `setScale(2, RoundingMode.HALF_UP)` where a
rounded value is needed. This keeps the 5% GST and grand totals exact.

**24. What does `@Transactional` do, and how is rollback demonstrated?**
`@Transactional` wraps the method in a database transaction that commits when the method returns
normally and rolls back when an unchecked exception propagates. `OrderService.placeOrder` is
`@Transactional`, so if the second `INSERT` (the `order_item` rows) fails, the session and order
header writes are undone as well. `OrderServiceTest.placeOrder_rollsBackOnFailure` proves the
exception propagates, which is what triggers Spring's rollback.

**25. What is composition over inheritance, and where is it used?**
Composition builds objects from other objects instead of forcing an "is-a" hierarchy. `Order`
*has* a `List<OrderItem>` and `DiningSession` *has* a table id rather than extending them; items
added or removed from an order go through `Order.addItem`/`removeItem`. This keeps `Order`
responsible only for order behaviour and avoids a fragile, deeply nested class tree.

**26. Why override `equals`/`hashCode`, and how?**
Value-like objects must compare by meaning, not by object identity, so they can be put in sets/maps
and compared in tests. `MenuItem` and `User` implement `equals`/`hashCode` on the `id` field, and
immutable value objects such as `PaymentReceipt` use a natural business key (the receipt number).
Records like `BillTotals` and the DTOs get correct `equals`/`hashCode` for free.

---

## C. Architecture / Spring

**27. Describe the layered architecture and the flow of a request.**
Requests flow strictly downward: controller → service → repository → database, and responses flow
back up. A controller only validates/translates HTTP and delegates; a service holds business rules
and transaction boundaries; a repository holds all SQL. `OrderController` → `OrderService` →
`JdbcOrderRepository` → MySQL is a concrete example, and no layer skips the one below it.

**28. What is the DAO pattern and how is it applied?**
DAO (Data Access Object) isolates all persistence code behind an interface so business code never
sees SQL. Here `Repository<T, ID>` is the generic DAO contract and classes such as
`JdbcOrderRepository` and `JdbcMenuItemRepository` are the JDBC implementations. Services depend on
the interface, so the storage mechanism could change without touching business logic.

**29. What is the Factory pattern and where is it used?**
A factory centralises object creation so callers don't `new` concrete types directly.
`MenuItemFactory` turns an `ItemType` into a `Food`/`Beverage`/`Dessert`, and `PaymentFactory` turns a
`PaymentMethodType` into a `CashPayment`/`CardPayment`/`UpiPayment`. Creation logic lives in exactly
one place, which keeps the mappers and services clean.

**30. What is the Strategy pattern and where is it used?**
Strategy encapsulates interchangeable algorithms behind one interface. `DiscountStrategy` declares
`applyDiscount(BigDecimal)` and `getDescription()`; `NoDiscount` is the default bean
(`AppConfig.discountStrategy()`), and `HappyHourDiscount` / `PercentageDiscount` are alternatives.
`BillingService` calls the strategy without knowing which policy is active, so the discount rule can
be swapped without changing billing code.

**31. What is dependency injection, and why constructor injection?**
DI means Spring supplies an object's collaborators instead of the object creating them.
Constructor injection receives dependencies as constructor parameters, so fields can be `final`,
dependencies are guaranteed present, and the class is trivially unit-testable with mocks (as in
`OrderServiceTest`, which builds `new OrderService(...)` with mocked repositories). It also makes
circular dependencies fail fast at startup.

**32. Why `JdbcTemplate` instead of JPA/Hibernate?**
This is an RDBMS course project, so writing the SQL by hand is the point: `JdbcTemplate` keeps every
query, join and stored-procedure call explicit and visible (prepared statements, `RowMapper`).
JPA/Hibernate would hide the SQL behind an ORM, which is great for productivity but obscures the
database skills being assessed. It also keeps the dependency footprint and mappings simple.

**33. How do prepared statements prevent SQL injection?**
`JdbcTemplate` always sends SQL and parameters separately (`?` placeholders), so user input is
bound as data and can never be parsed as SQL. For example the status filter and the report `LIMIT`
are passed as bound parameters, never string-concatenated. A value like `"1 OR 1=1"` is treated as a
literal, not as part of the query.

**34. How does authentication work, and why `HandlerInterceptor` + `HttpSession` instead of Spring Security?**
`POST /api/auth/login` verifies the BCrypt hash and stores the staff profile in the `HttpSession`;
`StaffAuthInterceptor` (a `HandlerInterceptor`) checks that session on protected paths and throws
`AuthenticationException` (401) or `ForbiddenException` (403) based on role. Because this project
uses only `BCryptPasswordEncoder` and no Spring Security web filters, the auth flow stays small and
fully readable for the viva. `WebConfig` maps paths to allowed roles (kitchen and status → CHEF/
MANAGER, billing → WAITER/MANAGER, admin → MANAGER).

**35. What does `@RestControllerAdvice` do?**
`GlobalExceptionHandler` is a `@RestControllerAdvice` that turns exceptions into consistent JSON
error bodies. Each handler maps a domain exception to an HTTP status and the canonical
`{error, message, statusCode, timestamp}` shape, so controllers never contain try/catch blocks and
every error the API returns looks the same.

**36. What is Bean Validation and how is it applied?**
Bean Validation (Jakarta Validation) declaratively checks request payloads using annotations such as
`@NotBlank`, `@NotEmpty`, `@NotNull`, `@Min(1)`, `@Max(20)`, `@Size(max=255)` and `@DecimalMin`.
They sit on the DTO records in `Dtos.java`; Spring runs them before the controller body executes and
a failure is turned into a `400 VALIDATION_FAILED` response with a per-field details list.

**37. Explain the meaning of the HTTP status codes used.**
`200 OK` — successful read/update; `201 Created` — a new order/menu item/table was created;
`400 Bad Request` — validation or business rule failure (empty cart, bad quantity, illegal status
jump); `401 Unauthorized` — no valid session; `403 Forbidden` — logged in but the role is not
allowed; `404 Not Found` — unknown table/order/session/URL; `409 Conflict` — the optimistic-lock
concurrency conflict or a duplicate transaction reference.

**38. Why return DTO records instead of entities?**
DTOs (in `Dtos.java`) decouple the API contract from the internal model and from the database shape.
They carry only what a client should see (no password hashes, no internals), they are immutable Java
records with `equals`/`hashCode` for free, and they let the same model be rendered differently for
customer, kitchen and admin clients. Bean Validation annotations also live naturally on the request
records.

**39. Why polling instead of WebSocket for live updates?**
Polling is trivial to implement and reason about: the customer page re-fetches status every ~4 s and
the kitchen board every ~3 s using `setTimeout` chains. It needs no extra server infrastructure and
fits an exam demonstration, where the trade-off (slightly higher latency and request count) is
irrelevant. A WebSocket would be the choice for high-frequency, low-latency production traffic.

**40. Explain soft delete and the optimistic-concurrency pattern as used here.**
Soft delete means marking a row as removed instead of physically deleting it: `DELETE
/api/admin/menu/{id}` sets `menu_item.is_deleted = TRUE` and `is_available = FALSE`, so historical
`order_item` rows keep valid references while the item disappears from the customer menu. The
optimistic-concurrency pattern is the guarded `UPDATE orders ... WHERE order_id = ? AND status = ?`;
zero affected rows means someone changed the row first, which becomes a `409 Conflict` rather than a
lost update. Both patterns avoid destructive operations and keep the data consistent.
