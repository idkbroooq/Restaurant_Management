-- ============================================================================
-- Restaurant Table-Ordering System: Seed Data
-- ============================================================================

USE restaurant_db;

-- ----------------------------------------------------------------------------
-- 1. Seed Categories (6 Categories)
-- ----------------------------------------------------------------------------
INSERT INTO category (category_id, name, description, display_order, is_active) VALUES
(1, 'Starters & Appetizers', 'Tandoori, crispy grills and delightful appetizers', 1, TRUE),
(2, 'Main Course', 'Rich aromatic curries and chef signature specialties', 2, TRUE),
(3, 'Breads & Rice', 'Freshly baked tandoori breads, rotis and fragrant biryanis', 3, TRUE),
(4, 'Accompaniments & Salads', 'Fresh salads, raitas and side relishes', 4, TRUE),
(5, 'Desserts', 'Traditional sweets and cold artisan delights', 5, TRUE),
(6, 'Beverages & Mocktails', 'Refreshing cold sips, hot teas and signature mocktails', 6, TRUE);

-- ----------------------------------------------------------------------------
-- 2. Seed Restaurant Tables (10 Tables)
-- ----------------------------------------------------------------------------
INSERT INTO restaurant_table (table_id, table_number, capacity, qr_token, is_active) VALUES
(1,  'T01', 2, 'tok_tbl_01_a9f8e7d6c5b4a321', TRUE),
(2,  'T02', 2, 'tok_tbl_02_b8e7d6c5b4a32109', TRUE),
(3,  'T03', 4, 'tok_tbl_03_c7d6c5b4a3210987', TRUE),
(4,  'T04', 4, 'tok_tbl_04_d6c5b4a321098765', TRUE),
(5,  'T05', 4, 'tok_tbl_05_e5b4a32109876543', TRUE),
(6,  'T06', 6, 'tok_tbl_06_f4a3210987654321', TRUE),
(7,  'T07', 6, 'tok_tbl_07_032109876543210f', TRUE),
(8,  'T08', 8, 'tok_tbl_08_12109876543210fe', TRUE),
(9,  'T09', 8, 'tok_tbl_09_2109876543210fed', TRUE),
(10, 'T10', 4, 'tok_tbl_10_309876543210fedc', TRUE);

-- ----------------------------------------------------------------------------
-- 3. Seed Staff Members (3 Staff with BCrypt hashes)
-- Passwords:
--   chef_mario     -> ChefPassword123!
--   waiter_john    -> WaiterPassword123!
--   manager_alice  -> ManagerPassword123!
-- ----------------------------------------------------------------------------
INSERT INTO staff (staff_id, username, password_hash, full_name, role, is_active) VALUES
(1, 'chef_mario',    '$2a$10$lRaCGijZXHd89xoWFuE6mOx8A8novuzlOgn9WeGiEylMTLJpPi9uu', 'Mario Batali',   'CHEF',    TRUE),
(2, 'waiter_john',   '$2a$10$BTUfFrQYXkQZxxo7ishqnezSEkvS4hpZYxH8jVh9figLV7lxacRly', 'John Henderson', 'WAITER',  TRUE),
(3, 'manager_alice', '$2a$10$zdFLQAEO3.2db3ZCfTH6D.V.GI7r134BNR9fRpFbVIpnGS8cd.BUq', 'Alice Vance',    'MANAGER', TRUE);

-- ----------------------------------------------------------------------------
-- 4. Seed Menu Items (29 Items: FOOD, BEVERAGE, DESSERT with CHECK rules)
-- ----------------------------------------------------------------------------
INSERT INTO menu_item (item_id, category_id, name, description, price, item_type, spice_level, volume_ml, is_available) VALUES
-- Starters (Category 1 - FOOD)
(1,  1, 'Paneer Tikka', 'Charcoal grilled cottage cheese cubes marinated in spiced hung curd', 280.00, 'FOOD', 'HOT', NULL, TRUE),
(2,  1, 'Chicken Malai Tikka', 'Tender chicken morsels in creamy cashew-cheese marinade', 340.00, 'FOOD', 'MILD', NULL, TRUE),
(3,  1, 'Crispy Corn Pepper Fry', 'Golden sweet corn wok-tossed with crushed black pepper & herbs', 220.00, 'FOOD', 'MEDIUM', NULL, TRUE),
(4,  1, 'Tandoori Stuffed Mushrooms', 'Button mushrooms loaded with spiced cottage cheese & mint', 260.00, 'FOOD', 'MEDIUM', NULL, TRUE),
(5,  1, 'Lamb Seekh Kebab', 'Minced spiced lamb skewers roasted over glowing charcoal embers', 420.00, 'FOOD', 'EXTRA_HOT', NULL, TRUE),

