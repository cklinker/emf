-- ============================================================================
-- V50: Seed E-Commerce Clothing Store (Threadline Clothing Co.)
--
-- Creates a complete e-commerce demo dataset:
--   - 1 tenant, 1 profile, 1 user
--   - 10 global picklists with ~60 values
--   - 9 collections with ~100 fields
--   - 9 page layouts with sections and field placements
--   - ~18 list views
--   - 10 validation rules, 5 record types
--   - 9 physical tables with ~60 sample records
-- ============================================================================

-- ============================================================================
-- SECTION 1: TENANT, PROFILE, PLATFORM USER
-- ============================================================================

INSERT INTO tenant (id, slug, name, edition, status, settings, limits, created_at, updated_at)
VALUES (
    'ec000000-0000-0000-0000-000000000001',
    'threadline-clothing',
    'Threadline Clothing Co.',
    'PROFESSIONAL',
    'ACTIVE',
    '{"industry": "retail", "storefront": "ecommerce"}',
    '{}',
    NOW(), NOW()
) ON CONFLICT (slug) DO NOTHING;

INSERT INTO profile (id, tenant_id, name, description, is_system, created_at, updated_at)
VALUES (
    'ec000000-0000-0000-0000-000000000010',
    'ec000000-0000-0000-0000-000000000001',
    'System Administrator',
    'Full access profile for Threadline Clothing',
    true,
    NOW(), NOW()
) ON CONFLICT (tenant_id, name) DO NOTHING;

INSERT INTO platform_user (id, tenant_id, email, username, first_name, last_name, status,
                           locale, timezone, profile_id, login_count, mfa_enabled, settings,
                           created_at, updated_at)
VALUES (
    'ec000000-0000-0000-0000-000000000020',
    'ec000000-0000-0000-0000-000000000001',
    'admin@threadline.com',
    'admin',
    'System',
    'Admin',
    'ACTIVE',
    'en_US',
    'America/New_York',
    'ec000000-0000-0000-0000-000000000010',
    0,
    false,
    '{}',
    NOW(), NOW()
) ON CONFLICT (tenant_id, email) DO NOTHING;

-- ============================================================================
-- SECTION 2: GLOBAL PICKLISTS
-- ============================================================================

-- 1. Order Status
INSERT INTO global_picklist (id, tenant_id, name, description, sorted, restricted, created_at, updated_at)
VALUES ('ec000200-0000-0000-0000-000000000001', 'ec000000-0000-0000-0000-000000000001',
        'Order Status', 'Status values for orders', false, true, NOW(), NOW())
ON CONFLICT (tenant_id, name) DO NOTHING;

-- 2. Payment Status
INSERT INTO global_picklist (id, tenant_id, name, description, sorted, restricted, created_at, updated_at)
VALUES ('ec000200-0000-0000-0000-000000000002', 'ec000000-0000-0000-0000-000000000001',
        'Payment Status', 'Status values for payments', false, true, NOW(), NOW())
ON CONFLICT (tenant_id, name) DO NOTHING;

-- 3. Payment Method
INSERT INTO global_picklist (id, tenant_id, name, description, sorted, restricted, created_at, updated_at)
VALUES ('ec000200-0000-0000-0000-000000000003', 'ec000000-0000-0000-0000-000000000001',
        'Payment Method', 'Accepted payment methods', false, true, NOW(), NOW())
ON CONFLICT (tenant_id, name) DO NOTHING;

-- 4. Product Status
INSERT INTO global_picklist (id, tenant_id, name, description, sorted, restricted, created_at, updated_at)
VALUES ('ec000200-0000-0000-0000-000000000004', 'ec000000-0000-0000-0000-000000000001',
        'Product Status', 'Status values for products', false, true, NOW(), NOW())
ON CONFLICT (tenant_id, name) DO NOTHING;

-- 5. Size
INSERT INTO global_picklist (id, tenant_id, name, description, sorted, restricted, created_at, updated_at)
VALUES ('ec000200-0000-0000-0000-000000000005', 'ec000000-0000-0000-0000-000000000001',
        'Size', 'Clothing size options', true, true, NOW(), NOW())
ON CONFLICT (tenant_id, name) DO NOTHING;

-- 6. Color
INSERT INTO global_picklist (id, tenant_id, name, description, sorted, restricted, created_at, updated_at)
VALUES ('ec000200-0000-0000-0000-000000000006', 'ec000000-0000-0000-0000-000000000001',
        'Color', 'Clothing color options', true, true, NOW(), NOW())
ON CONFLICT (tenant_id, name) DO NOTHING;

-- 7. Category Type
INSERT INTO global_picklist (id, tenant_id, name, description, sorted, restricted, created_at, updated_at)
VALUES ('ec000200-0000-0000-0000-000000000007', 'ec000000-0000-0000-0000-000000000001',
        'Category Type', 'Product category types', false, true, NOW(), NOW())
ON CONFLICT (tenant_id, name) DO NOTHING;

-- 8. Discount Type
INSERT INTO global_picklist (id, tenant_id, name, description, sorted, restricted, created_at, updated_at)
VALUES ('ec000200-0000-0000-0000-000000000008', 'ec000000-0000-0000-0000-000000000001',
        'Discount Type', 'Types of discounts', false, true, NOW(), NOW())
ON CONFLICT (tenant_id, name) DO NOTHING;

-- 9. Shipping Method
INSERT INTO global_picklist (id, tenant_id, name, description, sorted, restricted, created_at, updated_at)
VALUES ('ec000200-0000-0000-0000-000000000009', 'ec000000-0000-0000-0000-000000000001',
        'Shipping Method', 'Shipping options', false, true, NOW(), NOW())
ON CONFLICT (tenant_id, name) DO NOTHING;

-- 10. Gender
INSERT INTO global_picklist (id, tenant_id, name, description, sorted, restricted, created_at, updated_at)
VALUES ('ec000200-0000-0000-0000-000000000010', 'ec000000-0000-0000-0000-000000000001',
        'Gender', 'Target gender for products', false, true, NOW(), NOW())
ON CONFLICT (tenant_id, name) DO NOTHING;

-- ============================================================================
-- SECTION 2b: PICKLIST VALUES
-- ============================================================================

-- Order Status values
INSERT INTO picklist_value (id, picklist_source_type, picklist_source_id, value, label, is_default, is_active, sort_order, color, created_at, updated_at) VALUES
('ec000400-0001-0000-0000-000000000001', 'GLOBAL', 'ec000200-0000-0000-0000-000000000001', 'pending', 'Pending', true, true, 0, '#FFA500', NOW(), NOW()),
('ec000400-0001-0000-0000-000000000002', 'GLOBAL', 'ec000200-0000-0000-0000-000000000001', 'confirmed', 'Confirmed', false, true, 1, '#2196F3', NOW(), NOW()),
('ec000400-0001-0000-0000-000000000003', 'GLOBAL', 'ec000200-0000-0000-0000-000000000001', 'processing', 'Processing', false, true, 2, '#9C27B0', NOW(), NOW()),
('ec000400-0001-0000-0000-000000000004', 'GLOBAL', 'ec000200-0000-0000-0000-000000000001', 'shipped', 'Shipped', false, true, 3, '#00BCD4', NOW(), NOW()),
('ec000400-0001-0000-0000-000000000005', 'GLOBAL', 'ec000200-0000-0000-0000-000000000001', 'delivered', 'Delivered', false, true, 4, '#4CAF50', NOW(), NOW()),
('ec000400-0001-0000-0000-000000000006', 'GLOBAL', 'ec000200-0000-0000-0000-000000000001', 'cancelled', 'Cancelled', false, true, 5, '#F44336', NOW(), NOW()),
('ec000400-0001-0000-0000-000000000007', 'GLOBAL', 'ec000200-0000-0000-0000-000000000001', 'returned', 'Returned', false, true, 6, '#795548', NOW(), NOW())
ON CONFLICT (picklist_source_type, picklist_source_id, value) DO NOTHING;

-- Payment Status values
INSERT INTO picklist_value (id, picklist_source_type, picklist_source_id, value, label, is_default, is_active, sort_order, color, created_at, updated_at) VALUES
('ec000400-0002-0000-0000-000000000001', 'GLOBAL', 'ec000200-0000-0000-0000-000000000002', 'pending', 'Pending', true, true, 0, '#FFA500', NOW(), NOW()),
('ec000400-0002-0000-0000-000000000002', 'GLOBAL', 'ec000200-0000-0000-0000-000000000002', 'authorized', 'Authorized', false, true, 1, '#2196F3', NOW(), NOW()),
('ec000400-0002-0000-0000-000000000003', 'GLOBAL', 'ec000200-0000-0000-0000-000000000002', 'captured', 'Captured', false, true, 2, '#4CAF50', NOW(), NOW()),
('ec000400-0002-0000-0000-000000000004', 'GLOBAL', 'ec000200-0000-0000-0000-000000000002', 'refunded', 'Refunded', false, true, 3, '#FF9800', NOW(), NOW()),
('ec000400-0002-0000-0000-000000000005', 'GLOBAL', 'ec000200-0000-0000-0000-000000000002', 'failed', 'Failed', false, true, 4, '#F44336', NOW(), NOW())
ON CONFLICT (picklist_source_type, picklist_source_id, value) DO NOTHING;

-- Payment Method values
INSERT INTO picklist_value (id, picklist_source_type, picklist_source_id, value, label, is_default, is_active, sort_order, color, created_at, updated_at) VALUES
('ec000400-0003-0000-0000-000000000001', 'GLOBAL', 'ec000200-0000-0000-0000-000000000003', 'credit_card', 'Credit Card', true, true, 0, '#1976D2', NOW(), NOW()),
('ec000400-0003-0000-0000-000000000002', 'GLOBAL', 'ec000200-0000-0000-0000-000000000003', 'debit_card', 'Debit Card', false, true, 1, '#388E3C', NOW(), NOW()),
('ec000400-0003-0000-0000-000000000003', 'GLOBAL', 'ec000200-0000-0000-0000-000000000003', 'paypal', 'PayPal', false, true, 2, '#003087', NOW(), NOW()),
('ec000400-0003-0000-0000-000000000004', 'GLOBAL', 'ec000200-0000-0000-0000-000000000003', 'apple_pay', 'Apple Pay', false, true, 3, '#000000', NOW(), NOW()),
('ec000400-0003-0000-0000-000000000005', 'GLOBAL', 'ec000200-0000-0000-0000-000000000003', 'bank_transfer', 'Bank Transfer', false, true, 4, '#607D8B', NOW(), NOW())
ON CONFLICT (picklist_source_type, picklist_source_id, value) DO NOTHING;

-- Product Status values
INSERT INTO picklist_value (id, picklist_source_type, picklist_source_id, value, label, is_default, is_active, sort_order, color, created_at, updated_at) VALUES
('ec000400-0004-0000-0000-000000000001', 'GLOBAL', 'ec000200-0000-0000-0000-000000000004', 'draft', 'Draft', true, true, 0, '#9E9E9E', NOW(), NOW()),
('ec000400-0004-0000-0000-000000000002', 'GLOBAL', 'ec000200-0000-0000-0000-000000000004', 'active', 'Active', false, true, 1, '#4CAF50', NOW(), NOW()),
('ec000400-0004-0000-0000-000000000003', 'GLOBAL', 'ec000200-0000-0000-0000-000000000004', 'discontinued', 'Discontinued', false, true, 2, '#F44336', NOW(), NOW()),
('ec000400-0004-0000-0000-000000000004', 'GLOBAL', 'ec000200-0000-0000-0000-000000000004', 'out_of_season', 'Out of Season', false, true, 3, '#FF9800', NOW(), NOW())
ON CONFLICT (picklist_source_type, picklist_source_id, value) DO NOTHING;

-- Size values
INSERT INTO picklist_value (id, picklist_source_type, picklist_source_id, value, label, is_default, is_active, sort_order, color, created_at, updated_at) VALUES
('ec000400-0005-0000-0000-000000000001', 'GLOBAL', 'ec000200-0000-0000-0000-000000000005', 'xs', 'XS', false, true, 0, NULL, NOW(), NOW()),
('ec000400-0005-0000-0000-000000000002', 'GLOBAL', 'ec000200-0000-0000-0000-000000000005', 's', 'S', false, true, 1, NULL, NOW(), NOW()),
('ec000400-0005-0000-0000-000000000003', 'GLOBAL', 'ec000200-0000-0000-0000-000000000005', 'm', 'M', true, true, 2, NULL, NOW(), NOW()),
('ec000400-0005-0000-0000-000000000004', 'GLOBAL', 'ec000200-0000-0000-0000-000000000005', 'l', 'L', false, true, 3, NULL, NOW(), NOW()),
('ec000400-0005-0000-0000-000000000005', 'GLOBAL', 'ec000200-0000-0000-0000-000000000005', 'xl', 'XL', false, true, 4, NULL, NOW(), NOW()),
('ec000400-0005-0000-0000-000000000006', 'GLOBAL', 'ec000200-0000-0000-0000-000000000005', 'xxl', 'XXL', false, true, 5, NULL, NOW(), NOW()),
('ec000400-0005-0000-0000-000000000007', 'GLOBAL', 'ec000200-0000-0000-0000-000000000005', 'xxxl', 'XXXL', false, true, 6, NULL, NOW(), NOW()),
('ec000400-0005-0000-0000-000000000008', 'GLOBAL', 'ec000200-0000-0000-0000-000000000005', 'one_size', 'One Size', false, true, 7, NULL, NOW(), NOW())
ON CONFLICT (picklist_source_type, picklist_source_id, value) DO NOTHING;

-- Color values
INSERT INTO picklist_value (id, picklist_source_type, picklist_source_id, value, label, is_default, is_active, sort_order, color, created_at, updated_at) VALUES
('ec000400-0006-0000-0000-000000000001', 'GLOBAL', 'ec000200-0000-0000-0000-000000000006', 'black', 'Black', true, true, 0, '#000000', NOW(), NOW()),
('ec000400-0006-0000-0000-000000000002', 'GLOBAL', 'ec000200-0000-0000-0000-000000000006', 'white', 'White', false, true, 1, '#FFFFFF', NOW(), NOW()),
('ec000400-0006-0000-0000-000000000003', 'GLOBAL', 'ec000200-0000-0000-0000-000000000006', 'navy', 'Navy', false, true, 2, '#001F3F', NOW(), NOW()),
('ec000400-0006-0000-0000-000000000004', 'GLOBAL', 'ec000200-0000-0000-0000-000000000006', 'red', 'Red', false, true, 3, '#FF0000', NOW(), NOW()),
('ec000400-0006-0000-0000-000000000005', 'GLOBAL', 'ec000200-0000-0000-0000-000000000006', 'blue', 'Blue', false, true, 4, '#2196F3', NOW(), NOW()),
('ec000400-0006-0000-0000-000000000006', 'GLOBAL', 'ec000200-0000-0000-0000-000000000006', 'green', 'Green', false, true, 5, '#4CAF50', NOW(), NOW()),
('ec000400-0006-0000-0000-000000000007', 'GLOBAL', 'ec000200-0000-0000-0000-000000000006', 'grey', 'Grey', false, true, 6, '#9E9E9E', NOW(), NOW()),
('ec000400-0006-0000-0000-000000000008', 'GLOBAL', 'ec000200-0000-0000-0000-000000000006', 'beige', 'Beige', false, true, 7, '#F5F5DC', NOW(), NOW()),
('ec000400-0006-0000-0000-000000000009', 'GLOBAL', 'ec000200-0000-0000-0000-000000000006', 'pink', 'Pink', false, true, 8, '#E91E63', NOW(), NOW()),
('ec000400-0006-0000-0000-000000000010', 'GLOBAL', 'ec000200-0000-0000-0000-000000000006', 'brown', 'Brown', false, true, 9, '#795548', NOW(), NOW()),
('ec000400-0006-0000-0000-000000000011', 'GLOBAL', 'ec000200-0000-0000-0000-000000000006', 'purple', 'Purple', false, true, 10, '#9C27B0', NOW(), NOW()),
('ec000400-0006-0000-0000-000000000012', 'GLOBAL', 'ec000200-0000-0000-0000-000000000006', 'yellow', 'Yellow', false, true, 11, '#FFEB3B', NOW(), NOW())
ON CONFLICT (picklist_source_type, picklist_source_id, value) DO NOTHING;

