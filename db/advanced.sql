-- ============================================================================
-- Restaurant Table-Ordering System: Advanced SQL Script
-- File: db/advanced.sql
-- Contents:
--   1. Views (Kitchen Live Queue & Order Totals)
--   2. Triggers (Audit Trail Logging & Status Transition Validation)
--   3. Stored Routines (Order Total Function & Billing Procedure)
--   4. 10 Production Analytical & Operational Queries
--   5. ACID Transaction Demo (COMMIT & ROLLBACK)
--   6. Security & Least-Privilege User Provisioning (GRANT)
-- ============================================================================

USE restaurant_db;

-- ============================================================================
-- SECTION 1: DATABASE VIEWS
-- ============================================================================

-- ----------------------------------------------------------------------------
-- View 1: v_kitchen_active_orders
-- WHAT: A denormalized real-time view joining orders, dining_session,
--       restaurant_table, order_item, and menu_item filtered strictly for
--       in-flight kitchen tickets ('PLACED', 'ACCEPTED', 'PREPARING', 'READY').
-- WHY:  The kitchen display polls every 3 seconds. Providing a pre-composed view
--       simplifies frontend query logic, abstracts underlying join topology,
--       and includes calculated fields like elapsed wait time for color-coding.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE VIEW v_kitchen_active_orders AS
SELECT 
    o.order_id,
    rt.table_number,
    o.status AS order_status,
    o.created_at AS order_placed_at,
    TIMESTAMPDIFF(MINUTE, o.created_at, NOW()) AS elapsed_minutes,
    oi.order_item_id,
    mi.name AS item_name,
    mi.item_type,
    mi.spice_level,
    mi.volume_ml,
    oi.quantity,
    oi.item_notes,
    o.special_notes AS order_special_notes
FROM orders o
JOIN dining_session ds ON o.session_id = ds.session_id
JOIN restaurant_table rt ON ds.table_id = rt.table_id
JOIN order_item oi ON o.order_id = oi.order_id
JOIN menu_item mi ON oi.item_id = mi.item_id
WHERE o.status IN ('PLACED', 'ACCEPTED', 'PREPARING', 'READY');

-- ----------------------------------------------------------------------------
-- View 2: v_order_totals
-- WHAT: An auditing view aggregating order_item subtotals and comparing them
--       against the cached total_amount stored on the orders header.
-- WHY:  Rule 2 specifies: "Never trust prices from the client". This view provides
--       an automated double-entry verification check to detect any discrepancy
--       between stored order totals and sum of individual frozen line items.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE VIEW v_order_totals AS
SELECT 
    o.order_id,
    o.session_id,
    o.status,
    o.total_amount AS stored_total_amount,
    IFNULL(SUM(oi.subtotal), 0.00) AS calculated_subtotal,
    COUNT(oi.order_item_id) AS line_item_count,
    IFNULL(SUM(oi.quantity), 0) AS total_items_quantity,
    CASE 
        WHEN o.total_amount = IFNULL(SUM(oi.subtotal), 0.00) THEN 'MATCH'
        ELSE 'MISMATCH'
    END AS total_audit_status
FROM orders o
LEFT JOIN order_item oi ON o.order_id = oi.order_id
GROUP BY o.order_id, o.session_id, o.status, o.total_amount;


-- ============================================================================
-- SECTION 2: TRIGGERS
-- ============================================================================

DELIMITER //