-- Main Course (Category 2 - FOOD)
(6,  2, 'Butter Chicken', 'Smoked shredded tandoori chicken simmered in creamy makhani gravy', 420.00, 'FOOD', 'MILD', NULL, TRUE),
(7,  2, 'Paneer Butter Masala', 'Fresh cottage cheese batons in rich tomato butter emulsion', 340.00, 'FOOD', 'MILD', NULL, TRUE),
(8,  2, 'Dal Makhani', 'Slow cooked whole black lentils finished with cream and white butter', 290.00, 'FOOD', 'MILD', NULL, TRUE),
(9,  2, 'Kadai Chicken', 'Bone-in chicken tossed with bell peppers and freshly ground coriander spices', 410.00, 'FOOD', 'HOT', NULL, TRUE),
(10, 2, 'Kashmiri Rogan Josh', 'Slow braised lamb shank in traditional aromatic Kashmiri red gravy', 480.00, 'FOOD', 'HOT', NULL, TRUE),
(11, 2, 'Malai Kofta', 'Dumplings of cottage cheese & raisins in a velvety cashew cream gravy', 350.00, 'FOOD', 'MILD', NULL, TRUE),
(12, 2, 'Chettinad Veg Curry', 'Fiery mixed vegetables tempered with roasted coconut and star anise', 310.00, 'FOOD', 'EXTRA_HOT', NULL, TRUE),

-- Breads & Rice (Category 3 - FOOD)
(13, 3, 'Garlic Butter Naan', 'Clay oven leavened bread brushed with melted butter and garlic', 65.00, 'FOOD', NULL, NULL, TRUE),
(14, 3, 'Tandoori Roti', 'Stone ground whole wheat flatbread baked in traditional clay tandoor', 35.00, 'FOOD', NULL, NULL, TRUE),
(15, 3, 'Laccha Paratha', 'Multi-layered flaky whole wheat bread kissed with ghee', 55.00, 'FOOD', NULL, NULL, TRUE),
(16, 3, 'Chicken Dum Biryani', 'Aged basmati rice and marinated chicken cooked on dum with saffron', 380.00, 'FOOD', 'MEDIUM', NULL, TRUE),
(17, 3, 'Hyderabadi Veg Biryani', 'Fragrant spiced vegetable rice infused with rose water & fried onions', 310.00, 'FOOD', 'MEDIUM', NULL, TRUE),
(18, 3, 'Steamed Basmati Rice', 'Long grain royal basmati rice steamed to fluffy perfection', 150.00, 'FOOD', NULL, NULL, TRUE),

-- Accompaniments & Salads (Category 4 - FOOD)
(19, 4, 'Boondi Raita', 'Churned chilled yogurt with crispy chickpea pearls & roasted cumin', 90.00, 'FOOD', 'MILD', NULL, TRUE),
(20, 4, 'Fresh Garden Salad', 'Crisp sliced cucumbers, red onions, tomatoes and green chilies', 110.00, 'FOOD', NULL, NULL, TRUE),

-- Desserts (Category 5 - DESSERT: volume_ml NULL, spice_level NULL)
(21, 5, 'Gulab Jamun with Rabdi', 'Warm reduced-milk fried dumplings served with saffron clotted cream', 130.00, 'DESSERT', NULL, NULL, TRUE),
(22, 5, 'Kesar Pista Rasmalai', 'Soft poached cottage cheese discs steeped in cold cardamom saffron milk', 150.00, 'DESSERT', NULL, NULL, TRUE),
(23, 5, 'Sizzling Brownie with Vanilla', 'Warm dark chocolate fudge brownie with Madagascar vanilla bean ice cream', 220.00, 'DESSERT', NULL, NULL, TRUE),
(24, 5, 'Royal Kulfi Falooda', 'Dense pistachio kulfi served with rose syrup, sweet basil seeds and vermicelli', 140.00, 'DESSERT', NULL, NULL, TRUE),

