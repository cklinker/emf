-- Create PostgreSQL sequences for AUTO_NUMBER fields in seeded collections.
-- The Threadline Clothing seed (V50) inserted 10 products (TL-001000..001009)
-- and 6 orders (ORD-100001..100006) directly via SQL, bypassing the
-- auto-number service, so the sequences were never created. New records
-- created via the API will use these sequences going forward.
-- ensureSequenceExists() in AutoNumberService handles collections created
-- after this migration, so no further migrations are needed for new fields.

CREATE SEQUENCE IF NOT EXISTS seq_products_sku
    START WITH 1010
    INCREMENT BY 1
    MINVALUE 1000;

CREATE SEQUENCE IF NOT EXISTS seq_orders_order_number
    START WITH 100007
    INCREMENT BY 1
    MINVALUE 100001;