-- Category Type values
INSERT INTO picklist_value (id, picklist_source_type, picklist_source_id, value, label, is_default, is_active, sort_order, color, created_at, updated_at) VALUES
('ec000400-0007-0000-0000-000000000001', 'GLOBAL', 'ec000200-0000-0000-0000-000000000007', 'apparel', 'Apparel', true, true, 0, '#3F51B5', NOW(), NOW()),
('ec000400-0007-0000-0000-000000000002', 'GLOBAL', 'ec000200-0000-0000-0000-000000000007', 'footwear', 'Footwear', false, true, 1, '#795548', NOW(), NOW()),
('ec000400-0007-0000-0000-000000000003', 'GLOBAL', 'ec000200-0000-0000-0000-000000000007', 'accessories', 'Accessories', false, true, 2, '#FF9800', NOW(), NOW()),
('ec000400-0007-0000-0000-000000000004', 'GLOBAL', 'ec000200-0000-0000-0000-000000000007', 'activewear', 'Activewear', false, true, 3, '#4CAF50', NOW(), NOW()),
('ec000400-0007-0000-0000-000000000005', 'GLOBAL', 'ec000200-0000-0000-0000-000000000007', 'outerwear', 'Outerwear', false, true, 4, '#607D8B', NOW(), NOW())
ON CONFLICT (picklist_source_type, picklist_source_id, value) DO NOTHING;

-- Discount Type values
INSERT INTO picklist_value (id, picklist_source_type, picklist_source_id, value, label, is_default, is_active, sort_order, color, created_at, updated_at) VALUES
('ec000400-0008-0000-0000-000000000001', 'GLOBAL', 'ec000200-0000-0000-0000-000000000008', 'percentage', 'Percentage', true, true, 0, '#E91E63', NOW(), NOW()),
('ec000400-0008-0000-0000-000000000002', 'GLOBAL', 'ec000200-0000-0000-0000-000000000008', 'fixed_amount', 'Fixed Amount', false, true, 1, '#9C27B0', NOW(), NOW()),
('ec000400-0008-0000-0000-000000000003', 'GLOBAL', 'ec000200-0000-0000-0000-000000000008', 'free_shipping', 'Free Shipping', false, true, 2, '#00BCD4', NOW(), NOW())
ON CONFLICT (picklist_source_type, picklist_source_id, value) DO NOTHING;

-- Shipping Method values
INSERT INTO picklist_value (id, picklist_source_type, picklist_source_id, value, label, is_default, is_active, sort_order, color, created_at, updated_at) VALUES
('ec000400-0009-0000-0000-000000000001', 'GLOBAL', 'ec000200-0000-0000-0000-000000000009', 'standard', 'Standard', true, true, 0, '#607D8B', NOW(), NOW()),
('ec000400-0009-0000-0000-000000000002', 'GLOBAL', 'ec000200-0000-0000-0000-000000000009', 'express', 'Express', false, true, 1, '#FF9800', NOW(), NOW()),
('ec000400-0009-0000-0000-000000000003', 'GLOBAL', 'ec000200-0000-0000-0000-000000000009', 'next_day', 'Next Day', false, true, 2, '#F44336', NOW(), NOW()),
('ec000400-0009-0000-0000-000000000004', 'GLOBAL', 'ec000200-0000-0000-0000-000000000009', 'store_pickup', 'Store Pickup', false, true, 3, '#4CAF50', NOW(), NOW())
ON CONFLICT (picklist_source_type, picklist_source_id, value) DO NOTHING;

-- Gender values
INSERT INTO picklist_value (id, picklist_source_type, picklist_source_id, value, label, is_default, is_active, sort_order, color, created_at, updated_at) VALUES
('ec000400-0010-0000-0000-000000000001', 'GLOBAL', 'ec000200-0000-0000-0000-000000000010', 'men', 'Men', false, true, 0, '#1976D2', NOW(), NOW()),
('ec000400-0010-0000-0000-000000000002', 'GLOBAL', 'ec000200-0000-0000-0000-000000000010', 'women', 'Women', false, true, 1, '#E91E63', NOW(), NOW()),
('ec000400-0010-0000-0000-000000000003', 'GLOBAL', 'ec000200-0000-0000-0000-000000000010', 'unisex', 'Unisex', true, true, 2, '#9C27B0', NOW(), NOW()),
('ec000400-0010-0000-0000-000000000004', 'GLOBAL', 'ec000200-0000-0000-0000-000000000010', 'kids', 'Kids', false, true, 3, '#FF9800', NOW(), NOW())
ON CONFLICT (picklist_source_type, picklist_source_id, value) DO NOTHING;

-- ============================================================================
-- SECTION 3: COLLECTIONS
-- ============================================================================

INSERT INTO collection (id, tenant_id, name, display_name, description, path, storage_mode, active, current_version, system_collection, created_at, updated_at) VALUES
('ec000100-0000-0000-0000-000000000001', 'ec000000-0000-0000-0000-000000000001', 'categories', 'Categories', 'Product categories and classifications', '/api/categories', 'PHYSICAL_TABLES', true, 1, false, NOW(), NOW()),
('ec000100-0000-0000-0000-000000000002', 'ec000000-0000-0000-0000-000000000001', 'products', 'Products', 'Clothing product catalog', '/api/products', 'PHYSICAL_TABLES', true, 1, false, NOW(), NOW()),
('ec000100-0000-0000-0000-000000000003', 'ec000000-0000-0000-0000-000000000001', 'customers', 'Customers', 'Customer records and contact information', '/api/customers', 'PHYSICAL_TABLES', true, 1, false, NOW(), NOW()),
('ec000100-0000-0000-0000-000000000004', 'ec000000-0000-0000-0000-000000000001', 'orders', 'Orders', 'Sales orders', '/api/orders', 'PHYSICAL_TABLES', true, 1, false, NOW(), NOW()),
('ec000100-0000-0000-0000-000000000005', 'ec000000-0000-0000-0000-000000000001', 'order_items', 'Order Items', 'Individual line items within orders', '/api/order_items', 'PHYSICAL_TABLES', true, 1, false, NOW(), NOW()),
('ec000100-0000-0000-0000-000000000006', 'ec000000-0000-0000-0000-000000000001', 'payments', 'Payments', 'Payment transactions for orders', '/api/payments', 'PHYSICAL_TABLES', true, 1, false, NOW(), NOW()),
('ec000100-0000-0000-0000-000000000007', 'ec000000-0000-0000-0000-000000000001', 'inventory', 'Inventory', 'Stock levels per product variant', '/api/inventory', 'PHYSICAL_TABLES', true, 1, false, NOW(), NOW()),
('ec000100-0000-0000-0000-000000000008', 'ec000000-0000-0000-0000-000000000001', 'promotions', 'Promotions', 'Sales and promotional campaigns', '/api/promotions', 'PHYSICAL_TABLES', true, 1, false, NOW(), NOW()),
('ec000100-0000-0000-0000-000000000009', 'ec000000-0000-0000-0000-000000000001', 'discount_codes', 'Discount Codes', 'Coupon and discount codes', '/api/discount_codes', 'PHYSICAL_TABLES', true, 1, false, NOW(), NOW())
ON CONFLICT (tenant_id, name) DO NOTHING;

-- ============================================================================
-- SECTION 4: FIELDS
-- ============================================================================

-- ---- Categories fields (collection 0001) ----
INSERT INTO field (id, collection_id, name, display_name, type, required, active, unique_constraint, indexed, field_order, description, created_at, updated_at) VALUES
('ec000300-0001-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000001', 'name', 'Name', 'STRING', true, true, true, true, 1, 'Category name', NOW(), NOW()),
('ec000300-0001-0000-0000-000000000002', 'ec000100-0000-0000-0000-000000000001', 'display_name', 'Display Name', 'STRING', true, true, false, false, 2, 'Display name shown to customers', NOW(), NOW()),
('ec000300-0001-0000-0000-000000000003', 'ec000100-0000-0000-0000-000000000001', 'description', 'Description', 'RICH_TEXT', false, true, false, false, 3, 'Category description', NOW(), NOW()),
('ec000300-0001-0000-0000-000000000010', 'ec000100-0000-0000-0000-000000000001', 'slug', 'URL Slug', 'STRING', true, true, true, true, 10, 'URL-friendly identifier', NOW(), NOW())
ON CONFLICT (collection_id, name) DO NOTHING;

INSERT INTO field (id, collection_id, name, display_name, type, required, active, field_order, description, field_type_config, created_at, updated_at) VALUES
('ec000300-0001-0000-0000-000000000004', 'ec000100-0000-0000-0000-000000000001', 'category_type', 'Category Type', 'PICKLIST', true, true, 4, 'Type of category', '{"globalPicklistId": "ec000200-0000-0000-0000-000000000007", "restricted": true}', NOW(), NOW()),
('ec000300-0001-0000-0000-000000000005', 'ec000100-0000-0000-0000-000000000001', 'gender', 'Gender', 'PICKLIST', false, true, 5, 'Target gender', '{"globalPicklistId": "ec000200-0000-0000-0000-000000000010", "restricted": true}', NOW(), NOW())
ON CONFLICT (collection_id, name) DO NOTHING;

INSERT INTO field (id, collection_id, name, display_name, type, required, active, field_order, description, relationship_type, reference_collection_id, cascade_delete, created_at, updated_at) VALUES
('ec000300-0001-0000-0000-000000000006', 'ec000100-0000-0000-0000-000000000001', 'parent_category', 'Parent Category', 'LOOKUP', false, true, 6, 'Parent category for hierarchy', 'LOOKUP', 'ec000100-0000-0000-0000-000000000001', false, NOW(), NOW())
ON CONFLICT (collection_id, name) DO NOTHING;

INSERT INTO field (id, collection_id, name, display_name, type, required, active, field_order, description, created_at, updated_at) VALUES
('ec000300-0001-0000-0000-000000000007', 'ec000100-0000-0000-0000-000000000001', 'image_url', 'Image URL', 'URL', false, true, 7, 'Category image URL', NOW(), NOW()),
('ec000300-0001-0000-0000-000000000008', 'ec000100-0000-0000-0000-000000000001', 'sort_order', 'Sort Order', 'INTEGER', false, true, 8, 'Display order', NOW(), NOW()),
('ec000300-0001-0000-0000-000000000009', 'ec000100-0000-0000-0000-000000000001', 'is_active', 'Active', 'BOOLEAN', true, true, 9, 'Whether category is active', NOW(), NOW())
ON CONFLICT (collection_id, name) DO NOTHING;

-- ---- Products fields (collection 0002) ----
INSERT INTO field (id, collection_id, name, display_name, type, required, active, field_order, description, field_type_config, created_at, updated_at) VALUES
('ec000300-0002-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000002', 'sku', 'SKU', 'AUTO_NUMBER', true, true, 1, 'Stock keeping unit', '{"prefix": "TL-", "padding": 6, "startValue": 1000}', NOW(), NOW())
ON CONFLICT (collection_id, name) DO NOTHING;

INSERT INTO field (id, collection_id, name, display_name, type, required, active, field_order, description, created_at, updated_at) VALUES
('ec000300-0002-0000-0000-000000000002', 'ec000100-0000-0000-0000-000000000002', 'name', 'Name', 'STRING', true, true, 2, 'Product name', NOW(), NOW()),
('ec000300-0002-0000-0000-000000000003', 'ec000100-0000-0000-0000-000000000002', 'description', 'Description', 'RICH_TEXT', false, true, 3, 'Product description', NOW(), NOW())
ON CONFLICT (collection_id, name) DO NOTHING;

INSERT INTO field (id, collection_id, name, display_name, type, required, active, field_order, description, relationship_type, reference_collection_id, cascade_delete, created_at, updated_at) VALUES
('ec000300-0002-0000-0000-000000000004', 'ec000100-0000-0000-0000-000000000002', 'category', 'Category', 'LOOKUP', true, true, 4, 'Product category', 'LOOKUP', 'ec000100-0000-0000-0000-000000000001', false, NOW(), NOW())
ON CONFLICT (collection_id, name) DO NOTHING;

INSERT INTO field (id, collection_id, name, display_name, type, required, active, field_order, description, field_type_config, created_at, updated_at) VALUES
('ec000300-0002-0000-0000-000000000005', 'ec000100-0000-0000-0000-000000000002', 'status', 'Status', 'PICKLIST', true, true, 5, 'Product status', '{"globalPicklistId": "ec000200-0000-0000-0000-000000000004", "restricted": true}', NOW(), NOW()),
('ec000300-0002-0000-0000-000000000006', 'ec000100-0000-0000-0000-000000000002', 'base_price', 'Base Price', 'CURRENCY', true, true, 6, 'Retail price', '{"precision": 2, "defaultCurrencyCode": "USD"}', NOW(), NOW()),
('ec000300-0002-0000-0000-000000000007', 'ec000100-0000-0000-0000-000000000002', 'cost', 'Cost', 'CURRENCY', false, true, 7, 'Cost of goods', '{"precision": 2, "defaultCurrencyCode": "USD"}', NOW(), NOW()),
('ec000300-0002-0000-0000-000000000008', 'ec000100-0000-0000-0000-000000000002', 'compare_at_price', 'Compare At Price', 'CURRENCY', false, true, 8, 'Original price for sale display', '{"precision": 2, "defaultCurrencyCode": "USD"}', NOW(), NOW()),
('ec000300-0002-0000-0000-000000000009', 'ec000100-0000-0000-0000-000000000002', 'gender', 'Gender', 'PICKLIST', false, true, 9, 'Target gender', '{"globalPicklistId": "ec000200-0000-0000-0000-000000000010", "restricted": true}', NOW(), NOW()),
('ec000300-0002-0000-0000-000000000010', 'ec000100-0000-0000-0000-000000000002', 'available_sizes', 'Available Sizes', 'MULTI_PICKLIST', false, true, 10, 'Sizes available', '{"globalPicklistId": "ec000200-0000-0000-0000-000000000005", "restricted": true}', NOW(), NOW()),
('ec000300-0002-0000-0000-000000000011', 'ec000100-0000-0000-0000-000000000002', 'available_colors', 'Available Colors', 'MULTI_PICKLIST', false, true, 11, 'Colors available', '{"globalPicklistId": "ec000200-0000-0000-0000-000000000006", "restricted": true}', NOW(), NOW())
ON CONFLICT (collection_id, name) DO NOTHING;

INSERT INTO field (id, collection_id, name, display_name, type, required, active, field_order, description, created_at, updated_at) VALUES
('ec000300-0002-0000-0000-000000000012', 'ec000100-0000-0000-0000-000000000002', 'weight_grams', 'Weight (g)', 'INTEGER', false, true, 12, 'Product weight in grams', NOW(), NOW()),
('ec000300-0002-0000-0000-000000000013', 'ec000100-0000-0000-0000-000000000002', 'material', 'Material', 'STRING', false, true, 13, 'Primary material', NOW(), NOW()),
('ec000300-0002-0000-0000-000000000014', 'ec000100-0000-0000-0000-000000000002', 'care_instructions', 'Care Instructions', 'STRING', false, true, 14, 'Washing and care', NOW(), NOW()),
('ec000300-0002-0000-0000-000000000015', 'ec000100-0000-0000-0000-000000000002', 'primary_image_url', 'Primary Image', 'URL', false, true, 15, 'Main product image', NOW(), NOW()),
('ec000300-0002-0000-0000-000000000016', 'ec000100-0000-0000-0000-000000000002', 'is_featured', 'Featured', 'BOOLEAN', false, true, 16, 'Featured product flag', NOW(), NOW()),
('ec000300-0002-0000-0000-000000000017', 'ec000100-0000-0000-0000-000000000002', 'tags', 'Tags', 'JSON', false, true, 17, 'Product tags', NOW(), NOW())
ON CONFLICT (collection_id, name) DO NOTHING;

-- ---- Customers fields (collection 0003) ----
INSERT INTO field (id, collection_id, name, display_name, type, required, active, unique_constraint, indexed, field_order, description, created_at, updated_at) VALUES
('ec000300-0003-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000003', 'email', 'Email', 'EMAIL', true, true, true, true, 1, 'Customer email address', NOW(), NOW())
ON CONFLICT (collection_id, name) DO NOTHING;