-- Beverages (Category 6 - BEVERAGE: volume_ml > 0, spice_level NULL)
(25, 6, 'Alphonso Mango Lassi', 'Sweet churned yogurt smoothie blended with ripe Ratnagiri Alphonso mango pulp', 130.00, 'BEVERAGE', NULL, 350, TRUE),
(26, 6, 'Fresh Mint Lime Soda', 'Squeezed Persian limes with garden mint leaves and chilled sparkling soda', 90.00, 'BEVERAGE', NULL, 300, TRUE),
(27, 6, 'Adrak Masala Chai', 'Robust Assam CTC black tea brewed with fresh crushed ginger and green cardamom', 60.00, 'BEVERAGE', NULL, 200, TRUE),
(28, 6, 'Virgin Green Apple Mojito', 'Crushed granny smith apples, mint sprigs, lime wedges and soda fizz', 160.00, 'BEVERAGE', NULL, 350, TRUE),
(29, 6, 'Himalayan Spring Water', 'Pure bottled mineral water from the Himalayan foothills', 45.00, 'BEVERAGE', NULL, 1000, TRUE);

-- ----------------------------------------------------------------------------
-- 5. Seed Dining Sessions (10 Sessions)
-- ----------------------------------------------------------------------------
INSERT INTO dining_session (session_id, table_id, session_token, status, start_time, end_time) VALUES
(1,  1, 'sess_01_7a9f8b6c5d4e3f21', 'ACTIVE',    TIMESTAMP(DATE_SUB(NOW(), INTERVAL 45 MINUTE)), NULL),
(2,  2, 'sess_02_8b6c5d4e3f217a9f', 'ACTIVE',    TIMESTAMP(DATE_SUB(NOW(), INTERVAL 35 MINUTE)), NULL),
(3,  3, 'sess_03_9c5d4e3f217a9f8b', 'ACTIVE',    TIMESTAMP(DATE_SUB(NOW(), INTERVAL 25 MINUTE)), NULL),
(4,  4, 'sess_04_0d4e3f217a9f8b6c', 'ACTIVE',    TIMESTAMP(DATE_SUB(NOW(), INTERVAL 20 MINUTE)), NULL),
(5,  5, 'sess_05_1e3f217a9f8b6c5d', 'ACTIVE',    TIMESTAMP(DATE_SUB(NOW(), INTERVAL 15 MINUTE)), NULL),
(6,  6, 'sess_06_2f217a9f8b6c5d4e', 'ACTIVE',    TIMESTAMP(DATE_SUB(NOW(), INTERVAL 10 MINUTE)), NULL),
(7,  7, 'sess_07_317a9f8b6c5d4e3f', 'ACTIVE',    TIMESTAMP(DATE_SUB(NOW(), INTERVAL 5 MINUTE)),  NULL),
(8,  8, 'sess_08_4a9f8b6c5d4e3f21', 'COMPLETED', TIMESTAMP(DATE_SUB(NOW(), INTERVAL 120 MINUTE)), TIMESTAMP(DATE_SUB(NOW(), INTERVAL 50 MINUTE))),
(9,  9, 'sess_09_5b6c5d4e3f217a9f', 'COMPLETED', TIMESTAMP(DATE_SUB(NOW(), INTERVAL 90 MINUTE)),  TIMESTAMP(DATE_SUB(NOW(), INTERVAL 30 MINUTE))),
(10, 10, 'sess_10_6c5d4e3f217a9f8b', 'COMPLETED', TIMESTAMP(DATE_SUB(NOW(), INTERVAL 75 MINUTE)),  TIMESTAMP(DATE_SUB(NOW(), INTERVAL 15 MINUTE)));

