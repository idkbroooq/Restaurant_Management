-- ============================================================================
-- PATCH: randomize_qr_tokens.sql
-- ============================================================================
-- WHY
--   The seeded qr_token values in db/seed.sql are PREDICTABLE: they follow the
--   pattern  tok_tbl_<table-number>_<descending-hex>  (e.g. tok_tbl_05_e5b4a32109876543).
--   Anyone who can see one code can guess the rest, which lets them open another
--   table's menu and place orders against it.
--
--   Runtime tokens created through the admin console are already random
--   (TableService.generateToken() = "tok_tbl_" + UUID with dashes removed), so this
--   script only needs to replace the values that were seeded.
--
-- WHAT THIS DOES
--   Rewrites qr_token for every table using MySQL's RANDOM_BYTES(), which draws from
--   the platform CSPRNG. The result is "tok_tbl_" + 32 lowercase hex characters
--   (36 hex/URL-safe characters after the prefix, i.e. well over the 22 minimum).
--
-- HOW TO RUN (review first, then run once, as the schema owner)
--   mysql -u root -p < db/patches/randomize_qr_tokens.sql
--
-- AFTER RUNNING
--   Every existing printed QR code becomes invalid. Reprint the codes from the
--   admin console (Admin -> Tables -> QR) and replace the table tents.
-- ============================================================================

USE restaurant_db;

-- 1. Preview what the current tokens look like (optional).
SELECT table_id, table_number, qr_token AS old_token
FROM restaurant_table
ORDER BY table_id;

-- 2. Replace every token with a fresh, cryptographically random value.
UPDATE restaurant_table
SET qr_token = CONCAT('tok_tbl_', LOWER(HEX(RANDOM_BYTES(16))));

-- 3. Confirm the new tokens and that they are all unique.
SELECT table_id, table_number, qr_token AS new_token, CHAR_LENGTH(qr_token) AS token_length
FROM restaurant_table
ORDER BY table_id;

SELECT COUNT(*) AS tables,
       COUNT(DISTINCT qr_token) AS distinct_tokens
FROM restaurant_table;