INSERT INTO field (id, collection_id, name, display_name, type, required, active, field_order, description, created_at, updated_at) VALUES
('ec000300-0003-0000-0000-000000000002', 'ec000100-0000-0000-0000-000000000003', 'first_name', 'First Name', 'STRING', true, true, 2, 'First name', NOW(), NOW()),
('ec000300-0003-0000-0000-000000000003', 'ec000100-0000-0000-0000-000000000003', 'last_name', 'Last Name', 'STRING', true, true, 3, 'Last name', NOW(), NOW()),
('ec000300-0003-0000-0000-000000000004', 'ec000100-0000-0000-0000-000000000003', 'phone', 'Phone', 'PHONE', false, true, 4, 'Phone number', NOW(), NOW()),
('ec000300-0003-0000-0000-000000000005', 'ec000100-0000-0000-0000-000000000003', 'shipping_street', 'Shipping Street', 'STRING', false, true, 5, 'Shipping street', NOW(), NOW()),
('ec000300-0003-0000-0000-000000000006', 'ec000100-0000-0000-0000-000000000003', 'shipping_city', 'Shipping City', 'STRING', false, true, 6, 'Shipping city', NOW(), NOW()),
('ec000300-0003-0000-0000-000000000007', 'ec000100-0000-0000-0000-000000000003', 'shipping_state', 'Shipping State', 'STRING', false, true, 7, 'Shipping state', NOW(), NOW()),
('ec000300-0003-0000-0000-000000000008', 'ec000100-0000-0000-0000-000000000003', 'shipping_zip', 'Shipping ZIP', 'STRING', false, true, 8, 'Shipping zip', NOW(), NOW()),
('ec000300-0003-0000-0000-000000000009', 'ec000100-0000-0000-0000-000000000003', 'shipping_country', 'Shipping Country', 'STRING', false, true, 9, 'Shipping country', NOW(), NOW()),
('ec000300-0003-0000-0000-000000000010', 'ec000100-0000-0000-0000-000000000003', 'billing_street', 'Billing Street', 'STRING', false, true, 10, 'Billing street', NOW(), NOW()),
('ec000300-0003-0000-0000-000000000011', 'ec000100-0000-0000-0000-000000000003', 'billing_city', 'Billing City', 'STRING', false, true, 11, 'Billing city', NOW(), NOW()),
('ec000300-0003-0000-0000-000000000012', 'ec000100-0000-0000-0000-000000000003', 'billing_state', 'Billing State', 'STRING', false, true, 12, 'Billing state', NOW(), NOW()),
('ec000300-0003-0000-0000-000000000013', 'ec000100-0000-0000-0000-000000000003', 'billing_zip', 'Billing ZIP', 'STRING', false, true, 13, 'Billing zip', NOW(), NOW()),
('ec000300-0003-0000-0000-000000000014', 'ec000100-0000-0000-0000-000000000003', 'billing_country', 'Billing Country', 'STRING', false, true, 14, 'Billing country', NOW(), NOW()),
('ec000300-0003-0000-0000-000000000015', 'ec000100-0000-0000-0000-000000000003', 'total_orders', 'Total Orders', 'INTEGER', false, true, 15, 'Lifetime order count', NOW(), NOW()),
('ec000300-0003-0000-0000-000000000017', 'ec000100-0000-0000-0000-000000000003', 'notes', 'Notes', 'RICH_TEXT', false, true, 17, 'Internal notes', NOW(), NOW()),
('ec000300-0003-0000-0000-000000000018', 'ec000100-0000-0000-0000-000000000003', 'accepts_marketing', 'Accepts Marketing', 'BOOLEAN', false, true, 18, 'Opted in to marketing', NOW(), NOW())
ON CONFLICT (collection_id, name) DO NOTHING;

INSERT INTO field (id, collection_id, name, display_name, type, required, active, field_order, description, field_type_config, created_at, updated_at) VALUES
('ec000300-0003-0000-0000-000000000016', 'ec000100-0000-0000-0000-000000000003', 'total_spent', 'Lifetime Value', 'CURRENCY', false, true, 16, 'Total amount spent', '{"precision": 2, "defaultCurrencyCode": "USD"}', NOW(), NOW())
ON CONFLICT (collection_id, name) DO NOTHING;

-- ---- Orders fields (collection 0004) ----
INSERT INTO field (id, collection_id, name, display_name, type, required, active, field_order, description, field_type_config, created_at, updated_at) VALUES
('ec000300-0004-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000004', 'order_number', 'Order Number', 'AUTO_NUMBER', true, true, 1, 'Unique order identifier', '{"prefix": "ORD-", "padding": 6, "startValue": 100001}', NOW(), NOW())
ON CONFLICT (collection_id, name) DO NOTHING;

INSERT INTO field (id, collection_id, name, display_name, type, required, active, field_order, description, relationship_type, reference_collection_id, cascade_delete, created_at, updated_at) VALUES
('ec000300-0004-0000-0000-000000000002', 'ec000100-0000-0000-0000-000000000004', 'customer', 'Customer', 'LOOKUP', true, true, 2, 'Order customer', 'LOOKUP', 'ec000100-0000-0000-0000-000000000003', false, NOW(), NOW()),
('ec000300-0004-0000-0000-000000000018', 'ec000100-0000-0000-0000-000000000004', 'discount_code', 'Discount Code', 'LOOKUP', false, true, 18, 'Applied discount code', 'LOOKUP', 'ec000100-0000-0000-0000-000000000009', false, NOW(), NOW())
ON CONFLICT (collection_id, name) DO NOTHING;

INSERT INTO field (id, collection_id, name, display_name, type, required, active, field_order, description, field_type_config, created_at, updated_at) VALUES
('ec000300-0004-0000-0000-000000000003', 'ec000100-0000-0000-0000-000000000004', 'status', 'Order Status', 'PICKLIST', true, true, 3, 'Current order status', '{"globalPicklistId": "ec000200-0000-0000-0000-000000000001", "restricted": true}', NOW(), NOW()),
('ec000300-0004-0000-0000-000000000010', 'ec000100-0000-0000-0000-000000000004', 'shipping_method', 'Shipping Method', 'PICKLIST', false, true, 10, 'Selected shipping', '{"globalPicklistId": "ec000200-0000-0000-0000-000000000009", "restricted": true}', NOW(), NOW())
ON CONFLICT (collection_id, name) DO NOTHING;

INSERT INTO field (id, collection_id, name, display_name, type, required, active, field_order, description, created_at, updated_at) VALUES
('ec000300-0004-0000-0000-000000000004', 'ec000100-0000-0000-0000-000000000004', 'order_date', 'Order Date', 'DATETIME', true, true, 4, 'Date order was placed', NOW(), NOW())
ON CONFLICT (collection_id, name) DO NOTHING;

INSERT INTO field (id, collection_id, name, display_name, type, required, active, field_order, description, field_type_config, created_at, updated_at) VALUES
('ec000300-0004-0000-0000-000000000005', 'ec000100-0000-0000-0000-000000000004', 'subtotal', 'Subtotal', 'CURRENCY', true, true, 5, 'Before discounts', '{"precision": 2, "defaultCurrencyCode": "USD"}', NOW(), NOW()),
('ec000300-0004-0000-0000-000000000006', 'ec000100-0000-0000-0000-000000000004', 'discount_amount', 'Discount', 'CURRENCY', false, true, 6, 'Discount applied', '{"precision": 2, "defaultCurrencyCode": "USD"}', NOW(), NOW()),
('ec000300-0004-0000-0000-000000000007', 'ec000100-0000-0000-0000-000000000004', 'shipping_cost', 'Shipping', 'CURRENCY', false, true, 7, 'Shipping cost', '{"precision": 2, "defaultCurrencyCode": "USD"}', NOW(), NOW()),
('ec000300-0004-0000-0000-000000000008', 'ec000100-0000-0000-0000-000000000004', 'tax_amount', 'Tax', 'CURRENCY', true, true, 8, 'Tax amount', '{"precision": 2, "defaultCurrencyCode": "USD"}', NOW(), NOW()),
('ec000300-0004-0000-0000-000000000009', 'ec000100-0000-0000-0000-000000000004', 'total_amount', 'Total', 'CURRENCY', true, true, 9, 'Order total', '{"precision": 2, "defaultCurrencyCode": "USD"}', NOW(), NOW())
ON CONFLICT (collection_id, name) DO NOTHING;

INSERT INTO field (id, collection_id, name, display_name, type, required, active, field_order, description, created_at, updated_at) VALUES
('ec000300-0004-0000-0000-000000000011', 'ec000100-0000-0000-0000-000000000004', 'shipping_street', 'Ship To Street', 'STRING', false, true, 11, 'Shipping street', NOW(), NOW()),
('ec000300-0004-0000-0000-000000000012', 'ec000100-0000-0000-0000-000000000004', 'shipping_city', 'Ship To City', 'STRING', false, true, 12, 'Shipping city', NOW(), NOW()),
('ec000300-0004-0000-0000-000000000013', 'ec000100-0000-0000-0000-000000000004', 'shipping_state', 'Ship To State', 'STRING', false, true, 13, 'Shipping state', NOW(), NOW()),
('ec000300-0004-0000-0000-000000000014', 'ec000100-0000-0000-0000-000000000004', 'shipping_zip', 'Ship To ZIP', 'STRING', false, true, 14, 'Shipping zip', NOW(), NOW()),
('ec000300-0004-0000-0000-000000000015', 'ec000100-0000-0000-0000-000000000004', 'shipping_country', 'Ship To Country', 'STRING', false, true, 15, 'Shipping country', NOW(), NOW()),
('ec000300-0004-0000-0000-000000000016', 'ec000100-0000-0000-0000-000000000004', 'tracking_number', 'Tracking Number', 'STRING', false, true, 16, 'Tracking number', NOW(), NOW()),
('ec000300-0004-0000-0000-000000000017', 'ec000100-0000-0000-0000-000000000004', 'notes', 'Notes', 'RICH_TEXT', false, true, 17, 'Order notes', NOW(), NOW())
ON CONFLICT (collection_id, name) DO NOTHING;

-- ---- Order Items fields (collection 0005) ----
INSERT INTO field (id, collection_id, name, display_name, type, required, active, field_order, description, relationship_type, reference_collection_id, cascade_delete, created_at, updated_at) VALUES
('ec000300-0005-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000005', 'order_ref', 'Order', 'MASTER_DETAIL', true, true, 1, 'Parent order', 'MASTER_DETAIL', 'ec000100-0000-0000-0000-000000000004', true, NOW(), NOW()),
('ec000300-0005-0000-0000-000000000002', 'ec000100-0000-0000-0000-000000000005', 'product', 'Product', 'LOOKUP', true, true, 2, 'Product ordered', 'LOOKUP', 'ec000100-0000-0000-0000-000000000002', false, NOW(), NOW())
ON CONFLICT (collection_id, name) DO NOTHING;

INSERT INTO field (id, collection_id, name, display_name, type, required, active, field_order, description, field_type_config, created_at, updated_at) VALUES
('ec000300-0005-0000-0000-000000000005', 'ec000100-0000-0000-0000-000000000005', 'size', 'Size', 'PICKLIST', false, true, 5, 'Size ordered', '{"globalPicklistId": "ec000200-0000-0000-0000-000000000005", "restricted": true}', NOW(), NOW()),
('ec000300-0005-0000-0000-000000000006', 'ec000100-0000-0000-0000-000000000005', 'color', 'Color', 'PICKLIST', false, true, 6, 'Color ordered', '{"globalPicklistId": "ec000200-0000-0000-0000-000000000006", "restricted": true}', NOW(), NOW())
ON CONFLICT (collection_id, name) DO NOTHING;

INSERT INTO field (id, collection_id, name, display_name, type, required, active, field_order, description, created_at, updated_at) VALUES
('ec000300-0005-0000-0000-000000000003', 'ec000100-0000-0000-0000-000000000005', 'product_name', 'Product Name', 'STRING', true, true, 3, 'Snapshot of product name', NOW(), NOW()),
('ec000300-0005-0000-0000-000000000004', 'ec000100-0000-0000-0000-000000000005', 'sku', 'SKU', 'STRING', false, true, 4, 'Snapshot of SKU', NOW(), NOW()),
('ec000300-0005-0000-0000-000000000007', 'ec000100-0000-0000-0000-000000000005', 'quantity', 'Quantity', 'INTEGER', true, true, 7, 'Quantity ordered', NOW(), NOW())
ON CONFLICT (collection_id, name) DO NOTHING;

INSERT INTO field (id, collection_id, name, display_name, type, required, active, field_order, description, field_type_config, created_at, updated_at) VALUES
('ec000300-0005-0000-0000-000000000008', 'ec000100-0000-0000-0000-000000000005', 'unit_price', 'Unit Price', 'CURRENCY', true, true, 8, 'Price per unit', '{"precision": 2, "defaultCurrencyCode": "USD"}', NOW(), NOW()),
('ec000300-0005-0000-0000-000000000009', 'ec000100-0000-0000-0000-000000000005', 'line_total', 'Line Total', 'CURRENCY', true, true, 9, 'Total for line', '{"precision": 2, "defaultCurrencyCode": "USD"}', NOW(), NOW()),
('ec000300-0005-0000-0000-000000000010', 'ec000100-0000-0000-0000-000000000005', 'discount_amount', 'Discount', 'CURRENCY', false, true, 10, 'Line discount', '{"precision": 2, "defaultCurrencyCode": "USD"}', NOW(), NOW())
ON CONFLICT (collection_id, name) DO NOTHING;

-- ---- Payments fields (collection 0006) ----
INSERT INTO field (id, collection_id, name, display_name, type, required, active, field_order, description, relationship_type, reference_collection_id, cascade_delete, created_at, updated_at) VALUES
('ec000300-0006-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000006', 'order_ref', 'Order', 'LOOKUP', true, true, 1, 'Associated order', 'LOOKUP', 'ec000100-0000-0000-0000-000000000004', false, NOW(), NOW())
ON CONFLICT (collection_id, name) DO NOTHING;

INSERT INTO field (id, collection_id, name, display_name, type, required, active, field_order, description, field_type_config, created_at, updated_at) VALUES
('ec000300-0006-0000-0000-000000000002', 'ec000100-0000-0000-0000-000000000006', 'payment_method', 'Payment Method', 'PICKLIST', true, true, 2, 'Method of payment', '{"globalPicklistId": "ec000200-0000-0000-0000-000000000003", "restricted": true}', NOW(), NOW()),
('ec000300-0006-0000-0000-000000000003', 'ec000100-0000-0000-0000-000000000006', 'status', 'Payment Status', 'PICKLIST', true, true, 3, 'Payment status', '{"globalPicklistId": "ec000200-0000-0000-0000-000000000002", "restricted": true}', NOW(), NOW()),
('ec000300-0006-0000-0000-000000000004', 'ec000100-0000-0000-0000-000000000006', 'amount', 'Amount', 'CURRENCY', true, true, 4, 'Payment amount', '{"precision": 2, "defaultCurrencyCode": "USD"}', NOW(), NOW()),
('ec000300-0006-0000-0000-000000000008', 'ec000100-0000-0000-0000-000000000006', 'refund_amount', 'Refund Amount', 'CURRENCY', false, true, 8, 'Amount refunded', '{"precision": 2, "defaultCurrencyCode": "USD"}', NOW(), NOW())
ON CONFLICT (collection_id, name) DO NOTHING;

INSERT INTO field (id, collection_id, name, display_name, type, required, active, unique_constraint, field_order, description, created_at, updated_at) VALUES
('ec000300-0006-0000-0000-000000000005', 'ec000100-0000-0000-0000-000000000006', 'transaction_id', 'Transaction ID', 'EXTERNAL_ID', false, true, true, 5, 'Gateway transaction ID', NOW(), NOW())
ON CONFLICT (collection_id, name) DO NOTHING;

INSERT INTO field (id, collection_id, name, display_name, type, required, active, field_order, description, created_at, updated_at) VALUES
('ec000300-0006-0000-0000-000000000006', 'ec000100-0000-0000-0000-000000000006', 'payment_date', 'Payment Date', 'DATETIME', true, true, 6, 'Date of payment', NOW(), NOW()),
('ec000300-0006-0000-0000-000000000007', 'ec000100-0000-0000-0000-000000000006', 'gateway_response', 'Gateway Response', 'JSON', false, true, 7, 'Raw gateway response', NOW(), NOW()),
('ec000300-0006-0000-0000-000000000009', 'ec000100-0000-0000-0000-000000000006', 'refund_reason', 'Refund Reason', 'STRING', false, true, 9, 'Reason for refund', NOW(), NOW())
ON CONFLICT (collection_id, name) DO NOTHING;