-- ----------------------------------------------------------------------------
-- Trigger (a): trg_orders_validate_status_transition (BEFORE UPDATE)
-- WHAT: Enforces the restaurant order state machine by intercepting updates
--       and rejecting any invalid progression, backward jump, or changes to
--       terminal states (SERVED, CANCELLED).
-- WHY:  Orders must strictly follow:
--         PLACED -> ACCEPTED -> PREPARING -> READY -> SERVED
--       Any order can transition to CANCELLED from non-terminal states.
--       Direct invalid jumps (e.g. PLACED -> SERVED) would bypass kitchen prep
--       and corrupt food timing metrics.
-- ----------------------------------------------------------------------------
DROP TRIGGER IF EXISTS trg_orders_validate_status_transition //
CREATE TRIGGER trg_orders_validate_status_transition
BEFORE UPDATE ON orders
FOR EACH ROW
BEGIN
    IF OLD.status <> NEW.status THEN
        -- 1. Disallow updates to terminal states
        IF OLD.status IN ('SERVED', 'CANCELLED') THEN
            SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Order State Error: Terminal state reached (SERVED/CANCELLED). Further status changes are forbidden.';
        END IF;

        -- 2. Validate valid forward state transitions
        IF OLD.status = 'PLACED' AND NEW.status NOT IN ('ACCEPTED', 'CANCELLED') THEN
            SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Order State Error: PLACED order can only transition to ACCEPTED or CANCELLED.';
        ELSEIF OLD.status = 'ACCEPTED' AND NEW.status NOT IN ('PREPARING', 'CANCELLED') THEN
            SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Order State Error: ACCEPTED order can only transition to PREPARING or CANCELLED.';
        ELSEIF OLD.status = 'PREPARING' AND NEW.status NOT IN ('READY', 'CANCELLED') THEN
            SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Order State Error: PREPARING order can only transition to READY or CANCELLED.';
        ELSEIF OLD.status = 'READY' AND NEW.status NOT IN ('SERVED', 'CANCELLED') THEN
            SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Order State Error: READY order can only transition to SERVED or CANCELLED.';
        END IF;
    END IF;
END //

-- ----------------------------------------------------------------------------
-- Trigger (b): trg_orders_log_status_change (AFTER UPDATE)
-- WHAT: Automatically appends an entry into order_status_log whenever an
--       order's status column changes.
-- WHY:  Provides a tamper-evident audit trail of ticket lifecycle timing without
--       relying on manual application logging. Allows measuring station prep
--       times, identifying bottlenecks, and capturing staff accountability.
-- ----------------------------------------------------------------------------
DROP TRIGGER IF EXISTS trg_orders_log_status_change //
CREATE TRIGGER trg_orders_log_status_change
AFTER UPDATE ON orders
FOR EACH ROW
BEGIN
    IF OLD.status <> NEW.status THEN
        INSERT INTO order_status_log (
            order_id,
            from_status,
            to_status,
            changed_by_staff_id,
            notes,
            created_at
        ) VALUES (
            NEW.order_id,
            OLD.status,
            NEW.status,
            @current_staff_id, -- Optional session context variable set by application
            CONCAT('Automatic audit: Status changed from ', OLD.status, ' to ', NEW.status),
            CURRENT_TIMESTAMP
        );
    END IF;
END //

DELIMITER ;


-- ============================================================================
-- SECTION 3: STORED FUNCTIONS AND PROCEDURES
-- ============================================================================

DELIMITER //

-- ----------------------------------------------------------------------------
-- Function: fn_order_total(order_id)
-- WHAT: Computes and returns the exact decimal sum of all line item subtotals
--       for a specified order.
-- WHY:  Encapsulates order total calculation within the database engine.
--       Guarantees consistency across Java repositories, views, and batch jobs.
-- ----------------------------------------------------------------------------
DROP FUNCTION IF EXISTS fn_order_total //
CREATE FUNCTION fn_order_total(p_order_id INT UNSIGNED)
RETURNS DECIMAL(10,2)
READS SQL DATA
DETERMINISTIC
BEGIN
    DECLARE v_total DECIMAL(10,2);
    
    SELECT IFNULL(SUM(subtotal), 0.00)
    INTO v_total
    FROM order_item
    WHERE order_id = p_order_id;
    
    RETURN v_total;
END //