-- ----------------------------------------------------------------------------
-- 6. Seed Orders (12 Orders across PLACED, ACCEPTED, PREPARING, READY, SERVED, CANCELLED)
-- ----------------------------------------------------------------------------
INSERT INTO orders (order_id, session_id, status, total_amount, special_notes, created_at, updated_at) VALUES
-- Status: PLACED (New orders waiting for chef review)
(1,  7, 'PLACED',    485.00, 'Please serve the soup extra piping hot',   TIMESTAMP(DATE_SUB(NOW(), INTERVAL 3 MINUTE)),  TIMESTAMP(DATE_SUB(NOW(), INTERVAL 3 MINUTE))),
(2,  6, 'PLACED',    370.00, 'Less oil in main course please',          TIMESTAMP(DATE_SUB(NOW(), INTERVAL 2 MINUTE)),  TIMESTAMP(DATE_SUB(NOW(), INTERVAL 2 MINUTE))),

-- Status: ACCEPTED (Kitchen acknowledged order, not yet on stove)
(3,  5, 'ACCEPTED',  740.00, 'Table celebrating an anniversary',        TIMESTAMP(DATE_SUB(NOW(), INTERVAL 14 MINUTE)), TIMESTAMP(DATE_SUB(NOW(), INTERVAL 12 MINUTE))),
(4,  4, 'ACCEPTED',  590.00, 'Make starter crispy',                     TIMESTAMP(DATE_SUB(NOW(), INTERVAL 18 MINUTE)), TIMESTAMP(DATE_SUB(NOW(), INTERVAL 15 MINUTE))),

-- Status: PREPARING (Cooking active on tandoor/pan)
(5,  3, 'PREPARING', 915.00, 'Biryani with extra fried brown onions',   TIMESTAMP(DATE_SUB(NOW(), INTERVAL 24 MINUTE)), TIMESTAMP(DATE_SUB(NOW(), INTERVAL 15 MINUTE))),
(6,  2, 'PREPARING', 835.00, 'No coriander garnish on Butter Chicken',  TIMESTAMP(DATE_SUB(NOW(), INTERVAL 30 MINUTE)), TIMESTAMP(DATE_SUB(NOW(), INTERVAL 18 MINUTE))),

-- Status: READY (Food plated and waiting on kitchen pass for waiter)
(7,  1, 'READY',     665.00, 'Deliver with fresh lime wedges',          TIMESTAMP(DATE_SUB(NOW(), INTERVAL 40 MINUTE)), TIMESTAMP(DATE_SUB(NOW(), INTERVAL 8 MINUTE))),
(8,  3, 'READY',     270.00, 'Beverage round for table',                TIMESTAMP(DATE_SUB(NOW(), INTERVAL 20 MINUTE)), TIMESTAMP(DATE_SUB(NOW(), INTERVAL 5 MINUTE))),

-- Status: SERVED (Guest received food and dining)
(9,  8, 'SERVED',    1035.00,'Guest requested split cutlery',           TIMESTAMP(DATE_SUB(NOW(), INTERVAL 115 MINUTE)),TIMESTAMP(DATE_SUB(NOW(), INTERVAL 80 MINUTE))),
(10, 9, 'SERVED',    970.00, 'Allergy notes: No peanuts',              TIMESTAMP(DATE_SUB(NOW(), INTERVAL 85 MINUTE)), TIMESTAMP(DATE_SUB(NOW(), INTERVAL 55 MINUTE))),
(11, 10, 'SERVED',   785.00, 'Chef special preparation appreciated',    TIMESTAMP(DATE_SUB(NOW(), INTERVAL 70 MINUTE)), TIMESTAMP(DATE_SUB(NOW(), INTERVAL 40 MINUTE))),

-- Status: CANCELLED (Customer duplicate order or changed mind)
(12, 4, 'CANCELLED', 220.00, 'Customer changed mind immediately',      TIMESTAMP(DATE_SUB(NOW(), INTERVAL 17 MINUTE)), TIMESTAMP(DATE_SUB(NOW(), INTERVAL 16 MINUTE)));