-- ---- Inventory fields (collection 0007) ----
INSERT INTO field (id, collection_id, name, display_name, type, required, active, field_order, description, relationship_type, reference_collection_id, cascade_delete, created_at, updated_at) VALUES
('ec000300-0007-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000007', 'product', 'Product', 'LOOKUP', true, true, 1, 'Product', 'LOOKUP', 'ec000100-0000-0000-0000-000000000002', false, NOW(), NOW())
ON CONFLICT (collection_id, name) DO NOTHING;

INSERT INTO field (id, collection_id, name, display_name, type, required, active, field_order, description, field_type_config, created_at, updated_at) VALUES
('ec000300-0007-0000-0000-000000000002', 'ec000100-0000-0000-0000-000000000007', 'size', 'Size', 'PICKLIST', true, true, 2, 'Size variant', '{"globalPicklistId": "ec000200-0000-0000-0000-000000000005", "restricted": true}', NOW(), NOW()),
('ec000300-0007-0000-0000-000000000003', 'ec000100-0000-0000-0000-000000000007', 'color', 'Color', 'PICKLIST', true, true, 3, 'Color variant', '{"globalPicklistId": "ec000200-0000-0000-0000-000000000006", "restricted": true}', NOW(), NOW())
ON CONFLICT (collection_id, name) DO NOTHING;

INSERT INTO field (id, collection_id, name, display_name, type, required, active, field_order, description, created_at, updated_at) VALUES
('ec000300-0007-0000-0000-000000000004', 'ec000100-0000-0000-0000-000000000007', 'quantity_on_hand', 'On Hand', 'INTEGER', true, true, 4, 'Current stock', NOW(), NOW()),
('ec000300-0007-0000-0000-000000000005', 'ec000100-0000-0000-0000-000000000007', 'quantity_reserved', 'Reserved', 'INTEGER', false, true, 5, 'Reserved for orders', NOW(), NOW()),
('ec000300-0007-0000-0000-000000000006', 'ec000100-0000-0000-0000-000000000007', 'quantity_available', 'Available', 'INTEGER', false, true, 6, 'Available to sell', NOW(), NOW()),
('ec000300-0007-0000-0000-000000000007', 'ec000100-0000-0000-0000-000000000007', 'reorder_point', 'Reorder Point', 'INTEGER', false, true, 7, 'Low stock threshold', NOW(), NOW()),
('ec000300-0007-0000-0000-000000000008', 'ec000100-0000-0000-0000-000000000007', 'reorder_quantity', 'Reorder Qty', 'INTEGER', false, true, 8, 'Quantity to reorder', NOW(), NOW()),
('ec000300-0007-0000-0000-000000000009', 'ec000100-0000-0000-0000-000000000007', 'warehouse_location', 'Location', 'STRING', false, true, 9, 'Warehouse location', NOW(), NOW()),
('ec000300-0007-0000-0000-000000000010', 'ec000100-0000-0000-0000-000000000007', 'last_restock_date', 'Last Restocked', 'DATE', false, true, 10, 'Last restock date', NOW(), NOW())
ON CONFLICT (collection_id, name) DO NOTHING;

-- ---- Promotions fields (collection 0008) ----
INSERT INTO field (id, collection_id, name, display_name, type, required, active, field_order, description, created_at, updated_at) VALUES
('ec000300-0008-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000008', 'name', 'Name', 'STRING', true, true, 1, 'Promotion name', NOW(), NOW()),
('ec000300-0008-0000-0000-000000000002', 'ec000100-0000-0000-0000-000000000008', 'description', 'Description', 'RICH_TEXT', false, true, 2, 'Description', NOW(), NOW())
ON CONFLICT (collection_id, name) DO NOTHING;

INSERT INTO field (id, collection_id, name, display_name, type, required, active, field_order, description, field_type_config, created_at, updated_at) VALUES
('ec000300-0008-0000-0000-000000000003', 'ec000100-0000-0000-0000-000000000008', 'discount_type', 'Discount Type', 'PICKLIST', true, true, 3, 'Type of discount', '{"globalPicklistId": "ec000200-0000-0000-0000-000000000008", "restricted": true}', NOW(), NOW()),
('ec000300-0008-0000-0000-000000000007', 'ec000100-0000-0000-0000-000000000008', 'minimum_purchase', 'Min Purchase', 'CURRENCY', false, true, 7, 'Minimum order amount', '{"precision": 2, "defaultCurrencyCode": "USD"}', NOW(), NOW())
ON CONFLICT (collection_id, name) DO NOTHING;

INSERT INTO field (id, collection_id, name, display_name, type, required, active, field_order, description, created_at, updated_at) VALUES
('ec000300-0008-0000-0000-000000000004', 'ec000100-0000-0000-0000-000000000008', 'discount_value', 'Discount Value', 'DOUBLE', true, true, 4, 'Amount or percentage', NOW(), NOW()),
('ec000300-0008-0000-0000-000000000005', 'ec000100-0000-0000-0000-000000000008', 'start_date', 'Start Date', 'DATETIME', true, true, 5, 'Start date', NOW(), NOW()),
('ec000300-0008-0000-0000-000000000006', 'ec000100-0000-0000-0000-000000000008', 'end_date', 'End Date', 'DATETIME', true, true, 6, 'End date', NOW(), NOW()),
('ec000300-0008-0000-0000-000000000008', 'ec000100-0000-0000-0000-000000000008', 'max_uses', 'Max Uses', 'INTEGER', false, true, 8, 'Maximum redemptions', NOW(), NOW()),
('ec000300-0008-0000-0000-000000000009', 'ec000100-0000-0000-0000-000000000008', 'current_uses', 'Current Uses', 'INTEGER', false, true, 9, 'Redemption count', NOW(), NOW()),
('ec000300-0008-0000-0000-000000000010', 'ec000100-0000-0000-0000-000000000008', 'is_active', 'Active', 'BOOLEAN', true, true, 10, 'Active flag', NOW(), NOW()),
('ec000300-0008-0000-0000-000000000012', 'ec000100-0000-0000-0000-000000000008', 'banner_image_url', 'Banner Image', 'URL', false, true, 12, 'Banner image', NOW(), NOW())
ON CONFLICT (collection_id, name) DO NOTHING;

INSERT INTO field (id, collection_id, name, display_name, type, required, active, field_order, description, relationship_type, reference_collection_id, cascade_delete, created_at, updated_at) VALUES
('ec000300-0008-0000-0000-000000000011', 'ec000100-0000-0000-0000-000000000008', 'applies_to_category', 'Category', 'LOOKUP', false, true, 11, 'Applies to category', 'LOOKUP', 'ec000100-0000-0000-0000-000000000001', false, NOW(), NOW())
ON CONFLICT (collection_id, name) DO NOTHING;

-- ---- Discount Codes fields (collection 0009) ----
INSERT INTO field (id, collection_id, name, display_name, type, required, active, unique_constraint, indexed, field_order, description, created_at, updated_at) VALUES
('ec000300-0009-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000009', 'code', 'Code', 'STRING', true, true, true, true, 1, 'Discount code', NOW(), NOW())
ON CONFLICT (collection_id, name) DO NOTHING;

INSERT INTO field (id, collection_id, name, display_name, type, required, active, field_order, description, relationship_type, reference_collection_id, cascade_delete, created_at, updated_at) VALUES
('ec000300-0009-0000-0000-000000000002', 'ec000100-0000-0000-0000-000000000009', 'promotion', 'Promotion', 'LOOKUP', true, true, 2, 'Parent promotion', 'LOOKUP', 'ec000100-0000-0000-0000-000000000008', false, NOW(), NOW())
ON CONFLICT (collection_id, name) DO NOTHING;

INSERT INTO field (id, collection_id, name, display_name, type, required, active, field_order, description, field_type_config, created_at, updated_at) VALUES
('ec000300-0009-0000-0000-000000000003', 'ec000100-0000-0000-0000-000000000009', 'discount_type', 'Discount Type', 'PICKLIST', true, true, 3, 'Type of discount', '{"globalPicklistId": "ec000200-0000-0000-0000-000000000008", "restricted": true}', NOW(), NOW()),
('ec000300-0009-0000-0000-000000000007', 'ec000100-0000-0000-0000-000000000009', 'min_order_amount', 'Min Order', 'CURRENCY', false, true, 7, 'Minimum order amount', '{"precision": 2, "defaultCurrencyCode": "USD"}', NOW(), NOW())
ON CONFLICT (collection_id, name) DO NOTHING;

INSERT INTO field (id, collection_id, name, display_name, type, required, active, field_order, description, created_at, updated_at) VALUES
('ec000300-0009-0000-0000-000000000004', 'ec000100-0000-0000-0000-000000000009', 'discount_value', 'Discount Value', 'DOUBLE', true, true, 4, 'Amount or percentage', NOW(), NOW()),
('ec000300-0009-0000-0000-000000000005', 'ec000100-0000-0000-0000-000000000009', 'max_uses', 'Max Uses', 'INTEGER', false, true, 5, 'Max uses allowed', NOW(), NOW()),
('ec000300-0009-0000-0000-000000000006', 'ec000100-0000-0000-0000-000000000009', 'times_used', 'Times Used', 'INTEGER', false, true, 6, 'Times redeemed', NOW(), NOW()),
('ec000300-0009-0000-0000-000000000008', 'ec000100-0000-0000-0000-000000000009', 'start_date', 'Valid From', 'DATETIME', true, true, 8, 'Validity start', NOW(), NOW()),
('ec000300-0009-0000-0000-000000000009', 'ec000100-0000-0000-0000-000000000009', 'end_date', 'Valid Until', 'DATETIME', true, true, 9, 'Validity end', NOW(), NOW()),
('ec000300-0009-0000-0000-000000000010', 'ec000100-0000-0000-0000-000000000009', 'is_active', 'Active', 'BOOLEAN', true, true, 10, 'Active flag', NOW(), NOW())
ON CONFLICT (collection_id, name) DO NOTHING;

-- ============================================================================
-- SECTION 5: PAGE LAYOUTS
-- ============================================================================

