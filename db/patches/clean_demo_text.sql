-- ============================================================================
-- OPTIONAL / NOT EXECUTED — review before running.
-- clean_demo_text.sql
-- ----------------------------------------------------------------------------
-- WHY
--   The seed and the ACID/transaction demo scripts leave a few user-visible
--   strings that read like test scaffolding. They surface to staff on the
--   kitchen board (order "Guest note" blocks) and in the admin console.
--
--   Confirmed offenders in a freshly seeded + advanced.sql database:
--     * orders.order_id 13  special_notes = 'ACID Demo: Customer placed 2 dishes'
--           -> inserted by db/advanced.sql SECTION 5 "DEMO 1" (COMMIT), so it
--              persists. It shows up on the kitchen board as a guest note.
--     * orders.order_id 15  special_notes = 'local smoke test'
--           -> left behind by manual smoke testing, not by any seed file.
--
--   NOT offenders (do not touch):
--     * menu_item "Gulab Jamun with Rabdi" — "Gulab" merely CONTAINS the
--       letters "lab". The word-boundary match below deliberately ignores it.
--
-- WHAT THIS DOES
--   NULLs out any user-visible free-text string whose words include
--   demo / test / acid / lab / project. It only ever clears text; it never
--   changes ids, prices, statuses, quantities or the QR tokens.
--
-- DOES NOT TOUCH
--   db/schema.sql, db/advanced.sql, any Java code, or the app itself.
--
-- HOW TO RUN (after reviewing)
--   mysql -u root -p < db/patches/clean_demo_text.sql
--
--   If your database name is not restaurant_db, either edit the USE below or
--   pass it on the command line:  mysql -u root -p restaurant_db < ...
-- ============================================================================

USE restaurant_db;

-- ----------------------------------------------------------------------------
-- 0. BEFORE — report every user-visible string that will be cleared.
--    Expect: orders 13 and 15 (kept by advanced.sql / manual testing).
-- ----------------------------------------------------------------------------
SELECT 'orders.special_notes' AS location, order_id AS ref, special_notes AS offending_text
FROM orders
WHERE special_notes REGEXP '\\b(demo|test|acid|lab|project)\\b'
UNION ALL
SELECT 'order_item.item_notes', order_id, item_notes
FROM order_item
WHERE item_notes REGEXP '\\b(demo|test|acid|lab|project)\\b'
UNION ALL
SELECT 'menu_item.description', item_id, description
FROM menu_item
WHERE description REGEXP '\\b(demo|test|acid|lab|project)\\b'
UNION ALL
SELECT 'menu_item.name', item_id, name
FROM menu_item
WHERE name REGEXP '\\b(demo|test|acid|lab|project)\\b'
UNION ALL
SELECT 'category.description', category_id, description
FROM category
WHERE description REGEXP '\\b(demo|test|acid|lab|project)\\b'
UNION ALL
SELECT 'order_status_log.notes', log_id, notes
FROM order_status_log
WHERE notes REGEXP '\\b(demo|test|acid|lab|project)\\b';

-- ----------------------------------------------------------------------------
-- 1. CLEAN — clear the offending free-text fields (transaction you can undo).
-- ----------------------------------------------------------------------------
START TRANSACTION;

UPDATE orders
SET special_notes = NULL
WHERE special_notes REGEXP '\\b(demo|test|acid|lab|project)\\b';

UPDATE order_item
SET item_notes = NULL
WHERE item_notes REGEXP '\\b(demo|test|acid|lab|project)\\b';

UPDATE menu_item
SET description = NULL
WHERE description REGEXP '\\b(demo|test|acid|lab|project)\\b';

-- Menu/category NAMES are user-facing and must stay non-null, so they are only
-- reported above, never blanket-cleared. Fix any hit by hand:
--   UPDATE category SET name = '...'       WHERE category_id = <id>;
--   UPDATE menu_item SET name = '...'      WHERE item_id = <id>;

UPDATE category
SET description = NULL
WHERE description REGEXP '\\b(demo|test|acid|lab|project)\\b';

UPDATE order_status_log
SET notes = NULL
WHERE notes REGEXP '\\b(demo|test|acid|lab|project)\\b';

COMMIT;
-- Prefer to inspect first? Replace the COMMIT above with ROLLBACK to discard.

-- ----------------------------------------------------------------------------
-- 2. AFTER — verification. Every query below must return zero rows.
-- ----------------------------------------------------------------------------
SELECT COUNT(*) AS remaining_orders_hits FROM orders
WHERE special_notes REGEXP '\\b(demo|test|acid|lab|project)\\b'
   OR special_notes LIKE '%ACID Demo%'
   OR special_notes LIKE '%smoke test%';

SELECT COUNT(*) AS remaining_item_notes_hits FROM order_item
WHERE item_notes REGEXP '\\b(demo|test|acid|lab|project)\\b';

SELECT COUNT(*) AS remaining_menu_hits FROM menu_item
WHERE name REGEXP '\\b(demo|test|acid|lab|project)\\b'
   OR description REGEXP '\\b(demo|test|acid|lab|project)\\b';

SELECT COUNT(*) AS remaining_category_hits FROM category
WHERE name REGEXP '\\b(demo|test|acid|lab|project)\\b'
   OR description REGEXP '\\b(demo|test|acid|lab|project)\\b';

SELECT COUNT(*) AS remaining_log_hits FROM order_status_log
WHERE notes REGEXP '\\b(demo|test|acid|lab|project)\\b';