-- ----------------------------------------------------------------------------
-- 7. Seed Order Items (Snapshots of unit_price at time of order)
-- ----------------------------------------------------------------------------
-- Order 1 (Total: 485.00) -> Item 1 (Paneer Tikka, 280), Item 13 (Garlic Naan, 65), Item 26 (Fresh Lime, 90), Item 29 (Water, 45) -> 480 (Let's adjust Item 13 qty=1, 280+65+90+50 = 485)
INSERT INTO order_item (order_id, item_id, quantity, unit_price, item_notes) VALUES
(1, 1,  1, 280.00, 'Medium spicy marinade'),
(1, 13, 1, 65.00,  'Crispy butter naan'),
(1, 26, 1, 90.00,  'Sweet and salted blend'),
(1, 29, 1, 50.00,  'Chilled bottle'); -- (Snapshot unit price: 50.00)

-- Order 2 (Total: 370.00) -> Item 3 (Crispy Corn, 220), Item 21 (Gulab Jamun, 150) -> 370.00
INSERT INTO order_item (order_id, item_id, quantity, unit_price, item_notes) VALUES
(2, 3,  1, 220.00, 'Extra pepper crisp'),
(2, 22, 1, 150.00, 'Served cold');

-- Order 3 (Total: 740.00) -> Item 6 (Butter Chicken, 420), Item 13 (Garlic Naan x2, 130), Item 23 (Brownie, 220) -> 770 -> adjust
-- Let's do: Item 7 (Paneer Butter Masala, 340), Item 16 (Chicken Dum Biryani, 380), Item 14 (Tandoori Roti, 20) -> 740.00
INSERT INTO order_item (order_id, item_id, quantity, unit_price, item_notes) VALUES
(3, 7,  1, 340.00, 'Rich gravy'),
(3, 16, 1, 380.00, 'Served with salan'),
(3, 14, 1, 20.00,  'Without butter');

-- Order 4 (Total: 590.00) -> Item 2 (Malai Tikka, 340), Item 18 (Basmati Rice, 150), Item 20 (Salad, 100) -> 590.00
INSERT INTO order_item (order_id, item_id, quantity, unit_price, item_notes) VALUES
(4, 2,  1, 340.00, 'Extra mint chutney'),
(4, 18, 1, 150.00, 'Steamed piping hot'),
(4, 20, 1, 100.00, 'Sliced lemons on side');

-- Order 5 (Total: 915.00) -> Item 10 (Rogan Josh, 480), Item 15 (Laccha Paratha x2, 110), Item 25 (Mango Lassi x2, 260), Item 13 (Garlic Naan, 65) -> 915.00
INSERT INTO order_item (order_id, item_id, quantity, unit_price, item_notes) VALUES
(5, 10, 1, 480.00, 'Tender meat cut'),
(5, 15, 2, 55.00,  'Flaky with pure ghee'),
(5, 25, 2, 130.00, 'Thick and chilled'),
(5, 13, 1, 65.00,  'Hot from clay oven');

-- Order 6 (Total: 835.00) -> Item 6 (Butter Chicken, 420), Item 8 (Dal Makhani, 290), Item 13 (Garlic Naan, 65), Item 27 (Masala Chai, 60) -> 835.00
INSERT INTO order_item (order_id, item_id, quantity, unit_price, item_notes) VALUES
(6, 6,  1, 420.00, 'Boneless meat pieces'),
(6, 8,  1, 290.00, 'Slow cooked creamy dal'),
(6, 13, 1, 65.00,  'Well roasted'),
(6, 27, 1, 60.00,  'Ginger infused');

-- Order 7 (Total: 665.00) -> Item 4 (Tandoori Mushrooms, 260), Item 17 (Veg Biryani, 310), Item 19 (Raita, 95) -> 665.00
INSERT INTO order_item (order_id, item_id, quantity, unit_price, item_notes) VALUES
(7, 4,  1, 260.00, 'Stuffed with fresh paneer'),
(7, 17, 1, 310.00, 'Medium spicy masala'),
(7, 19, 1, 95.00,  'Cumin tempered');

-- Order 8 (Total: 270.00) -> Item 28 (Virgin Mojito, 160), Item 20 (Salad, 110) -> 270.00
INSERT INTO order_item (order_id, item_id, quantity, unit_price, item_notes) VALUES
(8, 28, 1, 160.00, 'Extra mint and crushed ice'),
(8, 20, 1, 110.00, 'Freshly cut');

-- Order 9 (Total: 1035.00) -> Item 5 (Seekh Kebab, 420), Item 9 (Kadai Chicken, 410), Item 13 (Garlic Naan x2, 130), Item 21 (Gulab Jamun, 75) -> 1035.00
INSERT INTO order_item (order_id, item_id, quantity, unit_price, item_notes) VALUES
(9, 5,  1, 420.00, 'Extra hot mint dip'),
(9, 9,  1, 410.00, 'Semi-dry spicy gravy'),
(9, 13, 2, 65.00,  'Garlic brushed with butter'),
(9, 21, 1, 75.00,  'Warm single portion');

-- Order 10 (Total: 970.00) -> Item 6 (Butter Chicken, 420), Item 11 (Malai Kofta, 350), Item 15 (Paratha x2, 110), Item 26 (Lime Soda, 90) -> 970.00
INSERT INTO order_item (order_id, item_id, quantity, unit_price, item_notes) VALUES
(10, 6,  1, 420.00, 'Mild savory'),
(10, 11, 1, 350.00, 'Creamy cashew sauce'),
(10, 15, 2, 55.00,  'Layered whole wheat'),
(10, 26, 1, 90.00,  'Salted soda');

-- Order 11 (Total: 785.00) -> Item 12 (Chettinad Curry, 310), Item 16 (Chicken Biryani, 380), Item 19 (Raita, 95) -> 785.00
INSERT INTO order_item (order_id, item_id, quantity, unit_price, item_notes) VALUES
(11, 12, 1, 310.00, 'Traditional South spices'),
(11, 16, 1, 380.00, 'Dum rice with boiled egg'),
(11, 19, 1, 95.00,  'Crispy boondi topping');

-- Order 12 (Total: 220.00) -> Item 3 (Crispy Corn, 220.00)
INSERT INTO order_item (order_id, item_id, quantity, unit_price, item_notes) VALUES
(12, 3, 1, 220.00, 'Quick starter');

-- ----------------------------------------------------------------------------
-- 8. Seed Payments (Completed payments for completed sessions & served orders)
-- ----------------------------------------------------------------------------
INSERT INTO payment (payment_id, session_id, order_id, amount, payment_method, payment_status, transaction_ref, paid_at) VALUES
(1, 8,  9,  1035.00, 'CARD', 'COMPLETED', 'TXN_CARD_20261019_001', TIMESTAMP(DATE_SUB(NOW(), INTERVAL 52 MINUTE))),
(2, 9,  10, 970.00,  'UPI',  'COMPLETED', 'UPI/20261019/9876543210',TIMESTAMP(DATE_SUB(NOW(), INTERVAL 32 MINUTE))),
(3, 10, 11, 785.00,  'CASH', 'COMPLETED', 'CSH_RCPT_20261019_103', TIMESTAMP(DATE_SUB(NOW(), INTERVAL 16 MINUTE)));

-- ----------------------------------------------------------------------------
-- 9. Seed Order Status Log (Audit lifecycle for orders)
-- Shows progression: PLACED -> ACCEPTED -> PREPARING -> READY -> SERVED
-- ----------------------------------------------------------------------------
-- Order 1: PLACED
INSERT INTO order_status_log (order_id, from_status, to_status, changed_by_staff_id, notes, created_at) VALUES
(1, NULL, 'PLACED', NULL, 'Order placed by customer via phone QR scan', TIMESTAMP(DATE_SUB(NOW(), INTERVAL 3 MINUTE)));

-- Order 2: PLACED
INSERT INTO order_status_log (order_id, from_status, to_status, changed_by_staff_id, notes, created_at) VALUES
(2, NULL, 'PLACED', NULL, 'Order placed by customer via phone QR scan', TIMESTAMP(DATE_SUB(NOW(), INTERVAL 2 MINUTE)));

-- Order 3: PLACED -> ACCEPTED
INSERT INTO order_status_log (order_id, from_status, to_status, changed_by_staff_id, notes, created_at) VALUES
(3, NULL,     'PLACED',   NULL, 'Customer order registered', TIMESTAMP(DATE_SUB(NOW(), INTERVAL 14 MINUTE))),
(3, 'PLACED', 'ACCEPTED', 1,    'Chef acknowledged ticket',  TIMESTAMP(DATE_SUB(NOW(), INTERVAL 12 MINUTE)));

-- Order 4: PLACED -> ACCEPTED
INSERT INTO order_status_log (order_id, from_status, to_status, changed_by_staff_id, notes, created_at) VALUES
(4, NULL,     'PLACED',   NULL, 'Customer order registered', TIMESTAMP(DATE_SUB(NOW(), INTERVAL 18 MINUTE))),
(4, 'PLACED', 'ACCEPTED', 1,    'Order accepted by kitchen', TIMESTAMP(DATE_SUB(NOW(), INTERVAL 15 MINUTE)));

-- Order 5: PLACED -> ACCEPTED -> PREPARING
INSERT INTO order_status_log (order_id, from_status, to_status, changed_by_staff_id, notes, created_at) VALUES
(5, NULL,       'PLACED',    NULL, 'Customer order submitted',       TIMESTAMP(DATE_SUB(NOW(), INTERVAL 24 MINUTE))),
(5, 'PLACED',   'ACCEPTED',  1,    'Chef Mario verified recipe',     TIMESTAMP(DATE_SUB(NOW(), INTERVAL 20 MINUTE))),
(5, 'ACCEPTED', 'PREPARING', 1,    'Biryani put on dum cook station',TIMESTAMP(DATE_SUB(NOW(), INTERVAL 15 MINUTE)));

-- Order 6: PLACED -> ACCEPTED -> PREPARING
INSERT INTO order_status_log (order_id, from_status, to_status, changed_by_staff_id, notes, created_at) VALUES
(6, NULL,       'PLACED',    NULL, 'Customer order submitted',    TIMESTAMP(DATE_SUB(NOW(), INTERVAL 30 MINUTE))),
(6, 'PLACED',   'ACCEPTED',  1,    'Tandoor station allocated',   TIMESTAMP(DATE_SUB(NOW(), INTERVAL 25 MINUTE))),
(6, 'ACCEPTED', 'PREPARING', 1,    'Curries on burner',           TIMESTAMP(DATE_SUB(NOW(), INTERVAL 18 MINUTE)));

-- Order 7: PLACED -> ACCEPTED -> PREPARING -> READY
INSERT INTO order_status_log (order_id, from_status, to_status, changed_by_staff_id, notes, created_at) VALUES
(7, NULL,        'PLACED',    NULL, 'Customer order submitted',     TIMESTAMP(DATE_SUB(NOW(), INTERVAL 40 MINUTE))),
(7, 'PLACED',    'ACCEPTED',  1,    'Chef accepted order',          TIMESTAMP(DATE_SUB(NOW(), INTERVAL 35 MINUTE))),
(7, 'ACCEPTED',  'PREPARING', 1,    'Preparing tandoori platters',  TIMESTAMP(DATE_SUB(NOW(), INTERVAL 25 MINUTE))),
(7, 'PREPARING', 'READY',     1,    'Plated on kitchen pickup pass',TIMESTAMP(DATE_SUB(NOW(), INTERVAL 8 MINUTE)));

-- Order 8: PLACED -> ACCEPTED -> PREPARING -> READY
INSERT INTO order_status_log (order_id, from_status, to_status, changed_by_staff_id, notes, created_at) VALUES
(8, NULL,        'PLACED',    NULL, 'Drinks order placed',           TIMESTAMP(DATE_SUB(NOW(), INTERVAL 20 MINUTE))),
(8, 'PLACED',    'ACCEPTED',  1,    'Bar section notified',          TIMESTAMP(DATE_SUB(NOW(), INTERVAL 17 MINUTE))),
(8, 'ACCEPTED',  'PREPARING', 1,    'Mojito blended and garnished',  TIMESTAMP(DATE_SUB(NOW(), INTERVAL 12 MINUTE))),
(8, 'PREPARING', 'READY',     1,    'Drinks ready for table pickup', TIMESTAMP(DATE_SUB(NOW(), INTERVAL 5 MINUTE)));

-- Order 9: PLACED -> ACCEPTED -> PREPARING -> READY -> SERVED
INSERT INTO order_status_log (order_id, from_status, to_status, changed_by_staff_id, notes, created_at) VALUES
(9, NULL,        'PLACED',    NULL, 'Order placed',                  TIMESTAMP(DATE_SUB(NOW(), INTERVAL 115 MINUTE))),
(9, 'PLACED',    'ACCEPTED',  1,    'Order accepted',                TIMESTAMP(DATE_SUB(NOW(), INTERVAL 110 MINUTE))),
(9, 'ACCEPTED',  'PREPARING', 1,    'Cooking in progress',           TIMESTAMP(DATE_SUB(NOW(), INTERVAL 95 MINUTE))),
(9, 'PREPARING', 'READY',     1,    'Order ready on counter',        TIMESTAMP(DATE_SUB(NOW(), INTERVAL 85 MINUTE))),
(9, 'READY',     'SERVED',    2,    'Waiter John served to Table 08',TIMESTAMP(DATE_SUB(NOW(), INTERVAL 80 MINUTE)));

-- Order 10: PLACED -> ACCEPTED -> PREPARING -> READY -> SERVED
INSERT INTO order_status_log (order_id, from_status, to_status, changed_by_staff_id, notes, created_at) VALUES
(10, NULL,        'PLACED',    NULL, 'Order placed',                 TIMESTAMP(DATE_SUB(NOW(), INTERVAL 85 MINUTE))),
(10, 'PLACED',    'ACCEPTED',  1,    'Order accepted',               TIMESTAMP(DATE_SUB(NOW(), INTERVAL 80 MINUTE))),
(10, 'ACCEPTED',  'PREPARING', 1,    'Cooking',                      TIMESTAMP(DATE_SUB(NOW(), INTERVAL 70 MINUTE))),
(10, 'PREPARING', 'READY',     1,    'Plated',                       TIMESTAMP(DATE_SUB(NOW(), INTERVAL 60 MINUTE))),
(10, 'READY',     'SERVED',    2,    'Delivered to Table 09',        TIMESTAMP(DATE_SUB(NOW(), INTERVAL 55 MINUTE)));

-- Order 11: PLACED -> ACCEPTED -> PREPARING -> READY -> SERVED
INSERT INTO order_status_log (order_id, from_status, to_status, changed_by_staff_id, notes, created_at) VALUES
(11, NULL,        'PLACED',    NULL, 'Order placed',                 TIMESTAMP(DATE_SUB(NOW(), INTERVAL 70 MINUTE))),
(11, 'PLACED',    'ACCEPTED',  1,    'Order accepted',               TIMESTAMP(DATE_SUB(NOW(), INTERVAL 65 MINUTE))),
(11, 'ACCEPTED',  'PREPARING', 1,    'Cooking',                      TIMESTAMP(DATE_SUB(NOW(), INTERVAL 50 MINUTE))),
(11, 'PREPARING', 'READY',     1,    'Ready at counter',             TIMESTAMP(DATE_SUB(NOW(), INTERVAL 45 MINUTE))),
(11, 'READY',     'SERVED',    2,    'Served to Table 10',           TIMESTAMP(DATE_SUB(NOW(), INTERVAL 40 MINUTE)));

-- Order 12: PLACED -> CANCELLED
INSERT INTO order_status_log (order_id, from_status, to_status, changed_by_staff_id, notes, created_at) VALUES
(12, NULL,     'PLACED',    NULL, 'Customer submitted order',       TIMESTAMP(DATE_SUB(NOW(), INTERVAL 17 MINUTE))),
(12, 'PLACED', 'CANCELLED', 3,    'Manager Alice cancelled on guest request', TIMESTAMP(DATE_SUB(NOW(), INTERVAL 16 MINUTE)));