-- ----------------------------------------------------------------------------
-- Procedure: sp_generate_bill(session_id)
-- WHAT: Aggregates all non-cancelled orders for an active dining session,
--       computes subtotal, applies 5% GST tax, calculates grand total,
--       and returns a complete billing summary.
-- WHY:  Centralizes table settlement and invoicing logic in the database.
--       Ensures tax calculations are consistent and legal requirements are met
--       before closing out dining sessions.
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_generate_bill //
CREATE PROCEDURE sp_generate_bill(IN p_session_id INT UNSIGNED)
BEGIN
    DECLARE v_subtotal DECIMAL(10,2);
    DECLARE v_tax_rate DECIMAL(5,2) DEFAULT 5.00; -- 5% GST rate
    DECLARE v_tax_amount DECIMAL(10,2);
    DECLARE v_grand_total DECIMAL(10,2);
    DECLARE v_table_num VARCHAR(20);
    DECLARE v_session_status VARCHAR(20);

    -- 1. Verify existence of session and retrieve table identifier
    SELECT ds.status, rt.table_number
    INTO v_session_status, v_table_num
    FROM dining_session ds
    JOIN restaurant_table rt ON ds.table_id = rt.table_id
    WHERE ds.session_id = p_session_id;

    IF v_session_status IS NULL THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Billing Error: Specified dining session does not exist.';
    END IF;

    -- 2. Aggregate active order subtotal (excluding cancelled tickets)
    SELECT IFNULL(SUM(o.total_amount), 0.00)
    INTO v_subtotal
    FROM orders o
    WHERE o.session_id = p_session_id
      AND o.status <> 'CANCELLED';

    -- 3. Compute tax amount and grand total
    SET v_tax_amount = ROUND(v_subtotal * (v_tax_rate / 100.0), 2);
    SET v_grand_total = v_subtotal + v_tax_amount;

    -- 4. Return structured invoice result set
    SELECT 
        p_session_id AS session_id,
        v_table_num AS table_number,
        v_session_status AS session_status,
        (SELECT COUNT(*) FROM orders WHERE session_id = p_session_id AND status <> 'CANCELLED') AS active_order_count,
        v_subtotal AS subtotal,
        v_tax_rate AS tax_percentage,
        v_tax_amount AS tax_amount,
        v_grand_total AS grand_total;
END //

DELIMITER ;


-- ============================================================================
-- SECTION 4: 10 DEMONSTRATION QUERIES
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Query 1: INNER JOIN (Full Multi-Table Order Details)
-- WHAT: Joins orders, dining_session, restaurant_table, order_item, menu_item,
--       and category to produce an itemized customer receipt.
-- WHY:  Demonstrates relational traversal across the primary business chain.
-- ----------------------------------------------------------------------------
SELECT 
    o.order_id,
    rt.table_number,
    o.status AS order_status,
    c.name AS category,
    mi.name AS dish_name,
    oi.quantity,
    oi.unit_price,
    oi.subtotal,
    oi.item_notes
FROM orders o
JOIN dining_session ds ON o.session_id = ds.session_id
JOIN restaurant_table rt ON ds.table_id = rt.table_id
JOIN order_item oi ON o.order_id = oi.order_id
JOIN menu_item mi ON oi.item_id = mi.item_id
JOIN category c ON mi.category_id = c.category_id
WHERE o.order_id = 5
ORDER BY oi.order_item_id;

-- ----------------------------------------------------------------------------
-- Query 2: LEFT JOIN (Category Menu Distribution)
-- WHAT: Left-joins category to menu_item to count items and calculate average
--       price per category, including categories that have no items.
-- WHY:  Preserves left-side category rows even if no menu items belong to them.
-- ----------------------------------------------------------------------------
SELECT 
    c.category_id,
    c.name AS category_name,
    COUNT(mi.item_id) AS total_items_count,
    SUM(CASE WHEN mi.is_available = TRUE THEN 1 ELSE 0 END) AS active_items_count,
    IFNULL(ROUND(AVG(mi.price), 2), 0.00) AS avg_price,
    IFNULL(MIN(mi.price), 0.00) AS min_price,
    IFNULL(MAX(mi.price), 0.00) AS max_price
FROM category c
LEFT JOIN menu_item mi ON c.category_id = mi.category_id
GROUP BY c.category_id, c.name
ORDER BY c.display_order;

-- ----------------------------------------------------------------------------
-- Query 3: GROUP BY & HAVING (High-Value Dining Sessions)
-- WHAT: Groups orders by dining session and filters for sessions whose aggregate
--       spend exceeds ₹800 using HAVING.
-- WHY:  Demonstrates post-aggregation filtering for high-value patron identification.
-- ----------------------------------------------------------------------------
SELECT 
    ds.session_id,
    rt.table_number,
    ds.status AS session_status,
    COUNT(o.order_id) AS total_orders,
    SUM(o.total_amount) AS session_gross_spend
