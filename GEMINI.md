# Restaurant Table-Ordering System (college lab: RDBMS + Java OOP)

Goal: Customers at a table open the menu on their phone (menu.html?table=<token>), add items to a cart and place an order. The order appears live on the kitchen/chef screen where staff move it PLACED → ACCEPTED → PREPARING → READY → SERVED. Then billing and sales reports.

Stack (do not change without asking): Java 17, Spring Boot 3 (Maven), Spring JDBC JdbcTemplate (NO JPA/Hibernate), MySQL 8, plain HTML/CSS/JS in src/main/resources/static, REST+JSON, kitchen screen polls every 3s.

Rules:
1. SQL lives only in repository classes, prepared statements only.
2. Never trust prices from the client; read price from DB.
3. Money = DECIMAL(10,2) in DB, BigDecimal in Java.
4. Placing an order = ONE database transaction.
5. Layers: controller → service → repository → DB.
6. Demonstrate OOP (encapsulation, inheritance, abstraction, polymorphism, interfaces, enums, custom exceptions, generics/collections, composition) and patterns (DAO, Factory, Strategy). Mark with a comment `// OOP: <concept>`.
7. Keep code simple and readable. I must explain it in a viva. Add short comments.
8. After each task: run it, tell me how to test it, and list anything unfinished.
9. Secrets via environment variables, never committed. Maintain .gitignore.
10. Any schema change must be made in db/schema.sql, never silently in Java.
