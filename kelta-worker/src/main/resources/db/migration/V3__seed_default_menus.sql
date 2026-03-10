-- Seed default UI menus for the EMF Control Plane
-- This creates a main navigation menu with links to all the admin pages

-- Insert main menu
INSERT INTO ui_menu (id, name, description, created_at, updated_at)
VALUES 
    ('00000000-0000-0000-0000-000000000001', 'Main Navigation', 'Primary navigation menu for the control plane', NOW(), NOW());

-- Insert menu items
INSERT INTO ui_menu_item (id, menu_id, label, path, icon, display_order, active, created_at)
VALUES
    -- Dashboard
    ('10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'Dashboard', '/', 'dashboard', 0, true, NOW()),
    
    -- Collections
    ('10000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'Collections', '/collections', 'collections', 1, true, NOW()),
    
    -- Authorization
    ('10000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001', 'Roles', '/roles', 'security', 2, true, NOW()),
    ('10000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000001', 'Policies', '/policies', 'policy', 3, true, NOW()),
    
    -- OIDC Providers
    ('10000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000001', 'OIDC Providers', '/oidc-providers', 'key', 4, true, NOW()),
    
    -- UI Builder
    ('10000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000001', 'Pages', '/pages', 'pages', 5, true, NOW()),
    ('10000000-0000-0000-0000-000000000007', '00000000-0000-0000-0000-000000000001', 'Menus', '/menus', 'menu', 6, true, NOW()),
    
    -- Packages & Migrations
    ('10000000-0000-0000-0000-000000000008', '00000000-0000-0000-0000-000000000001', 'Packages', '/packages', 'package', 7, true, NOW()),
    ('10000000-0000-0000-0000-000000000009', '00000000-0000-0000-0000-000000000001', 'Migrations', '/migrations', 'migration', 8, true, NOW()),
    
    -- Resource Browser
    ('10000000-0000-0000-0000-000000000010', '00000000-0000-0000-0000-000000000001', 'Resources', '/resources', 'folder', 9, true, NOW()),
    
    -- Plugins
    ('10000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-000000000001', 'Plugins', '/plugins', 'extension', 10, true, NOW());