FROM dining_session ds
JOIN restaurant_table rt ON ds.table_id = rt.table_id
JOIN orders o ON ds.session_id = o.session_id
WHERE o.status <> 'CANCELLED'
GROUP BY ds.session_id, rt.table_number, ds.status
HAVING SUM(o.total_amount) > 800.00
ORDER BY session_gross_spend DESC;

-- ----------------------------------------------------------------------------
-- Query 4: SUBQUERY (Orders Exceeding Restaurant Average)
-- WHAT: Uses a scalar subquery in the WHERE clause to find orders whose total
--       is strictly higher than the overall average order value.
-- WHY:  Standard business benchmark query to identify premium orders.
-- ----------------------------------------------------------------------------
SELECT 
    o.order_id,
    rt.table_number,
    o.status,
    o.total_amount,
    ROUND((SELECT AVG(total_amount) FROM orders WHERE status <> 'CANCELLED'), 2) AS overall_avg_amount,
    ROUND(o.total_amount - (SELECT AVG(total_amount) FROM orders WHERE status <> 'CANCELLED'), 2) AS amount_above_avg
FROM orders o
JOIN dining_session ds ON o.session_id = ds.session_id
JOIN restaurant_table rt ON ds.table_id = rt.table_id
WHERE o.status <> 'CANCELLED'
  AND o.total_amount > (SELECT AVG(total_amount) FROM orders WHERE status <> 'CANCELLED')
ORDER BY o.total_amount DESC;

-- ----------------------------------------------------------------------------
-- Query 5: TOP 5 SELLING ITEMS (Volume & Revenue Ranking)
-- WHAT: Aggregates order_item records by menu dish to rank the top 5 revenue
--       and quantity generators.
-- WHY:  Core sales velocity reporting used by kitchen and inventory management.
-- ----------------------------------------------------------------------------
SELECT 
    mi.item_id,
    mi.name AS item_name,
    c.name AS category,
    mi.item_type,
    SUM(oi.quantity) AS total_units_sold,
    SUM(oi.subtotal) AS total_revenue_generated
FROM order_item oi
JOIN menu_item mi ON oi.item_id = mi.item_id
JOIN category c ON mi.category_id = c.category_id
JOIN orders o ON oi.order_id = o.order_id
WHERE o.status <> 'CANCELLED'
GROUP BY mi.item_id, mi.name, c.name, mi.item_type
ORDER BY total_units_sold DESC, total_revenue_generated DESC
LIMIT 5;

