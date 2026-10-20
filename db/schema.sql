-- ============================================================================
-- Restaurant Table-Ordering System: Database Schema (MySQL 8)
-- Stack: Java 17, Spring Boot 3, Spring JDBC (JdbcTemplate), MySQL 8
-- Charset: utf8mb4 | Collation: utf8mb4_unicode_ci
-- Re-runnable: Drops tables in reverse dependency order before recreation.
-- ============================================================================

CREATE DATABASE IF NOT EXISTS restaurant_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE restaurant_db;

-- ----------------------------------------------------------------------------
-- Drop tables in reverse topological order (children before parents)
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS order_status_log;
DROP TABLE IF EXISTS payment;
DROP TABLE IF EXISTS order_item;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS dining_session;
DROP TABLE IF EXISTS staff;
DROP TABLE IF EXISTS menu_item;
DROP TABLE IF EXISTS category;
DROP TABLE IF EXISTS restaurant_table;

-- ----------------------------------------------------------------------------
-- Table 1: restaurant_table
-- Physical restaurant tables with unique QR scan tokens for phone ordering.
-- ----------------------------------------------------------------------------
CREATE TABLE restaurant_table (
    table_id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    table_number VARCHAR(20) NOT NULL,
    capacity INT UNSIGNED NOT NULL DEFAULT 4,
    qr_token VARCHAR(64) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_restaurant_table_number UNIQUE (table_number),
    CONSTRAINT uq_restaurant_table_qr_token UNIQUE (qr_token),
    CONSTRAINT chk_restaurant_table_capacity CHECK (capacity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- Table 2: category
-- Menu hierarchy classification (Starters, Mains, Desserts, Beverages, etc.).
-- ----------------------------------------------------------------------------
CREATE TABLE category (
    category_id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    description VARCHAR(255) NULL,
    display_order INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_category_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- Table 3: menu_item
-- Items available for order. Includes domain validation per item_type.
-- ----------------------------------------------------------------------------
CREATE TABLE menu_item (
    item_id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    category_id INT UNSIGNED NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT NULL,
    price DECIMAL(10,2) NOT NULL,
    item_type ENUM('FOOD', 'BEVERAGE', 'DESSERT') NOT NULL,
    spice_level ENUM('MILD', 'MEDIUM', 'HOT', 'EXTRA_HOT') NULL,
    volume_ml INT UNSIGNED NULL,
    is_available BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,  -- soft delete; never hard-delete items with order history
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_menu_item_name UNIQUE (name),
    CONSTRAINT chk_menu_item_price CHECK (price > 0.00),
    -- Type-specific attribute check:
    -- FOOD: volume_ml must be NULL, spice_level can be NULL or set.
    -- BEVERAGE: volume_ml must be set (>0), spice_level must be NULL.
    -- DESSERT: volume_ml and spice_level must both be NULL.
    CONSTRAINT chk_menu_item_type_attributes CHECK (
        (item_type = 'FOOD' AND volume_ml IS NULL) OR
        (item_type = 'BEVERAGE' AND volume_ml IS NOT NULL AND volume_ml > 0 AND spice_level IS NULL) OR
        (item_type = 'DESSERT' AND spice_level IS NULL AND volume_ml IS NULL)
    ),
    CONSTRAINT fk_menu_item_category FOREIGN KEY (category_id)
        REFERENCES category (category_id)
        ON DELETE RESTRICT ON UPDATE CASCADE -- Prevent deleting category with active menu items
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ----------------------------------------------------------------------------
-- Table 4: staff
-- Restaurant employees (chefs, waiters, managers) with BCrypt-hashed credentials.
-- ----------------------------------------------------------------------------
CREATE TABLE staff (
    staff_id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    role ENUM('CHEF', 'WAITER', 'MANAGER') NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_staff_username UNIQUE (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- Table 5: dining_session
-- Represents an active dining visit at a table from guest seating to bill payment.
-- Enables customers to place multiple incremental orders during one sitting.
-- ----------------------------------------------------------------------------
CREATE TABLE dining_session (
    session_id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    table_id INT UNSIGNED NOT NULL,
    session_token VARCHAR(64) NOT NULL,
    status ENUM('ACTIVE', 'COMPLETED', 'CANCELLED') NOT NULL DEFAULT 'ACTIVE',
    start_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    end_time TIMESTAMP NULL,
    CONSTRAINT uq_dining_session_token UNIQUE (session_token),
    CONSTRAINT fk_dining_session_table FOREIGN KEY (table_id)
        REFERENCES restaurant_table (table_id)
        ON DELETE RESTRICT ON UPDATE CASCADE -- Preserve dining session history when tables exist
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- Table 6: orders
-- A single order batch placed by customers at a table during a dining session.
-- ----------------------------------------------------------------------------
CREATE TABLE orders (
    order_id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    session_id INT UNSIGNED NOT NULL,
    status ENUM('PLACED', 'ACCEPTED', 'PREPARING', 'READY', 'SERVED', 'CANCELLED') NOT NULL DEFAULT 'PLACED',
    total_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    special_notes VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_orders_total_amount CHECK (total_amount >= 0.00),
    CONSTRAINT fk_orders_session FOREIGN KEY (session_id)
        REFERENCES dining_session (session_id)
        ON DELETE RESTRICT ON UPDATE CASCADE -- Prevent deleting session while order records exist
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- Table 7: order_item
-- Line items within an order. unit_price is an immutable price snapshot at order time.
-- subtotal is a stored generated column to eliminate computational sync anomalies.
-- ----------------------------------------------------------------------------
CREATE TABLE order_item (
    order_item_id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    order_id INT UNSIGNED NOT NULL,
    item_id INT UNSIGNED NOT NULL,
    quantity INT UNSIGNED NOT NULL DEFAULT 1,
    unit_price DECIMAL(10,2) NOT NULL,
    subtotal DECIMAL(10,2) GENERATED ALWAYS AS (quantity * unit_price) STORED,
    item_notes VARCHAR(255) NULL,
    CONSTRAINT uq_order_item_unique UNIQUE (order_id, item_id),
    CONSTRAINT chk_order_item_quantity CHECK (quantity > 0),
    CONSTRAINT chk_order_item_unit_price CHECK (unit_price > 0.00),
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id)
        REFERENCES orders (order_id)
        ON DELETE CASCADE ON UPDATE CASCADE, -- Deleting an order cascades to its line items
    CONSTRAINT fk_order_item_menu_item FOREIGN KEY (item_id)
        REFERENCES menu_item (item_id)
        ON DELETE RESTRICT ON UPDATE CASCADE -- Menu items with sales history cannot be deleted
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- Table 8: payment
-- Billing and settlements for a dining session / order.
-- ----------------------------------------------------------------------------
CREATE TABLE payment (
    payment_id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    session_id INT UNSIGNED NOT NULL,
    order_id INT UNSIGNED NULL,
    amount DECIMAL(10,2) NOT NULL,
    payment_method ENUM('CASH', 'CARD', 'UPI') NOT NULL,
    payment_status ENUM('PENDING', 'COMPLETED', 'FAILED', 'REFUNDED') NOT NULL DEFAULT 'PENDING',
    transaction_ref VARCHAR(100) NULL,
    paid_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_payment_transaction_ref UNIQUE (transaction_ref),
    CONSTRAINT chk_payment_amount CHECK (amount > 0.00),
    CONSTRAINT fk_payment_session FOREIGN KEY (session_id)
        REFERENCES dining_session (session_id)
        ON DELETE RESTRICT ON UPDATE CASCADE, -- Payment records must never be accidentally dropped
    CONSTRAINT fk_payment_order FOREIGN KEY (order_id)
        REFERENCES orders (order_id)
        ON DELETE RESTRICT ON UPDATE CASCADE -- Prevent dropping order tied to settled payment
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- Table 9: order_status_log
-- Audit log of status transitions (PLACED -> ACCEPTED -> PREPARING -> READY -> SERVED).
-- ----------------------------------------------------------------------------
CREATE TABLE order_status_log (
    log_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    order_id INT UNSIGNED NOT NULL,
    from_status ENUM('PLACED', 'ACCEPTED', 'PREPARING', 'READY', 'SERVED', 'CANCELLED') NULL,
    to_status ENUM('PLACED', 'ACCEPTED', 'PREPARING', 'READY', 'SERVED', 'CANCELLED') NOT NULL,
    changed_by_staff_id INT UNSIGNED NULL,
    notes VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_status_log_order FOREIGN KEY (order_id)
        REFERENCES orders (order_id)
        ON DELETE CASCADE ON UPDATE CASCADE, -- Order removal cleans audit log in tandem
    CONSTRAINT fk_order_status_log_staff FOREIGN KEY (changed_by_staff_id)
        REFERENCES staff (staff_id)
        ON DELETE SET NULL ON UPDATE CASCADE -- Retain audit trail if staff user is removed
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- PERFORMANCE INDEXES
-- ============================================================================

-- 1. Kitchen display polling query optimization:
-- The kitchen screen polls every 3s:
-- SELECT * FROM orders WHERE status IN ('PLACED', 'ACCEPTED', 'PREPARING') ORDER BY created_at ASC;
-- A composite B-tree index on (status, created_at) enables index filtering and sorted retrieval without filesort.
CREATE INDEX idx_orders_kitchen ON orders (status, created_at);

-- 2. Foreign Key Indexes (accelerates JOIN operations and foreign key constraint validation):
CREATE INDEX idx_menu_item_category_id ON menu_item (category_id);
CREATE INDEX idx_dining_session_table_id ON dining_session (table_id);
CREATE INDEX idx_orders_session_id ON orders (session_id);
CREATE INDEX idx_order_item_order_id ON order_item (order_id);
CREATE INDEX idx_order_item_item_id ON order_item (item_id);
CREATE INDEX idx_payment_session_id ON payment (session_id);
CREATE INDEX idx_payment_order_id ON payment (order_id);
CREATE INDEX idx_order_status_log_order_id ON order_status_log (order_id);
CREATE INDEX idx_order_status_log_staff_id ON order_status_log (changed_by_staff_id);