INSERT INTO page_layout (id, tenant_id, collection_id, name, description, layout_type, is_default, created_at, updated_at) VALUES
('ec000500-0001-0000-0000-000000000001', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000001', 'Category Layout', 'Standard category layout', 'DETAIL', true, NOW(), NOW()),
('ec000500-0002-0000-0000-000000000001', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000002', 'Product Layout', 'Standard product layout', 'DETAIL', true, NOW(), NOW()),
('ec000500-0003-0000-0000-000000000001', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000003', 'Customer Layout', 'Standard customer layout', 'DETAIL', true, NOW(), NOW()),
('ec000500-0004-0000-0000-000000000001', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000004', 'Order Layout', 'Standard order layout', 'DETAIL', true, NOW(), NOW()),
('ec000500-0005-0000-0000-000000000001', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000005', 'Order Item Layout', 'Standard order item layout', 'DETAIL', true, NOW(), NOW()),
('ec000500-0006-0000-0000-000000000001', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000006', 'Payment Layout', 'Standard payment layout', 'DETAIL', true, NOW(), NOW()),
('ec000500-0007-0000-0000-000000000001', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000007', 'Inventory Layout', 'Standard inventory layout', 'DETAIL', true, NOW(), NOW()),
('ec000500-0008-0000-0000-000000000001', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000008', 'Promotion Layout', 'Standard promotion layout', 'DETAIL', true, NOW(), NOW()),
('ec000500-0009-0000-0000-000000000001', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000009', 'Discount Code Layout', 'Standard discount code layout', 'DETAIL', true, NOW(), NOW());

-- ---- Layout Sections ----

-- Categories: 2 sections
INSERT INTO layout_section (id, layout_id, heading, columns, sort_order, collapsed, style, section_type) VALUES
('ec000600-0001-0001-0000-000000000001', 'ec000500-0001-0000-0000-000000000001', 'Category Information', 2, 0, false, 'DEFAULT', 'STANDARD'),
('ec000600-0001-0002-0000-000000000001', 'ec000500-0001-0000-0000-000000000001', 'Details', 1, 1, false, 'DEFAULT', 'STANDARD');

-- Products: 3 sections
INSERT INTO layout_section (id, layout_id, heading, columns, sort_order, collapsed, style, section_type) VALUES
('ec000600-0002-0001-0000-000000000001', 'ec000500-0002-0000-0000-000000000001', 'Product Information', 2, 0, false, 'DEFAULT', 'STANDARD'),
('ec000600-0002-0002-0000-000000000001', 'ec000500-0002-0000-0000-000000000001', 'Attributes', 2, 1, false, 'DEFAULT', 'STANDARD'),
('ec000600-0002-0003-0000-000000000001', 'ec000500-0002-0000-0000-000000000001', 'Media & Tags', 1, 2, false, 'DEFAULT', 'STANDARD');

-- Customers: 3 sections
INSERT INTO layout_section (id, layout_id, heading, columns, sort_order, collapsed, style, section_type) VALUES
('ec000600-0003-0001-0000-000000000001', 'ec000500-0003-0000-0000-000000000001', 'Contact Information', 2, 0, false, 'DEFAULT', 'STANDARD'),
('ec000600-0003-0002-0000-000000000001', 'ec000500-0003-0000-0000-000000000001', 'Shipping Address', 2, 1, false, 'DEFAULT', 'STANDARD'),
('ec000600-0003-0003-0000-000000000001', 'ec000500-0003-0000-0000-000000000001', 'Billing Address', 2, 2, false, 'DEFAULT', 'STANDARD');

-- Orders: 3 sections
INSERT INTO layout_section (id, layout_id, heading, columns, sort_order, collapsed, style, section_type) VALUES
('ec000600-0004-0001-0000-000000000001', 'ec000500-0004-0000-0000-000000000001', 'Order Information', 2, 0, false, 'DEFAULT', 'STANDARD'),
('ec000600-0004-0002-0000-000000000001', 'ec000500-0004-0000-0000-000000000001', 'Financials', 2, 1, false, 'DEFAULT', 'STANDARD'),
('ec000600-0004-0003-0000-000000000001', 'ec000500-0004-0000-0000-000000000001', 'Shipping Address', 2, 2, false, 'DEFAULT', 'STANDARD');

-- Order Items: 2 sections
INSERT INTO layout_section (id, layout_id, heading, columns, sort_order, collapsed, style, section_type) VALUES
('ec000600-0005-0001-0000-000000000001', 'ec000500-0005-0000-0000-000000000001', 'Line Item Details', 2, 0, false, 'DEFAULT', 'STANDARD'),
('ec000600-0005-0002-0000-000000000001', 'ec000500-0005-0000-0000-000000000001', 'Pricing', 2, 1, false, 'DEFAULT', 'STANDARD');

-- Payments: 2 sections
INSERT INTO layout_section (id, layout_id, heading, columns, sort_order, collapsed, style, section_type) VALUES
('ec000600-0006-0001-0000-000000000001', 'ec000500-0006-0000-0000-000000000001', 'Payment Information', 2, 0, false, 'DEFAULT', 'STANDARD'),
('ec000600-0006-0002-0000-000000000001', 'ec000500-0006-0000-0000-000000000001', 'Refund Details', 2, 1, false, 'DEFAULT', 'STANDARD');

-- Inventory: 2 sections
INSERT INTO layout_section (id, layout_id, heading, columns, sort_order, collapsed, style, section_type) VALUES
('ec000600-0007-0001-0000-000000000001', 'ec000500-0007-0000-0000-000000000001', 'Stock Details', 2, 0, false, 'DEFAULT', 'STANDARD'),
('ec000600-0007-0002-0000-000000000001', 'ec000500-0007-0000-0000-000000000001', 'Quantities', 2, 1, false, 'DEFAULT', 'STANDARD');

-- Promotions: 2 sections
INSERT INTO layout_section (id, layout_id, heading, columns, sort_order, collapsed, style, section_type) VALUES
('ec000600-0008-0001-0000-000000000001', 'ec000500-0008-0000-0000-000000000001', 'Promotion Details', 2, 0, false, 'DEFAULT', 'STANDARD'),
('ec000600-0008-0002-0000-000000000001', 'ec000500-0008-0000-0000-000000000001', 'Limits & Media', 2, 1, false, 'DEFAULT', 'STANDARD');

-- Discount Codes: 2 sections
INSERT INTO layout_section (id, layout_id, heading, columns, sort_order, collapsed, style, section_type) VALUES
('ec000600-0009-0001-0000-000000000001', 'ec000500-0009-0000-0000-000000000001', 'Code Details', 2, 0, false, 'DEFAULT', 'STANDARD'),
('ec000600-0009-0002-0000-000000000001', 'ec000500-0009-0000-0000-000000000001', 'Validity', 2, 1, false, 'DEFAULT', 'STANDARD');

-- ---- Layout Fields ----

-- Categories Section 1: name, display_name, category_type, gender, slug, is_active, parent_category, sort_order
INSERT INTO layout_field (id, section_id, field_id, column_number, sort_order, is_required_on_layout, is_read_only_on_layout) VALUES
('ec000700-0001-0001-0000-000000000001', 'ec000600-0001-0001-0000-000000000001', 'ec000300-0001-0000-0000-000000000001', 0, 0, true, false),
('ec000700-0001-0001-0000-000000000002', 'ec000600-0001-0001-0000-000000000001', 'ec000300-0001-0000-0000-000000000002', 1, 0, true, false),
('ec000700-0001-0001-0000-000000000003', 'ec000600-0001-0001-0000-000000000001', 'ec000300-0001-0000-0000-000000000004', 0, 1, true, false),
('ec000700-0001-0001-0000-000000000004', 'ec000600-0001-0001-0000-000000000001', 'ec000300-0001-0000-0000-000000000005', 1, 1, false, false),
('ec000700-0001-0001-0000-000000000005', 'ec000600-0001-0001-0000-000000000001', 'ec000300-0001-0000-0000-000000000010', 0, 2, true, false),
('ec000700-0001-0001-0000-000000000006', 'ec000600-0001-0001-0000-000000000001', 'ec000300-0001-0000-0000-000000000009', 1, 2, true, false),
('ec000700-0001-0001-0000-000000000007', 'ec000600-0001-0001-0000-000000000001', 'ec000300-0001-0000-0000-000000000006', 0, 3, false, false),
('ec000700-0001-0001-0000-000000000008', 'ec000600-0001-0001-0000-000000000001', 'ec000300-0001-0000-0000-000000000008', 1, 3, false, false);

-- Categories Section 2: description, image_url
INSERT INTO layout_field (id, section_id, field_id, column_number, sort_order, is_required_on_layout, is_read_only_on_layout) VALUES
('ec000700-0001-0002-0000-000000000001', 'ec000600-0001-0002-0000-000000000001', 'ec000300-0001-0000-0000-000000000003', 0, 0, false, false),
('ec000700-0001-0002-0000-000000000002', 'ec000600-0001-0002-0000-000000000001', 'ec000300-0001-0000-0000-000000000007', 0, 1, false, false);

-- Products Section 1: sku, name, category, status, gender, base_price, cost, compare_at_price
INSERT INTO layout_field (id, section_id, field_id, column_number, sort_order, is_required_on_layout, is_read_only_on_layout) VALUES
('ec000700-0002-0001-0000-000000000001', 'ec000600-0002-0001-0000-000000000001', 'ec000300-0002-0000-0000-000000000001', 0, 0, false, true),
('ec000700-0002-0001-0000-000000000002', 'ec000600-0002-0001-0000-000000000001', 'ec000300-0002-0000-0000-000000000002', 1, 0, true, false),
('ec000700-0002-0001-0000-000000000003', 'ec000600-0002-0001-0000-000000000001', 'ec000300-0002-0000-0000-000000000004', 0, 1, true, false),
('ec000700-0002-0001-0000-000000000004', 'ec000600-0002-0001-0000-000000000001', 'ec000300-0002-0000-0000-000000000005', 1, 1, true, false),
('ec000700-0002-0001-0000-000000000005', 'ec000600-0002-0001-0000-000000000001', 'ec000300-0002-0000-0000-000000000009', 0, 2, false, false),
('ec000700-0002-0001-0000-000000000006', 'ec000600-0002-0001-0000-000000000001', 'ec000300-0002-0000-0000-000000000006', 1, 2, true, false),
('ec000700-0002-0001-0000-000000000007', 'ec000600-0002-0001-0000-000000000001', 'ec000300-0002-0000-0000-000000000007', 0, 3, false, false),
('ec000700-0002-0001-0000-000000000008', 'ec000600-0002-0001-0000-000000000001', 'ec000300-0002-0000-0000-000000000008', 1, 3, false, false);

-- Products Section 2: available_sizes, available_colors, weight_grams, material, care_instructions, is_featured
INSERT INTO layout_field (id, section_id, field_id, column_number, sort_order, is_required_on_layout, is_read_only_on_layout) VALUES
('ec000700-0002-0002-0000-000000000001', 'ec000600-0002-0002-0000-000000000001', 'ec000300-0002-0000-0000-000000000010', 0, 0, false, false),
('ec000700-0002-0002-0000-000000000002', 'ec000600-0002-0002-0000-000000000001', 'ec000300-0002-0000-0000-000000000011', 1, 0, false, false),
('ec000700-0002-0002-0000-000000000003', 'ec000600-0002-0002-0000-000000000001', 'ec000300-0002-0000-0000-000000000012', 0, 1, false, false),
('ec000700-0002-0002-0000-000000000004', 'ec000600-0002-0002-0000-000000000001', 'ec000300-0002-0000-0000-000000000013', 1, 1, false, false),
('ec000700-0002-0002-0000-000000000005', 'ec000600-0002-0002-0000-000000000001', 'ec000300-0002-0000-0000-000000000014', 0, 2, false, false),
('ec000700-0002-0002-0000-000000000006', 'ec000600-0002-0002-0000-000000000001', 'ec000300-0002-0000-0000-000000000016', 1, 2, false, false);

-- Products Section 3: primary_image_url, tags, description
INSERT INTO layout_field (id, section_id, field_id, column_number, sort_order, is_required_on_layout, is_read_only_on_layout) VALUES
('ec000700-0002-0003-0000-000000000001', 'ec000600-0002-0003-0000-000000000001', 'ec000300-0002-0000-0000-000000000015', 0, 0, false, false),
('ec000700-0002-0003-0000-000000000002', 'ec000600-0002-0003-0000-000000000001', 'ec000300-0002-0000-0000-000000000017', 0, 1, false, false),
('ec000700-0002-0003-0000-000000000003', 'ec000600-0002-0003-0000-000000000001', 'ec000300-0002-0000-0000-000000000003', 0, 2, false, false);

-- Customers Section 1: first_name, last_name, email, phone, accepts_marketing
INSERT INTO layout_field (id, section_id, field_id, column_number, sort_order, is_required_on_layout, is_read_only_on_layout) VALUES
('ec000700-0003-0001-0000-000000000001', 'ec000600-0003-0001-0000-000000000001', 'ec000300-0003-0000-0000-000000000002', 0, 0, true, false),
('ec000700-0003-0001-0000-000000000002', 'ec000600-0003-0001-0000-000000000001', 'ec000300-0003-0000-0000-000000000003', 1, 0, true, false),
('ec000700-0003-0001-0000-000000000003', 'ec000600-0003-0001-0000-000000000001', 'ec000300-0003-0000-0000-000000000001', 0, 1, true, false),
('ec000700-0003-0001-0000-000000000004', 'ec000600-0003-0001-0000-000000000001', 'ec000300-0003-0000-0000-000000000004', 1, 1, false, false),
('ec000700-0003-0001-0000-000000000005', 'ec000600-0003-0001-0000-000000000001', 'ec000300-0003-0000-0000-000000000018', 0, 2, false, false),
('ec000700-0003-0001-0000-000000000006', 'ec000600-0003-0001-0000-000000000001', 'ec000300-0003-0000-0000-000000000015', 1, 2, false, true),
('ec000700-0003-0001-0000-000000000007', 'ec000600-0003-0001-0000-000000000001', 'ec000300-0003-0000-0000-000000000016', 0, 3, false, true);

-- Customers Section 2: shipping address
INSERT INTO layout_field (id, section_id, field_id, column_number, sort_order, is_required_on_layout, is_read_only_on_layout) VALUES
('ec000700-0003-0002-0000-000000000001', 'ec000600-0003-0002-0000-000000000001', 'ec000300-0003-0000-0000-000000000005', 0, 0, false, false),
('ec000700-0003-0002-0000-000000000002', 'ec000600-0003-0002-0000-000000000001', 'ec000300-0003-0000-0000-000000000006', 1, 0, false, false),
('ec000700-0003-0002-0000-000000000003', 'ec000600-0003-0002-0000-000000000001', 'ec000300-0003-0000-0000-000000000007', 0, 1, false, false),
('ec000700-0003-0002-0000-000000000004', 'ec000600-0003-0002-0000-000000000001', 'ec000300-0003-0000-0000-000000000008', 1, 1, false, false),
('ec000700-0003-0002-0000-000000000005', 'ec000600-0003-0002-0000-000000000001', 'ec000300-0003-0000-0000-000000000009', 0, 2, false, false);

-- Customers Section 3: billing address
INSERT INTO layout_field (id, section_id, field_id, column_number, sort_order, is_required_on_layout, is_read_only_on_layout) VALUES
('ec000700-0003-0003-0000-000000000001', 'ec000600-0003-0003-0000-000000000001', 'ec000300-0003-0000-0000-000000000010', 0, 0, false, false),
('ec000700-0003-0003-0000-000000000002', 'ec000600-0003-0003-0000-000000000001', 'ec000300-0003-0000-0000-000000000011', 1, 0, false, false),
('ec000700-0003-0003-0000-000000000003', 'ec000600-0003-0003-0000-000000000001', 'ec000300-0003-0000-0000-000000000012', 0, 1, false, false),
('ec000700-0003-0003-0000-000000000004', 'ec000600-0003-0003-0000-000000000001', 'ec000300-0003-0000-0000-000000000013', 1, 1, false, false),
('ec000700-0003-0003-0000-000000000005', 'ec000600-0003-0003-0000-000000000001', 'ec000300-0003-0000-0000-000000000014', 0, 2, false, false);

-- Orders Section 1: order_number, customer, status, order_date, shipping_method, tracking, discount_code
INSERT INTO layout_field (id, section_id, field_id, column_number, sort_order, is_required_on_layout, is_read_only_on_layout) VALUES
('ec000700-0004-0001-0000-000000000001', 'ec000600-0004-0001-0000-000000000001', 'ec000300-0004-0000-0000-000000000001', 0, 0, false, true),
('ec000700-0004-0001-0000-000000000002', 'ec000600-0004-0001-0000-000000000001', 'ec000300-0004-0000-0000-000000000002', 1, 0, true, false),
('ec000700-0004-0001-0000-000000000003', 'ec000600-0004-0001-0000-000000000001', 'ec000300-0004-0000-0000-000000000003', 0, 1, true, false),
('ec000700-0004-0001-0000-000000000004', 'ec000600-0004-0001-0000-000000000001', 'ec000300-0004-0000-0000-000000000004', 1, 1, true, false),
('ec000700-0004-0001-0000-000000000005', 'ec000600-0004-0001-0000-000000000001', 'ec000300-0004-0000-0000-000000000010', 0, 2, false, false),
('ec000700-0004-0001-0000-000000000006', 'ec000600-0004-0001-0000-000000000001', 'ec000300-0004-0000-0000-000000000016', 1, 2, false, false),
('ec000700-0004-0001-0000-000000000007', 'ec000600-0004-0001-0000-000000000001', 'ec000300-0004-0000-0000-000000000018', 0, 3, false, false);

-- Orders Section 2: subtotal, discount, shipping, tax, total
INSERT INTO layout_field (id, section_id, field_id, column_number, sort_order, is_required_on_layout, is_read_only_on_layout) VALUES
('ec000700-0004-0002-0000-000000000001', 'ec000600-0004-0002-0000-000000000001', 'ec000300-0004-0000-0000-000000000005', 0, 0, true, false),
('ec000700-0004-0002-0000-000000000002', 'ec000600-0004-0002-0000-000000000001', 'ec000300-0004-0000-0000-000000000006', 1, 0, false, false),
('ec000700-0004-0002-0000-000000000003', 'ec000600-0004-0002-0000-000000000001', 'ec000300-0004-0000-0000-000000000007', 0, 1, false, false),
('ec000700-0004-0002-0000-000000000004', 'ec000600-0004-0002-0000-000000000001', 'ec000300-0004-0000-0000-000000000008', 1, 1, true, false),
('ec000700-0004-0002-0000-000000000005', 'ec000600-0004-0002-0000-000000000001', 'ec000300-0004-0000-0000-000000000009', 0, 2, true, true);

-- Orders Section 3: shipping address
INSERT INTO layout_field (id, section_id, field_id, column_number, sort_order, is_required_on_layout, is_read_only_on_layout) VALUES
('ec000700-0004-0003-0000-000000000001', 'ec000600-0004-0003-0000-000000000001', 'ec000300-0004-0000-0000-000000000011', 0, 0, false, false),
('ec000700-0004-0003-0000-000000000002', 'ec000600-0004-0003-0000-000000000001', 'ec000300-0004-0000-0000-000000000012', 1, 0, false, false),
('ec000700-0004-0003-0000-000000000003', 'ec000600-0004-0003-0000-000000000001', 'ec000300-0004-0000-0000-000000000013', 0, 1, false, false),
('ec000700-0004-0003-0000-000000000004', 'ec000600-0004-0003-0000-000000000001', 'ec000300-0004-0000-0000-000000000014', 1, 1, false, false),
('ec000700-0004-0003-0000-000000000005', 'ec000600-0004-0003-0000-000000000001', 'ec000300-0004-0000-0000-000000000015', 0, 2, false, false);

-- Order Items Section 1: order, product, product_name, sku, size, color
INSERT INTO layout_field (id, section_id, field_id, column_number, sort_order, is_required_on_layout, is_read_only_on_layout) VALUES
('ec000700-0005-0001-0000-000000000001', 'ec000600-0005-0001-0000-000000000001', 'ec000300-0005-0000-0000-000000000001', 0, 0, true, false),
('ec000700-0005-0001-0000-000000000002', 'ec000600-0005-0001-0000-000000000001', 'ec000300-0005-0000-0000-000000000002', 1, 0, true, false),
('ec000700-0005-0001-0000-000000000003', 'ec000600-0005-0001-0000-000000000001', 'ec000300-0005-0000-0000-000000000003', 0, 1, true, false),
('ec000700-0005-0001-0000-000000000004', 'ec000600-0005-0001-0000-000000000001', 'ec000300-0005-0000-0000-000000000004', 1, 1, false, true),
('ec000700-0005-0001-0000-000000000005', 'ec000600-0005-0001-0000-000000000001', 'ec000300-0005-0000-0000-000000000005', 0, 2, false, false),
('ec000700-0005-0001-0000-000000000006', 'ec000600-0005-0001-0000-000000000001', 'ec000300-0005-0000-0000-000000000006', 1, 2, false, false);

-- Order Items Section 2: quantity, unit_price, discount, line_total
INSERT INTO layout_field (id, section_id, field_id, column_number, sort_order, is_required_on_layout, is_read_only_on_layout) VALUES
('ec000700-0005-0002-0000-000000000001', 'ec000600-0005-0002-0000-000000000001', 'ec000300-0005-0000-0000-000000000007', 0, 0, true, false),
('ec000700-0005-0002-0000-000000000002', 'ec000600-0005-0002-0000-000000000001', 'ec000300-0005-0000-0000-000000000008', 1, 0, true, false),
('ec000700-0005-0002-0000-000000000003', 'ec000600-0005-0002-0000-000000000001', 'ec000300-0005-0000-0000-000000000010', 0, 1, false, false),
('ec000700-0005-0002-0000-000000000004', 'ec000600-0005-0002-0000-000000000001', 'ec000300-0005-0000-0000-000000000009', 1, 1, true, true);

-- Payments Section 1: order, method, status, amount, date, transaction_id
INSERT INTO layout_field (id, section_id, field_id, column_number, sort_order, is_required_on_layout, is_read_only_on_layout) VALUES
('ec000700-0006-0001-0000-000000000001', 'ec000600-0006-0001-0000-000000000001', 'ec000300-0006-0000-0000-000000000001', 0, 0, true, false),
('ec000700-0006-0001-0000-000000000002', 'ec000600-0006-0001-0000-000000000001', 'ec000300-0006-0000-0000-000000000002', 1, 0, true, false),
('ec000700-0006-0001-0000-000000000003', 'ec000600-0006-0001-0000-000000000001', 'ec000300-0006-0000-0000-000000000003', 0, 1, true, false),
('ec000700-0006-0001-0000-000000000004', 'ec000600-0006-0001-0000-000000000001', 'ec000300-0006-0000-0000-000000000004', 1, 1, true, false),
('ec000700-0006-0001-0000-000000000005', 'ec000600-0006-0001-0000-000000000001', 'ec000300-0006-0000-0000-000000000006', 0, 2, true, false),
('ec000700-0006-0001-0000-000000000006', 'ec000600-0006-0001-0000-000000000001', 'ec000300-0006-0000-0000-000000000005', 1, 2, false, true);

-- Payments Section 2: refund_amount, refund_reason, gateway_response
INSERT INTO layout_field (id, section_id, field_id, column_number, sort_order, is_required_on_layout, is_read_only_on_layout) VALUES
('ec000700-0006-0002-0000-000000000001', 'ec000600-0006-0002-0000-000000000001', 'ec000300-0006-0000-0000-000000000008', 0, 0, false, false),
('ec000700-0006-0002-0000-000000000002', 'ec000600-0006-0002-0000-000000000001', 'ec000300-0006-0000-0000-000000000009', 1, 0, false, false),
('ec000700-0006-0002-0000-000000000003', 'ec000600-0006-0002-0000-000000000001', 'ec000300-0006-0000-0000-000000000007', 0, 1, false, true);

-- Inventory Section 1: product, size, color, location
INSERT INTO layout_field (id, section_id, field_id, column_number, sort_order, is_required_on_layout, is_read_only_on_layout) VALUES
('ec000700-0007-0001-0000-000000000001', 'ec000600-0007-0001-0000-000000000001', 'ec000300-0007-0000-0000-000000000001', 0, 0, true, false),
('ec000700-0007-0001-0000-000000000002', 'ec000600-0007-0001-0000-000000000001', 'ec000300-0007-0000-0000-000000000002', 1, 0, true, false),
('ec000700-0007-0001-0000-000000000003', 'ec000600-0007-0001-0000-000000000001', 'ec000300-0007-0000-0000-000000000003', 0, 1, true, false),
('ec000700-0007-0001-0000-000000000004', 'ec000600-0007-0001-0000-000000000001', 'ec000300-0007-0000-0000-000000000009', 1, 1, false, false);

-- Inventory Section 2: on_hand, reserved, available, reorder_point, reorder_qty, last_restock
INSERT INTO layout_field (id, section_id, field_id, column_number, sort_order, is_required_on_layout, is_read_only_on_layout) VALUES
('ec000700-0007-0002-0000-000000000001', 'ec000600-0007-0002-0000-000000000001', 'ec000300-0007-0000-0000-000000000004', 0, 0, true, false),
('ec000700-0007-0002-0000-000000000002', 'ec000600-0007-0002-0000-000000000001', 'ec000300-0007-0000-0000-000000000005', 1, 0, false, false),
('ec000700-0007-0002-0000-000000000003', 'ec000600-0007-0002-0000-000000000001', 'ec000300-0007-0000-0000-000000000006', 0, 1, false, true),
('ec000700-0007-0002-0000-000000000004', 'ec000600-0007-0002-0000-000000000001', 'ec000300-0007-0000-0000-000000000007', 1, 1, false, false),
('ec000700-0007-0002-0000-000000000005', 'ec000600-0007-0002-0000-000000000001', 'ec000300-0007-0000-0000-000000000008', 0, 2, false, false),
('ec000700-0007-0002-0000-000000000006', 'ec000600-0007-0002-0000-000000000001', 'ec000300-0007-0000-0000-000000000010', 1, 2, false, false);

-- Promotions Section 1: name, discount_type, discount_value, start, end, is_active, category
INSERT INTO layout_field (id, section_id, field_id, column_number, sort_order, is_required_on_layout, is_read_only_on_layout) VALUES
('ec000700-0008-0001-0000-000000000001', 'ec000600-0008-0001-0000-000000000001', 'ec000300-0008-0000-0000-000000000001', 0, 0, true, false),
('ec000700-0008-0001-0000-000000000002', 'ec000600-0008-0001-0000-000000000001', 'ec000300-0008-0000-0000-000000000003', 1, 0, true, false),
('ec000700-0008-0001-0000-000000000003', 'ec000600-0008-0001-0000-000000000001', 'ec000300-0008-0000-0000-000000000004', 0, 1, true, false),
('ec000700-0008-0001-0000-000000000004', 'ec000600-0008-0001-0000-000000000001', 'ec000300-0008-0000-0000-000000000010', 1, 1, true, false),
('ec000700-0008-0001-0000-000000000005', 'ec000600-0008-0001-0000-000000000001', 'ec000300-0008-0000-0000-000000000005', 0, 2, true, false),
('ec000700-0008-0001-0000-000000000006', 'ec000600-0008-0001-0000-000000000001', 'ec000300-0008-0000-0000-000000000006', 1, 2, true, false),
('ec000700-0008-0001-0000-000000000007', 'ec000600-0008-0001-0000-000000000001', 'ec000300-0008-0000-0000-000000000011', 0, 3, false, false);

-- Promotions Section 2: min_purchase, max_uses, current_uses, banner, description
INSERT INTO layout_field (id, section_id, field_id, column_number, sort_order, is_required_on_layout, is_read_only_on_layout) VALUES
('ec000700-0008-0002-0000-000000000001', 'ec000600-0008-0002-0000-000000000001', 'ec000300-0008-0000-0000-000000000007', 0, 0, false, false),
('ec000700-0008-0002-0000-000000000002', 'ec000600-0008-0002-0000-000000000001', 'ec000300-0008-0000-0000-000000000008', 1, 0, false, false),
('ec000700-0008-0002-0000-000000000003', 'ec000600-0008-0002-0000-000000000001', 'ec000300-0008-0000-0000-000000000009', 0, 1, false, true),
('ec000700-0008-0002-0000-000000000004', 'ec000600-0008-0002-0000-000000000001', 'ec000300-0008-0000-0000-000000000012', 1, 1, false, false),
('ec000700-0008-0002-0000-000000000005', 'ec000600-0008-0002-0000-000000000001', 'ec000300-0008-0000-0000-000000000002', 0, 2, false, false);

-- Discount Codes Section 1: code, promotion, discount_type, discount_value, is_active
INSERT INTO layout_field (id, section_id, field_id, column_number, sort_order, is_required_on_layout, is_read_only_on_layout) VALUES
('ec000700-0009-0001-0000-000000000001', 'ec000600-0009-0001-0000-000000000001', 'ec000300-0009-0000-0000-000000000001', 0, 0, true, false),
('ec000700-0009-0001-0000-000000000002', 'ec000600-0009-0001-0000-000000000001', 'ec000300-0009-0000-0000-000000000002', 1, 0, true, false),
('ec000700-0009-0001-0000-000000000003', 'ec000600-0009-0001-0000-000000000001', 'ec000300-0009-0000-0000-000000000003', 0, 1, true, false),
('ec000700-0009-0001-0000-000000000004', 'ec000600-0009-0001-0000-000000000001', 'ec000300-0009-0000-0000-000000000004', 1, 1, true, false),
('ec000700-0009-0001-0000-000000000005', 'ec000600-0009-0001-0000-000000000001', 'ec000300-0009-0000-0000-000000000010', 0, 2, true, false);

-- Discount Codes Section 2: start, end, max_uses, times_used, min_order
INSERT INTO layout_field (id, section_id, field_id, column_number, sort_order, is_required_on_layout, is_read_only_on_layout) VALUES
('ec000700-0009-0002-0000-000000000001', 'ec000600-0009-0002-0000-000000000001', 'ec000300-0009-0000-0000-000000000008', 0, 0, true, false),
('ec000700-0009-0002-0000-000000000002', 'ec000600-0009-0002-0000-000000000001', 'ec000300-0009-0000-0000-000000000009', 1, 0, true, false),
('ec000700-0009-0002-0000-000000000003', 'ec000600-0009-0002-0000-000000000001', 'ec000300-0009-0000-0000-000000000005', 0, 1, false, false),
('ec000700-0009-0002-0000-000000000004', 'ec000600-0009-0002-0000-000000000001', 'ec000300-0009-0000-0000-000000000006', 1, 1, false, true),
('ec000700-0009-0002-0000-000000000005', 'ec000600-0009-0002-0000-000000000001', 'ec000300-0009-0000-0000-000000000007', 0, 2, false, false);

-- ---- Related Lists (on Orders layout) ----
INSERT INTO layout_related_list (id, layout_id, related_collection_id, relationship_field_id, display_columns, sort_field, sort_direction, row_limit, sort_order) VALUES
('ec000900-0004-0000-0000-000000000001', 'ec000500-0004-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000005', 'ec000300-0005-0000-0000-000000000001', '["product_name", "size", "color", "quantity", "unit_price", "line_total"]', 'sort_order', 'ASC', 25, 0),
('ec000900-0004-0000-0000-000000000002', 'ec000500-0004-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000006', 'ec000300-0006-0000-0000-000000000001', '["payment_method", "status", "amount", "payment_date", "transaction_id"]', 'payment_date', 'DESC', 10, 1);

-- ---- Layout Assignments ----
INSERT INTO layout_assignment (id, tenant_id, collection_id, profile_id, layout_id) VALUES
('ec000c00-0001-0000-0000-000000000001', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000001', 'ec000000-0000-0000-0000-000000000010', 'ec000500-0001-0000-0000-000000000001'),
('ec000c00-0002-0000-0000-000000000001', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000002', 'ec000000-0000-0000-0000-000000000010', 'ec000500-0002-0000-0000-000000000001'),
('ec000c00-0003-0000-0000-000000000001', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000003', 'ec000000-0000-0000-0000-000000000010', 'ec000500-0003-0000-0000-000000000001'),
('ec000c00-0004-0000-0000-000000000001', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000004', 'ec000000-0000-0000-0000-000000000010', 'ec000500-0004-0000-0000-000000000001'),
('ec000c00-0005-0000-0000-000000000001', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000005', 'ec000000-0000-0000-0000-000000000010', 'ec000500-0005-0000-0000-000000000001'),
('ec000c00-0006-0000-0000-000000000001', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000006', 'ec000000-0000-0000-0000-000000000010', 'ec000500-0006-0000-0000-000000000001'),
('ec000c00-0007-0000-0000-000000000001', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000007', 'ec000000-0000-0000-0000-000000000010', 'ec000500-0007-0000-0000-000000000001'),
('ec000c00-0008-0000-0000-000000000001', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000008', 'ec000000-0000-0000-0000-000000000010', 'ec000500-0008-0000-0000-000000000001'),
('ec000c00-0009-0000-0000-000000000001', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000009', 'ec000000-0000-0000-0000-000000000010', 'ec000500-0009-0000-0000-000000000001');

-- ============================================================================
-- SECTION 6: LIST VIEWS
-- ============================================================================

-- Categories
INSERT INTO list_view (id, tenant_id, collection_id, name, created_by, visibility, is_default, columns, filter_logic, filters, sort_field, sort_direction, row_limit, created_at, updated_at) VALUES
('ec000800-0001-0000-0000-000000000001', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000001', 'All Categories', 'ec000000-0000-0000-0000-000000000020', 'PUBLIC', true, '["name", "category_type", "gender", "is_active"]', NULL, '[]', 'name', 'ASC', 50, NOW(), NOW()),
('ec000800-0001-0000-0000-000000000002', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000001', 'Active Categories', 'ec000000-0000-0000-0000-000000000020', 'PUBLIC', false, '["name", "category_type", "gender"]', 'AND', '[{"field": "is_active", "operator": "eq", "value": "true"}]', 'sort_order', 'ASC', 50, NOW(), NOW());

-- Products
INSERT INTO list_view (id, tenant_id, collection_id, name, created_by, visibility, is_default, columns, filter_logic, filters, sort_field, sort_direction, row_limit, created_at, updated_at) VALUES
('ec000800-0002-0000-0000-000000000001', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000002', 'All Products', 'ec000000-0000-0000-0000-000000000020', 'PUBLIC', true, '["sku", "name", "category", "status", "base_price"]', NULL, '[]', 'name', 'ASC', 50, NOW(), NOW()),
('ec000800-0002-0000-0000-000000000002', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000002', 'Active Products', 'ec000000-0000-0000-0000-000000000020', 'PUBLIC', false, '["sku", "name", "base_price", "is_featured"]', 'AND', '[{"field": "status", "operator": "eq", "value": "active"}]', 'base_price', 'DESC', 50, NOW(), NOW()),
('ec000800-0002-0000-0000-000000000003', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000002', 'Featured Products', 'ec000000-0000-0000-0000-000000000020', 'PUBLIC', false, '["sku", "name", "base_price", "category"]', 'AND', '[{"field": "is_featured", "operator": "eq", "value": "true"}]', 'name', 'ASC', 50, NOW(), NOW());

-- Customers
INSERT INTO list_view (id, tenant_id, collection_id, name, created_by, visibility, is_default, columns, filter_logic, filters, sort_field, sort_direction, row_limit, created_at, updated_at) VALUES
('ec000800-0003-0000-0000-000000000001', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000003', 'All Customers', 'ec000000-0000-0000-0000-000000000020', 'PUBLIC', true, '["email", "first_name", "last_name", "phone", "total_orders", "total_spent"]', NULL, '[]', 'last_name', 'ASC', 50, NOW(), NOW()),
('ec000800-0003-0000-0000-000000000002', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000003', 'Recent Customers', 'ec000000-0000-0000-0000-000000000020', 'PUBLIC', false, '["email", "first_name", "last_name", "total_spent"]', NULL, '[]', 'created_at', 'DESC', 25, NOW(), NOW());

-- Orders
INSERT INTO list_view (id, tenant_id, collection_id, name, created_by, visibility, is_default, columns, filter_logic, filters, sort_field, sort_direction, row_limit, created_at, updated_at) VALUES
('ec000800-0004-0000-0000-000000000001', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000004', 'All Orders', 'ec000000-0000-0000-0000-000000000020', 'PUBLIC', true, '["order_number", "customer", "status", "order_date", "total_amount"]', NULL, '[]', 'order_date', 'DESC', 50, NOW(), NOW()),
('ec000800-0004-0000-0000-000000000002', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000004', 'Open Orders', 'ec000000-0000-0000-0000-000000000020', 'PUBLIC', false, '["order_number", "customer", "status", "total_amount"]', 'OR', '[{"field": "status", "operator": "eq", "value": "pending"}, {"field": "status", "operator": "eq", "value": "confirmed"}, {"field": "status", "operator": "eq", "value": "processing"}]', 'order_date', 'ASC', 50, NOW(), NOW()),
('ec000800-0004-0000-0000-000000000003', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000004', 'Shipped Orders', 'ec000000-0000-0000-0000-000000000020', 'PUBLIC', false, '["order_number", "customer", "tracking_number", "total_amount"]', 'AND', '[{"field": "status", "operator": "eq", "value": "shipped"}]', 'order_date', 'DESC', 50, NOW(), NOW());

-- Order Items
INSERT INTO list_view (id, tenant_id, collection_id, name, created_by, visibility, is_default, columns, filter_logic, filters, sort_field, sort_direction, row_limit, created_at, updated_at) VALUES
('ec000800-0005-0000-0000-000000000001', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000005', 'All Line Items', 'ec000000-0000-0000-0000-000000000020', 'PUBLIC', true, '["order_ref", "product_name", "size", "color", "quantity", "line_total"]', NULL, '[]', 'created_at', 'DESC', 50, NOW(), NOW());

-- Payments
INSERT INTO list_view (id, tenant_id, collection_id, name, created_by, visibility, is_default, columns, filter_logic, filters, sort_field, sort_direction, row_limit, created_at, updated_at) VALUES
('ec000800-0006-0000-0000-000000000001', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000006', 'All Payments', 'ec000000-0000-0000-0000-000000000020', 'PUBLIC', true, '["order_ref", "payment_method", "status", "amount", "payment_date"]', NULL, '[]', 'payment_date', 'DESC', 50, NOW(), NOW()),
('ec000800-0006-0000-0000-000000000002', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000006', 'Pending Payments', 'ec000000-0000-0000-0000-000000000020', 'PUBLIC', false, '["order_ref", "amount", "payment_date"]', 'AND', '[{"field": "status", "operator": "eq", "value": "pending"}]', 'payment_date', 'ASC', 50, NOW(), NOW());

-- Inventory
INSERT INTO list_view (id, tenant_id, collection_id, name, created_by, visibility, is_default, columns, filter_logic, filters, sort_field, sort_direction, row_limit, created_at, updated_at) VALUES
('ec000800-0007-0000-0000-000000000001', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000007', 'All Inventory', 'ec000000-0000-0000-0000-000000000020', 'PUBLIC', true, '["product", "size", "color", "quantity_on_hand", "quantity_available"]', NULL, '[]', 'product', 'ASC', 50, NOW(), NOW()),
('ec000800-0007-0000-0000-000000000002', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000007', 'Low Stock', 'ec000000-0000-0000-0000-000000000020', 'PUBLIC', false, '["product", "size", "color", "quantity_on_hand", "reorder_point"]', 'AND', '[{"field": "quantity_on_hand", "operator": "lt", "value": "10"}]', 'quantity_on_hand', 'ASC', 50, NOW(), NOW());

-- Promotions
INSERT INTO list_view (id, tenant_id, collection_id, name, created_by, visibility, is_default, columns, filter_logic, filters, sort_field, sort_direction, row_limit, created_at, updated_at) VALUES
('ec000800-0008-0000-0000-000000000001', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000008', 'All Promotions', 'ec000000-0000-0000-0000-000000000020', 'PUBLIC', true, '["name", "discount_type", "discount_value", "start_date", "end_date", "is_active"]', NULL, '[]', 'start_date', 'DESC', 50, NOW(), NOW()),
('ec000800-0008-0000-0000-000000000002', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000008', 'Active Promotions', 'ec000000-0000-0000-0000-000000000020', 'PUBLIC', false, '["name", "discount_type", "discount_value", "end_date"]', 'AND', '[{"field": "is_active", "operator": "eq", "value": "true"}]', 'end_date', 'ASC', 50, NOW(), NOW());

-- Discount Codes
INSERT INTO list_view (id, tenant_id, collection_id, name, created_by, visibility, is_default, columns, filter_logic, filters, sort_field, sort_direction, row_limit, created_at, updated_at) VALUES
('ec000800-0009-0000-0000-000000000001', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000009', 'All Discount Codes', 'ec000000-0000-0000-0000-000000000020', 'PUBLIC', true, '["code", "promotion", "discount_type", "discount_value", "times_used", "is_active"]', NULL, '[]', 'code', 'ASC', 50, NOW(), NOW());

-- ============================================================================
-- SECTION 7: VALIDATION RULES
-- ============================================================================

INSERT INTO validation_rule (id, tenant_id, collection_id, name, description, active, error_condition_formula, error_message, error_field, evaluate_on, created_at, updated_at) VALUES
('ec000a00-0002-0000-0000-000000000001', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000002', 'Price Must Be Positive', 'Base price must be greater than zero', true, 'base_price <= 0', 'Base price must be greater than zero', 'base_price', 'CREATE_AND_UPDATE', NOW(), NOW()),
('ec000a00-0002-0000-0000-000000000002', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000002', 'Cost Less Than Price', 'Cost must be less than selling price', true, 'cost >= base_price', 'Cost must be less than the selling price', 'cost', 'CREATE_AND_UPDATE', NOW(), NOW()),
('ec000a00-0004-0000-0000-000000000001', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000004', 'Total Must Be Positive', 'Order total must be positive', true, 'total_amount <= 0', 'Order total must be greater than zero', 'total_amount', 'CREATE_AND_UPDATE', NOW(), NOW()),
('ec000a00-0005-0000-0000-000000000001', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000005', 'Quantity Positive', 'Quantity must be at least 1', true, 'quantity <= 0', 'Quantity must be at least 1', 'quantity', 'CREATE_AND_UPDATE', NOW(), NOW()),
('ec000a00-0005-0000-0000-000000000002', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000005', 'Line Total Matches', 'Line total must equal quantity times price', true, 'line_total != quantity * unit_price', 'Line total must equal quantity times unit price', 'line_total', 'CREATE_AND_UPDATE', NOW(), NOW()),
('ec000a00-0006-0000-0000-000000000001', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000006', 'Amount Positive', 'Payment amount must be positive', true, 'amount <= 0', 'Payment amount must be greater than zero', 'amount', 'CREATE_AND_UPDATE', NOW(), NOW()),
('ec000a00-0007-0000-0000-000000000001', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000007', 'On Hand Not Negative', 'Stock cannot be negative', true, 'quantity_on_hand < 0', 'On-hand quantity cannot be negative', 'quantity_on_hand', 'CREATE_AND_UPDATE', NOW(), NOW()),
('ec000a00-0008-0000-0000-000000000001', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000008', 'Promo End After Start', 'End date must be after start', true, 'end_date <= start_date', 'End date must be after start date', 'end_date', 'CREATE_AND_UPDATE', NOW(), NOW()),
('ec000a00-0009-0000-0000-000000000001', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000009', 'Code End After Start', 'End date must be after start', true, 'end_date <= start_date', 'End date must be after start date', 'end_date', 'CREATE_AND_UPDATE', NOW(), NOW()),
('ec000a00-0009-0000-0000-000000000002', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000009', 'Value Positive', 'Discount value must be positive', true, 'discount_value <= 0', 'Discount value must be greater than zero', 'discount_value', 'CREATE_AND_UPDATE', NOW(), NOW());

-- ============================================================================
-- SECTION 8: RECORD TYPES
-- ============================================================================

INSERT INTO record_type (id, tenant_id, collection_id, name, description, is_active, is_default, created_at, updated_at) VALUES
('ec000b00-0002-0000-0000-000000000001', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000002', 'Standard', 'Standard retail product', true, true, NOW(), NOW()),
('ec000b00-0002-0000-0000-000000000002', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000002', 'Custom', 'Custom or made-to-order product', true, false, NOW(), NOW()),
('ec000b00-0004-0000-0000-000000000001', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000004', 'Online', 'Online web order', true, true, NOW(), NOW()),
('ec000b00-0004-0000-0000-000000000002', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000004', 'In Store', 'In-store purchase', true, false, NOW(), NOW()),
('ec000b00-0004-0000-0000-000000000003', 'ec000000-0000-0000-0000-000000000001', 'ec000100-0000-0000-0000-000000000004', 'Phone', 'Phone order', true, false, NOW(), NOW());

-- ============================================================================
-- SECTION 9: PHYSICAL TABLES
-- ============================================================================

CREATE TABLE IF NOT EXISTS tbl_categories (
    id VARCHAR(36) PRIMARY KEY,
    name TEXT,
    display_name TEXT,
    description TEXT,
    category_type VARCHAR(255),
    gender VARCHAR(255),
    parent_category VARCHAR(36),
    image_url VARCHAR(2048),
    sort_order INTEGER,
    is_active BOOLEAN,
    slug TEXT,
    created_by VARCHAR(36),
    updated_by VARCHAR(36),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS tbl_products (
    id VARCHAR(36) PRIMARY KEY,
    sku VARCHAR(100),
    name TEXT,
    description TEXT,
    category VARCHAR(36),
    status VARCHAR(255),
    base_price NUMERIC(18,2),
    base_price_currency_code VARCHAR(3),
    cost NUMERIC(18,2),
    cost_currency_code VARCHAR(3),
    compare_at_price NUMERIC(18,2),
    compare_at_price_currency_code VARCHAR(3),
    gender VARCHAR(255),
    available_sizes TEXT[],
    available_colors TEXT[],
    weight_grams INTEGER,
    material TEXT,
    care_instructions TEXT,
    primary_image_url VARCHAR(2048),
    is_featured BOOLEAN,
    tags JSONB,
    created_by VARCHAR(36),
    updated_by VARCHAR(36),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS tbl_customers (
    id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(320),
    first_name TEXT,
    last_name TEXT,
    phone VARCHAR(40),
    shipping_street TEXT,
    shipping_city TEXT,
    shipping_state TEXT,
    shipping_zip TEXT,
    shipping_country TEXT,
    billing_street TEXT,
    billing_city TEXT,
    billing_state TEXT,
    billing_zip TEXT,
    billing_country TEXT,
    total_orders INTEGER,
    total_spent NUMERIC(18,2),
    total_spent_currency_code VARCHAR(3),
    notes TEXT,
    accepts_marketing BOOLEAN,
    created_by VARCHAR(36),
    updated_by VARCHAR(36),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS tbl_orders (
    id VARCHAR(36) PRIMARY KEY,
    order_number VARCHAR(100),
    customer VARCHAR(36),
    status VARCHAR(255),
    order_date TIMESTAMP,
    subtotal NUMERIC(18,2),
    subtotal_currency_code VARCHAR(3),
    discount_amount NUMERIC(18,2),
    discount_amount_currency_code VARCHAR(3),
    shipping_cost NUMERIC(18,2),
    shipping_cost_currency_code VARCHAR(3),
    tax_amount NUMERIC(18,2),
    tax_amount_currency_code VARCHAR(3),
    total_amount NUMERIC(18,2),
    total_amount_currency_code VARCHAR(3),
    shipping_method VARCHAR(255),
    shipping_street TEXT,
    shipping_city TEXT,
    shipping_state TEXT,
    shipping_zip TEXT,
    shipping_country TEXT,
    tracking_number TEXT,
    notes TEXT,
    discount_code VARCHAR(36),
    created_by VARCHAR(36),
    updated_by VARCHAR(36),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS tbl_order_items (
    id VARCHAR(36) PRIMARY KEY,
    order_ref VARCHAR(36) NOT NULL,
    product VARCHAR(36),
    product_name TEXT,
    sku TEXT,
    size VARCHAR(255),
    color VARCHAR(255),
    quantity INTEGER,
    unit_price NUMERIC(18,2),
    unit_price_currency_code VARCHAR(3),
    line_total NUMERIC(18,2),
    line_total_currency_code VARCHAR(3),
    discount_amount NUMERIC(18,2),
    discount_amount_currency_code VARCHAR(3),
    created_by VARCHAR(36),
    updated_by VARCHAR(36),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS tbl_payments (
    id VARCHAR(36) PRIMARY KEY,
    order_ref VARCHAR(36),
    payment_method VARCHAR(255),
    status VARCHAR(255),
    amount NUMERIC(18,2),
    amount_currency_code VARCHAR(3),
    transaction_id VARCHAR(255),
    payment_date TIMESTAMP,
    gateway_response JSONB,
    refund_amount NUMERIC(18,2),
    refund_amount_currency_code VARCHAR(3),
    refund_reason TEXT,
    created_by VARCHAR(36),
    updated_by VARCHAR(36),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS tbl_inventory (
    id VARCHAR(36) PRIMARY KEY,
    product VARCHAR(36),
    size VARCHAR(255),
    color VARCHAR(255),
    quantity_on_hand INTEGER,
    quantity_reserved INTEGER,
    quantity_available INTEGER,
    reorder_point INTEGER,
    reorder_quantity INTEGER,
    warehouse_location TEXT,
    last_restock_date DATE,
    created_by VARCHAR(36),
    updated_by VARCHAR(36),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS tbl_promotions (
    id VARCHAR(36) PRIMARY KEY,
    name TEXT,
    description TEXT,
    discount_type VARCHAR(255),
    discount_value DOUBLE PRECISION,
    start_date TIMESTAMP,
    end_date TIMESTAMP,
    minimum_purchase NUMERIC(18,2),
    minimum_purchase_currency_code VARCHAR(3),
    max_uses INTEGER,
    current_uses INTEGER,
    is_active BOOLEAN,
    applies_to_category VARCHAR(36),
    banner_image_url VARCHAR(2048),
    created_by VARCHAR(36),
    updated_by VARCHAR(36),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS tbl_discount_codes (
    id VARCHAR(36) PRIMARY KEY,
    code TEXT,
    promotion VARCHAR(36),
    discount_type VARCHAR(255),
    discount_value DOUBLE PRECISION,
    max_uses INTEGER,
    times_used INTEGER,
    min_order_amount NUMERIC(18,2),
    min_order_amount_currency_code VARCHAR(3),
    start_date TIMESTAMP,
    end_date TIMESTAMP,
    is_active BOOLEAN,
    created_by VARCHAR(36),
    updated_by VARCHAR(36),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================================================================
-- SECTION 10: SAMPLE DATA
-- ============================================================================

-- Categories
INSERT INTO tbl_categories (id, name, display_name, description, category_type, gender, sort_order, is_active, slug, created_by, created_at, updated_at) VALUES
('ec00d001-0000-0000-0000-000000000001', 'T-Shirts', 'T-Shirts', 'Casual and graphic tees', 'apparel', 'unisex', 0, true, 't-shirts', 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d001-0000-0000-0000-000000000002', 'Jeans', 'Jeans', 'Denim jeans and pants', 'apparel', 'unisex', 1, true, 'jeans', 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d001-0000-0000-0000-000000000003', 'Dresses', 'Dresses', 'Casual and formal dresses', 'apparel', 'women', 2, true, 'dresses', 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d001-0000-0000-0000-000000000004', 'Jackets', 'Jackets', 'Outerwear and jackets', 'outerwear', 'unisex', 3, true, 'jackets', 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d001-0000-0000-0000-000000000005', 'Sneakers', 'Sneakers', 'Casual and athletic sneakers', 'footwear', 'unisex', 4, true, 'sneakers', 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d001-0000-0000-0000-000000000006', 'Hats', 'Hats', 'Caps, beanies, and hats', 'accessories', 'unisex', 5, true, 'hats', 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d001-0000-0000-0000-000000000007', 'Activewear Tops', 'Activewear Tops', 'Athletic and workout tops', 'activewear', 'unisex', 6, true, 'activewear-tops', 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d001-0000-0000-0000-000000000008', 'Handbags', 'Handbags', 'Totes, crossbody, and clutches', 'accessories', 'women', 7, true, 'handbags', 'ec000000-0000-0000-0000-000000000020', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Products
INSERT INTO tbl_products (id, sku, name, description, category, status, base_price, base_price_currency_code, cost, cost_currency_code, gender, available_sizes, available_colors, weight_grams, material, care_instructions, is_featured, created_by, created_at, updated_at) VALUES
('ec00d002-0000-0000-0000-000000000001', 'TL-001000', 'Classic Crew Tee', 'Soft cotton crew neck tee', 'ec00d001-0000-0000-0000-000000000001', 'active', 29.99, 'USD', 8.50, 'USD', 'unisex', ARRAY['s','m','l','xl'], ARRAY['black','white','navy'], 180, '100% Cotton', 'Machine wash cold', true, 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d002-0000-0000-0000-000000000002', 'TL-001001', 'Slim Fit Denim', 'Modern slim fit jeans', 'ec00d001-0000-0000-0000-000000000002', 'active', 79.99, 'USD', 22.00, 'USD', 'men', ARRAY['s','m','l','xl','xxl'], ARRAY['blue','black'], 450, '98% Cotton, 2% Elastane', 'Machine wash cold, tumble dry low', true, 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d002-0000-0000-0000-000000000003', 'TL-001002', 'Floral Maxi Dress', 'Elegant floral print maxi dress', 'ec00d001-0000-0000-0000-000000000003', 'active', 89.99, 'USD', 25.00, 'USD', 'women', ARRAY['xs','s','m','l'], ARRAY['pink','navy','white'], 320, '100% Rayon', 'Hand wash cold', false, 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d002-0000-0000-0000-000000000004', 'TL-001003', 'Bomber Jacket', 'Classic bomber jacket', 'ec00d001-0000-0000-0000-000000000004', 'active', 129.99, 'USD', 38.00, 'USD', 'men', ARRAY['s','m','l','xl'], ARRAY['black','navy','green'], 680, 'Nylon shell, polyester lining', 'Dry clean only', true, 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d002-0000-0000-0000-000000000005', 'TL-001004', 'Canvas Sneakers', 'Classic low-top canvas sneakers', 'ec00d001-0000-0000-0000-000000000005', 'active', 59.99, 'USD', 15.00, 'USD', 'unisex', ARRAY['s','m','l','xl'], ARRAY['white','black','red'], 350, 'Canvas upper, rubber sole', 'Spot clean', false, 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d002-0000-0000-0000-000000000006', 'TL-001005', 'Wool Beanie', 'Warm knit wool beanie', 'ec00d001-0000-0000-0000-000000000006', 'active', 24.99, 'USD', 6.00, 'USD', 'unisex', ARRAY['one_size'], ARRAY['black','grey','navy','red'], 85, '100% Merino Wool', 'Hand wash cold', false, 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d002-0000-0000-0000-000000000007', 'TL-001006', 'Performance Tank', 'Moisture-wicking athletic tank top', 'ec00d001-0000-0000-0000-000000000007', 'active', 34.99, 'USD', 9.00, 'USD', 'women', ARRAY['xs','s','m','l'], ARRAY['black','pink','white'], 120, '92% Polyester, 8% Spandex', 'Machine wash cold', false, 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d002-0000-0000-0000-000000000008', 'TL-001007', 'Leather Tote Bag', 'Full-grain leather tote bag', 'ec00d001-0000-0000-0000-000000000008', 'active', 149.99, 'USD', 42.00, 'USD', 'women', ARRAY['one_size'], ARRAY['brown','black','beige'], 680, 'Full-grain leather', 'Wipe with damp cloth', true, 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d002-0000-0000-0000-000000000009', 'TL-001008', 'Vintage Wash Tee', 'Relaxed fit vintage wash tee', 'ec00d001-0000-0000-0000-000000000001', 'active', 34.99, 'USD', 10.00, 'USD', 'unisex', ARRAY['s','m','l','xl'], ARRAY['grey','beige','blue'], 190, '100% Cotton', 'Machine wash cold', false, 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d002-0000-0000-0000-000000000010', 'TL-001009', 'High Rise Skinny Jean', 'High waisted skinny jeans', 'ec00d001-0000-0000-0000-000000000002', 'draft', 89.99, 'USD', 24.00, 'USD', 'women', ARRAY['xs','s','m','l'], ARRAY['black','blue'], 420, '95% Cotton, 5% Elastane', 'Machine wash cold', false, 'ec000000-0000-0000-0000-000000000020', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Customers
INSERT INTO tbl_customers (id, email, first_name, last_name, phone, shipping_street, shipping_city, shipping_state, shipping_zip, shipping_country, billing_street, billing_city, billing_state, billing_zip, billing_country, total_orders, total_spent, total_spent_currency_code, accepts_marketing, created_by, created_at, updated_at) VALUES
('ec00d003-0000-0000-0000-000000000001', 'sarah.johnson@example.com', 'Sarah', 'Johnson', '555-0101', '123 Oak Street', 'Portland', 'OR', '97201', 'US', '123 Oak Street', 'Portland', 'OR', '97201', 'US', 3, 299.97, 'USD', true, 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d003-0000-0000-0000-000000000002', 'michael.chen@example.com', 'Michael', 'Chen', '555-0102', '456 Elm Avenue', 'San Francisco', 'CA', '94102', 'US', '456 Elm Avenue', 'San Francisco', 'CA', '94102', 'US', 2, 209.98, 'USD', true, 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d003-0000-0000-0000-000000000003', 'emily.r@example.com', 'Emily', 'Rodriguez', '555-0103', '789 Pine Road', 'Austin', 'TX', '73301', 'US', '789 Pine Road', 'Austin', 'TX', '73301', 'US', 1, 89.99, 'USD', false, 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d003-0000-0000-0000-000000000004', 'david.kim@example.com', 'David', 'Kim', '555-0104', '321 Maple Drive', 'Seattle', 'WA', '98101', 'US', '321 Maple Drive', 'Seattle', 'WA', '98101', 'US', 1, 129.99, 'USD', true, 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d003-0000-0000-0000-000000000005', 'jessica.t@example.com', 'Jessica', 'Taylor', '555-0105', '654 Cedar Lane', 'Denver', 'CO', '80201', 'US', '654 Cedar Lane', 'Denver', 'CO', '80201', 'US', 1, 59.99, 'USD', true, 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d003-0000-0000-0000-000000000006', 'robert.w@example.com', 'Robert', 'Williams', '555-0106', '987 Birch Way', 'Chicago', 'IL', '60601', 'US', '987 Birch Way', 'Chicago', 'IL', '60601', 'US', 0, 0.00, 'USD', false, 'ec000000-0000-0000-0000-000000000020', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Promotions (before orders, since orders might reference discount codes)
INSERT INTO tbl_promotions (id, name, description, discount_type, discount_value, start_date, end_date, minimum_purchase, minimum_purchase_currency_code, max_uses, current_uses, is_active, created_by, created_at, updated_at) VALUES
('ec00d008-0000-0000-0000-000000000001', 'Summer Sale', 'Summer clearance - 20% off everything', 'percentage', 20.0, '2025-06-01 00:00:00', '2025-09-01 00:00:00', 25.00, 'USD', 1000, 156, true, 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d008-0000-0000-0000-000000000002', 'New Customer Welcome', 'Welcome discount for new customers', 'fixed_amount', 10.0, '2025-01-01 00:00:00', '2025-12-31 00:00:00', 50.00, 'USD', 5000, 423, true, 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d008-0000-0000-0000-000000000003', 'Free Shipping Weekend', 'Free shipping on all orders', 'free_shipping', 0.0, '2025-08-01 00:00:00', '2025-08-03 00:00:00', 0.00, 'USD', NULL, 0, true, 'ec000000-0000-0000-0000-000000000020', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Discount Codes
INSERT INTO tbl_discount_codes (id, code, promotion, discount_type, discount_value, max_uses, times_used, min_order_amount, min_order_amount_currency_code, start_date, end_date, is_active, created_by, created_at, updated_at) VALUES
('ec00d009-0000-0000-0000-000000000001', 'SUMMER20', 'ec00d008-0000-0000-0000-000000000001', 'percentage', 20.0, 1000, 156, 25.00, 'USD', '2025-06-01 00:00:00', '2025-09-01 00:00:00', true, 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d009-0000-0000-0000-000000000002', 'WELCOME10', 'ec00d008-0000-0000-0000-000000000002', 'fixed_amount', 10.0, 5000, 423, 50.00, 'USD', '2025-01-01 00:00:00', '2025-12-31 00:00:00', true, 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d009-0000-0000-0000-000000000003', 'FREESHIP', 'ec00d008-0000-0000-0000-000000000003', 'free_shipping', 0.0, NULL, 0, 0.00, 'USD', '2025-08-01 00:00:00', '2025-08-03 00:00:00', true, 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d009-0000-0000-0000-000000000004', 'VIP30', 'ec00d008-0000-0000-0000-000000000001', 'percentage', 30.0, 50, 12, 100.00, 'USD', '2025-06-01 00:00:00', '2025-12-31 00:00:00', true, 'ec000000-0000-0000-0000-000000000020', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Orders
INSERT INTO tbl_orders (id, order_number, customer, status, order_date, subtotal, subtotal_currency_code, discount_amount, discount_amount_currency_code, shipping_cost, shipping_cost_currency_code, tax_amount, tax_amount_currency_code, total_amount, total_amount_currency_code, shipping_method, shipping_street, shipping_city, shipping_state, shipping_zip, shipping_country, tracking_number, discount_code, created_by, created_at, updated_at) VALUES
('ec00d004-0000-0000-0000-000000000001', 'ORD-100001', 'ec00d003-0000-0000-0000-000000000001', 'delivered', '2025-06-15 10:30:00', 109.98, 'USD', 0.00, 'USD', 7.99, 'USD', 9.44, 'USD', 127.41, 'USD', 'standard', '123 Oak Street', 'Portland', 'OR', '97201', 'US', '1Z999AA10123456784', NULL, 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d004-0000-0000-0000-000000000002', 'ORD-100002', 'ec00d003-0000-0000-0000-000000000001', 'shipped', '2025-07-01 14:15:00', 89.99, 'USD', 0.00, 'USD', 7.99, 'USD', 7.84, 'USD', 105.82, 'USD', 'express', '123 Oak Street', 'Portland', 'OR', '97201', 'US', '1Z999AA10123456785', NULL, 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d004-0000-0000-0000-000000000003', 'ORD-100003', 'ec00d003-0000-0000-0000-000000000002', 'processing', '2025-07-10 09:00:00', 159.98, 'USD', 10.00, 'USD', 0.00, 'USD', 12.00, 'USD', 161.98, 'USD', 'standard', '456 Elm Avenue', 'San Francisco', 'CA', '94102', 'US', NULL, 'ec00d009-0000-0000-0000-000000000002', 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d004-0000-0000-0000-000000000004', 'ORD-100004', 'ec00d003-0000-0000-0000-000000000003', 'pending', '2025-07-12 16:45:00', 89.99, 'USD', 0.00, 'USD', 7.99, 'USD', 7.84, 'USD', 105.82, 'USD', 'standard', '789 Pine Road', 'Austin', 'TX', '73301', 'US', NULL, NULL, 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d004-0000-0000-0000-000000000005', 'ORD-100005', 'ec00d003-0000-0000-0000-000000000004', 'confirmed', '2025-07-13 11:20:00', 129.99, 'USD', 0.00, 'USD', 12.99, 'USD', 11.44, 'USD', 154.42, 'USD', 'next_day', '321 Maple Drive', 'Seattle', 'WA', '98101', 'US', NULL, NULL, 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d004-0000-0000-0000-000000000006', 'ORD-100006', 'ec00d003-0000-0000-0000-000000000005', 'cancelled', '2025-07-14 08:00:00', 59.99, 'USD', 0.00, 'USD', 7.99, 'USD', 5.44, 'USD', 73.42, 'USD', 'standard', '654 Cedar Lane', 'Denver', 'CO', '80201', 'US', NULL, NULL, 'ec000000-0000-0000-0000-000000000020', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Order Items (2 per order)
INSERT INTO tbl_order_items (id, order_ref, product, product_name, sku, size, color, quantity, unit_price, unit_price_currency_code, line_total, line_total_currency_code, created_by, created_at, updated_at) VALUES
('ec00d005-0000-0000-0000-000000000001', 'ec00d004-0000-0000-0000-000000000001', 'ec00d002-0000-0000-0000-000000000001', 'Classic Crew Tee', 'TL-001000', 'm', 'black', 2, 29.99, 'USD', 59.98, 'USD', 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d005-0000-0000-0000-000000000002', 'ec00d004-0000-0000-0000-000000000001', 'ec00d002-0000-0000-0000-000000000006', 'Wool Beanie', 'TL-001005', 'one_size', 'navy', 2, 24.99, 'USD', 49.98, 'USD', 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d005-0000-0000-0000-000000000003', 'ec00d004-0000-0000-0000-000000000002', 'ec00d002-0000-0000-0000-000000000003', 'Floral Maxi Dress', 'TL-001002', 's', 'pink', 1, 89.99, 'USD', 89.99, 'USD', 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d005-0000-0000-0000-000000000004', 'ec00d004-0000-0000-0000-000000000003', 'ec00d002-0000-0000-0000-000000000002', 'Slim Fit Denim', 'TL-001001', 'l', 'blue', 1, 79.99, 'USD', 79.99, 'USD', 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d005-0000-0000-0000-000000000005', 'ec00d004-0000-0000-0000-000000000003', 'ec00d002-0000-0000-0000-000000000002', 'Slim Fit Denim', 'TL-001001', 'm', 'black', 1, 79.99, 'USD', 79.99, 'USD', 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d005-0000-0000-0000-000000000006', 'ec00d004-0000-0000-0000-000000000004', 'ec00d002-0000-0000-0000-000000000003', 'Floral Maxi Dress', 'TL-001002', 'm', 'navy', 1, 89.99, 'USD', 89.99, 'USD', 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d005-0000-0000-0000-000000000007', 'ec00d004-0000-0000-0000-000000000005', 'ec00d002-0000-0000-0000-000000000004', 'Bomber Jacket', 'TL-001003', 'l', 'black', 1, 129.99, 'USD', 129.99, 'USD', 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d005-0000-0000-0000-000000000008', 'ec00d004-0000-0000-0000-000000000006', 'ec00d002-0000-0000-0000-000000000005', 'Canvas Sneakers', 'TL-001004', 'l', 'white', 1, 59.99, 'USD', 59.99, 'USD', 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d005-0000-0000-0000-000000000009', 'ec00d004-0000-0000-0000-000000000001', 'ec00d002-0000-0000-0000-000000000009', 'Vintage Wash Tee', 'TL-001008', 'l', 'grey', 1, 0.02, 'USD', 0.02, 'USD', 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d005-0000-0000-0000-000000000010', 'ec00d004-0000-0000-0000-000000000002', 'ec00d002-0000-0000-0000-000000000007', 'Performance Tank', 'TL-001006', 's', 'black', 1, 0.00, 'USD', 0.00, 'USD', 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d005-0000-0000-0000-000000000011', 'ec00d004-0000-0000-0000-000000000004', 'ec00d002-0000-0000-0000-000000000001', 'Classic Crew Tee', 'TL-001000', 'l', 'white', 1, 0.00, 'USD', 0.00, 'USD', 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d005-0000-0000-0000-000000000012', 'ec00d004-0000-0000-0000-000000000005', 'ec00d002-0000-0000-0000-000000000009', 'Vintage Wash Tee', 'TL-001008', 'm', 'beige', 1, 0.00, 'USD', 0.00, 'USD', 'ec000000-0000-0000-0000-000000000020', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Payments (one per non-cancelled order)
INSERT INTO tbl_payments (id, order_ref, payment_method, status, amount, amount_currency_code, transaction_id, payment_date, created_by, created_at, updated_at) VALUES
('ec00d006-0000-0000-0000-000000000001', 'ec00d004-0000-0000-0000-000000000001', 'credit_card', 'captured', 127.41, 'USD', 'txn_abc123def456', '2025-06-15 10:31:00', 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d006-0000-0000-0000-000000000002', 'ec00d004-0000-0000-0000-000000000002', 'paypal', 'captured', 105.82, 'USD', 'txn_ghi789jkl012', '2025-07-01 14:16:00', 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d006-0000-0000-0000-000000000003', 'ec00d004-0000-0000-0000-000000000003', 'credit_card', 'authorized', 161.98, 'USD', 'txn_mno345pqr678', '2025-07-10 09:01:00', 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d006-0000-0000-0000-000000000004', 'ec00d004-0000-0000-0000-000000000004', 'debit_card', 'pending', 105.82, 'USD', NULL, '2025-07-12 16:46:00', 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d006-0000-0000-0000-000000000005', 'ec00d004-0000-0000-0000-000000000005', 'apple_pay', 'captured', 154.42, 'USD', 'txn_stu901vwx234', '2025-07-13 11:21:00', 'ec000000-0000-0000-0000-000000000020', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Inventory
INSERT INTO tbl_inventory (id, product, size, color, quantity_on_hand, quantity_reserved, quantity_available, reorder_point, reorder_quantity, warehouse_location, last_restock_date, created_by, created_at, updated_at) VALUES
('ec00d007-0000-0000-0000-000000000001', 'ec00d002-0000-0000-0000-000000000001', 'm', 'black', 150, 5, 145, 20, 100, 'A1-01', '2025-06-01', 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d007-0000-0000-0000-000000000002', 'ec00d002-0000-0000-0000-000000000001', 'l', 'white', 85, 2, 83, 20, 100, 'A1-02', '2025-06-01', 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d007-0000-0000-0000-000000000003', 'ec00d002-0000-0000-0000-000000000002', 'l', 'blue', 45, 3, 42, 15, 50, 'B2-01', '2025-05-15', 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d007-0000-0000-0000-000000000004', 'ec00d002-0000-0000-0000-000000000003', 's', 'pink', 30, 1, 29, 10, 40, 'C3-01', '2025-06-10', 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d007-0000-0000-0000-000000000005', 'ec00d002-0000-0000-0000-000000000004', 'l', 'black', 25, 1, 24, 10, 30, 'D4-01', '2025-06-20', 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d007-0000-0000-0000-000000000006', 'ec00d002-0000-0000-0000-000000000005', 'l', 'white', 8, 0, 8, 15, 50, 'E5-01', '2025-04-01', 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d007-0000-0000-0000-000000000007', 'ec00d002-0000-0000-0000-000000000006', 'one_size', 'navy', 200, 0, 200, 30, 100, 'F6-01', '2025-06-15', 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d007-0000-0000-0000-000000000008', 'ec00d002-0000-0000-0000-000000000007', 's', 'black', 60, 0, 60, 15, 50, 'G7-01', '2025-06-25', 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d007-0000-0000-0000-000000000009', 'ec00d002-0000-0000-0000-000000000008', 'one_size', 'brown', 18, 0, 18, 5, 20, 'H8-01', '2025-05-20', 'ec000000-0000-0000-0000-000000000020', NOW(), NOW()),
('ec00d007-0000-0000-0000-000000000010', 'ec00d002-0000-0000-0000-000000000009', 'm', 'grey', 3, 0, 3, 20, 100, 'A1-03', '2025-03-01', 'ec000000-0000-0000-0000-000000000020', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- END OF MIGRATION
-- ============================================================================