-- ----------------------------------------------------------------------------
-- Query 6: REVENUE PER DAY (Financial Summary)
-- WHAT: Groups orders by calendar date, calculating daily revenue, average
--       spend per order, and cancellation count.
-- WHY:  Fundamental daily financial report required by restaurant managers.
-- ----------------------------------------------------------------------------
SELECT 
    DATE(o.created_at) AS sales_date,
    COUNT(o.order_id) AS total_tickets_placed,
    SUM(CASE WHEN o.status <> 'CANCELLED' THEN 1 ELSE 0 END) AS completed_tickets,
    SUM(CASE WHEN o.status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled_tickets,
    IFNULL(SUM(CASE WHEN o.status <> 'CANCELLED' THEN o.total_amount ELSE 0.00 END), 0.00) AS daily_gross_revenue,
    ROUND(AVG(CASE WHEN o.status <> 'CANCELLED' THEN o.total_amount ELSE NULL END), 2) AS avg_ticket_size
FROM orders o
GROUP BY DATE(o.created_at)
ORDER BY sales_date DESC;

-- ----------------------------------------------------------------------------
-- Query 7: AVERAGE PREPARATION TIME FROM STATUS LOG
-- WHAT: Computes elapsed kitchen duration (minutes between 'ACCEPTED' and 'READY')
--       by self-joining order_status_log transitions for each ticket.
-- WHY:  Key operational performance indicator (KPI) measuring kitchen efficiency.
-- ----------------------------------------------------------------------------
SELECT 
    COUNT(o.order_id) AS orders_measured,
    ROUND(AVG(TIMESTAMPDIFF(MINUTE, l_acc.created_at, l_ready.created_at)), 2) AS avg_kitchen_prep_minutes,
    MIN(TIMESTAMPDIFF(MINUTE, l_acc.created_at, l_ready.created_at)) AS fastest_prep_minutes,
    MAX(TIMESTAMPDIFF(MINUTE, l_acc.created_at, l_ready.created_at)) AS slowest_prep_minutes
FROM orders o
JOIN order_status_log l_acc ON o.order_id = l_acc.order_id AND l_acc.to_status = 'ACCEPTED'
JOIN order_status_log l_ready ON o.order_id = l_ready.order_id AND l_ready.to_status = 'READY';

-- ----------------------------------------------------------------------------
-- Query 8: ITEMS NEVER ORDERED (Menu Engineering / Zero Sales)
-- WHAT: Identifies active menu items that have never appeared in any order_item
--       record using a LEFT JOIN / IS NULL pattern.
-- WHY:  Used in menu engineering to detect dead inventory or dishes to revamp.
-- ----------------------------------------------------------------------------
SELECT 
    mi.item_id,
    mi.name AS unused_dish_name,
    c.name AS category_name,
    mi.item_type,
    mi.price,
    mi.created_at AS added_to_menu_at
FROM menu_item mi
JOIN category c ON mi.category_id = c.category_id
LEFT JOIN order_item oi ON mi.item_id = oi.item_id
WHERE oi.order_item_id IS NULL
ORDER BY mi.item_id;

-- ----------------------------------------------------------------------------
-- Query 9: BUSIEST TABLE (Table Utilization & Turnover)
-- WHAT: Aggregates table sessions, order volume, and gross billing per table.
-- WHY:  Reveals high-demand floor sections, seating capacity efficiency, and server load.
-- ----------------------------------------------------------------------------
SELECT 
    rt.table_id,
    rt.table_number,
    rt.capacity,
    COUNT(DISTINCT ds.session_id) AS sessions_hosted,
    COUNT(o.order_id) AS total_orders_placed,
    IFNULL(SUM(CASE WHEN o.status <> 'CANCELLED' THEN o.total_amount ELSE 0.00 END), 0.00) AS gross_revenue_earned
FROM restaurant_table rt
LEFT JOIN dining_session ds ON rt.table_id = ds.table_id
LEFT JOIN orders o ON ds.session_id = o.session_id
GROUP BY rt.table_id, rt.table_number, rt.capacity
ORDER BY gross_revenue_earned DESC, total_orders_placed DESC;

-- ----------------------------------------------------------------------------
-- Query 10: ACTIVE KITCHEN QUEUE & WAIT TIME MONITOR
-- WHAT: Lists active kitchen orders pending cooking, showing elapsed minutes
--       and items requested, sorted by longest waiting customer first.
-- WHY:  Real-time operational query backing kitchen priority display systems.
-- ----------------------------------------------------------------------------
SELECT 
    o.order_id,
    rt.table_number,
    o.status AS queue_status,
    TIMESTAMPDIFF(MINUTE, o.created_at, NOW()) AS minutes_waiting,
    COUNT(oi.order_item_id) AS total_dishes_ordered,
    SUM(oi.quantity) AS total_food_units,
    o.special_notes
FROM orders o
JOIN dining_session ds ON o.session_id = ds.session_id
JOIN restaurant_table rt ON ds.table_id = rt.table_id
JOIN order_item oi ON o.order_id = oi.order_id
WHERE o.status IN ('PLACED', 'ACCEPTED', 'PREPARING')
GROUP BY o.order_id, rt.table_number, o.status, o.created_at, o.special_notes
ORDER BY minutes_waiting DESC;


-- ============================================================================
-- SECTION 5: TRANSACTION DEMO (ACID COMMIT & ROLLBACK)
-- ============================================================================

-- ----------------------------------------------------------------------------
-- DEMO 1: Successful Order Placement (COMMIT)
-- WHAT: Executes a complete atomic order creation:
--       1. Inserts header record in orders
--       2. Inserts line items in order_item
--       3. Updates orders.total_amount using fn_order_total
--       4. Commits transaction
-- WHY:  Rule 4 specifies: "Placing an order = ONE database transaction."
--       Prevents partial tickets where order headers exist without lines.
-- ----------------------------------------------------------------------------
START TRANSACTION;

-- Step 1: Insert order header
INSERT INTO orders (session_id, status, total_amount, special_notes)
VALUES (1, 'PLACED', 0.00, 'ACID Demo: Customer placed 2 dishes');

SET @committed_order_id = LAST_INSERT_ID();

-- Step 2: Insert order line items (using snapshot unit prices)
INSERT INTO order_item (order_id, item_id, quantity, unit_price, item_notes)
VALUES 
(@committed_order_id, 1,  1, 280.00, 'Crispy roasted'),
(@committed_order_id, 25, 2, 130.00, 'Extra thick mango lassi');

-- Step 3: Compute and update order total amount
UPDATE orders 
SET total_amount = fn_order_total(@committed_order_id)
WHERE order_id = @committed_order_id;

-- Step 4: Commit transaction atomically
COMMIT;

-- Verification of successful commit
SELECT 'Transaction Committed Successfully' AS outcome, order_id, status, total_amount 
FROM orders WHERE order_id = @committed_order_id;


-- ----------------------------------------------------------------------------
-- DEMO 2: Failed Order Placement (ROLLBACK)
-- WHAT: Demonstrates transaction rollback when a mid-order error occurs.
--       Inserts an order header and an item, simulates a business failure
--       (e.g., payment failure or cart abandonment), and rolls back.
-- WHY:  Proves atomicity: the intermediate order and line items are discarded;
--       zero orphan data remains in the database.
-- ----------------------------------------------------------------------------
START TRANSACTION;

-- Step 1: Insert draft order
INSERT INTO orders (session_id, status, total_amount, special_notes)
VALUES (2, 'PLACED', 0.00, 'ACID Demo: This order will be rolled back');

SET @aborted_order_id = LAST_INSERT_ID();

-- Step 2: Insert line item
INSERT INTO order_item (order_id, item_id, quantity, unit_price, item_notes)
VALUES (@aborted_order_id, 14, 2, 35.00, 'Tandoori Roti');

-- Step 3: Simulate failure condition (e.g. payment gateway rejects token)
-- We issue ROLLBACK to abandon all changes made in this unit of work
ROLLBACK;

-- Verification: Confirms no orphan row was committed
SELECT 'Transaction Rolled Back' AS outcome, COUNT(*) AS rows_found_in_orders
FROM orders WHERE order_id = @aborted_order_id;


-- ============================================================================
-- SECTION 6: SECURITY & LEAST-PRIVILEGE USER PROVISIONING (GRANT)
-- ============================================================================

-- ----------------------------------------------------------------------------
-- WHAT: Creates the application user 'restaurant_app' and grants strictly the
--       minimum necessary permissions required to operate the service.
-- WHY:  Follows the Principle of Least Privilege:
--       - DML (SELECT, INSERT, UPDATE, DELETE): Needed by Spring Boot repositories.
--       - EXECUTE: Needed to invoke fn_order_total and sp_generate_bill.
--       - SHOW VIEW: Needed to query and inspect schema views.
--       - NO DDL (CREATE, ALTER, DROP): App cannot drop or alter tables at runtime.
--       - NO GRANT OPTION / SUPER: App cannot elevate privileges or create users.
-- ----------------------------------------------------------------------------

-- Create user with secure password.
-- Replace 'CHANGE_ME' with your own password, then put the SAME value in the
-- git-ignored config/local.properties (see README.md -> "Environment Variables").
CREATE USER IF NOT EXISTS 'restaurant_app'@'localhost' IDENTIFIED BY 'CHANGE_ME';

-- Grant required operational privileges on application database
GRANT SELECT, INSERT, UPDATE, DELETE ON restaurant_db.* TO 'restaurant_app'@'localhost';
GRANT EXECUTE ON restaurant_db.* TO 'restaurant_app'@'localhost';
GRANT SHOW VIEW ON restaurant_db.* TO 'restaurant_app'@'localhost';

-- Apply privilege changes immediately
FLUSH PRIVILEGES;

-- Display active grants to verify security posture
SHOW GRANTS FOR 'restaurant_app'@'localhost';
