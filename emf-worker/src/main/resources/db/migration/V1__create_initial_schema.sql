-- EMF Control Plane Initial Schema
-- Flyway Migration V1: Create all tables for the control plane service
-- Requirements: 11.1-11.18

-- ============================================================================
-- COLLECTION MANAGEMENT TABLES
-- ============================================================================

-- Table: collection (Requirement 11.1)
-- Stores collection definitions - logical groupings of data entities
CREATE TABLE collection (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    current_version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE,
    
    -- Unique constraint on name for active collections
    CONSTRAINT uk_collection_name UNIQUE (name)
);

-- Indexes for collection table
CREATE INDEX idx_collection_active ON collection(active);
CREATE INDEX idx_collection_name ON collection(name);
CREATE INDEX idx_collection_created_at ON collection(created_at);

-- Table: collection_version (Requirement 11.3)
-- Stores immutable snapshots of collection schemas for versioning
CREATE TABLE collection_version (
    id VARCHAR(36) PRIMARY KEY,
    collection_id VARCHAR(36) NOT NULL,
    version INTEGER NOT NULL,
    schema JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Foreign key to collection
    CONSTRAINT fk_collection_version_collection 
        FOREIGN KEY (collection_id) REFERENCES collection(id) ON DELETE CASCADE,
    
    -- Unique constraint on collection_id + version
    CONSTRAINT uk_collection_version UNIQUE (collection_id, version)
);

-- Indexes for collection_version table
CREATE INDEX idx_collection_version_collection_id ON collection_version(collection_id);
CREATE INDEX idx_collection_version_version ON collection_version(version);

-- Table: field (Requirement 11.2)
-- Stores field definitions within collections
CREATE TABLE field (
    id VARCHAR(36) PRIMARY KEY,
    collection_id VARCHAR(36) NOT NULL,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(50) NOT NULL,
    required BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    constraints JSONB,
    description VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE,
    
    -- Foreign key to collection
    CONSTRAINT fk_field_collection 
        FOREIGN KEY (collection_id) REFERENCES collection(id) ON DELETE CASCADE,
    
    -- Unique constraint on collection_id + name for active fields
    CONSTRAINT uk_field_collection_name UNIQUE (collection_id, name)
);

-- Indexes for field table
CREATE INDEX idx_field_collection_id ON field(collection_id);
CREATE INDEX idx_field_active ON field(active);
CREATE INDEX idx_field_type ON field(type);
CREATE INDEX idx_field_name ON field(name);


-- Table: field_version (Requirement 11.4)
-- Stores snapshots of field definitions at specific collection versions
CREATE TABLE field_version (
    id VARCHAR(36) PRIMARY KEY,
    collection_version_id VARCHAR(36) NOT NULL,
    field_id VARCHAR(36) NOT NULL,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(50) NOT NULL,
    required BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    constraints JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Foreign key to collection_version
    CONSTRAINT fk_field_version_collection_version 
        FOREIGN KEY (collection_version_id) REFERENCES collection_version(id) ON DELETE CASCADE
);

-- Indexes for field_version table
CREATE INDEX idx_field_version_collection_version_id ON field_version(collection_version_id);
CREATE INDEX idx_field_version_field_id ON field_version(field_id);

-- ============================================================================
-- AUTHORIZATION TABLES
-- ============================================================================

-- Table: role (Requirement 11.5)
-- Stores roles for role-based access control
CREATE TABLE role (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Unique constraint on role name
    CONSTRAINT uk_role_name UNIQUE (name)
);

-- Indexes for role table
CREATE INDEX idx_role_name ON role(name);

-- Table: policy (Requirement 11.6)
-- Stores authorization policies with rules
CREATE TABLE policy (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    rules JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Unique constraint on policy name
    CONSTRAINT uk_policy_name UNIQUE (name)
);

-- Indexes for policy table
CREATE INDEX idx_policy_name ON policy(name);

-- Table: route_policy (Requirement 11.7)
-- Links collection operations to policies
CREATE TABLE route_policy (
    id VARCHAR(36) PRIMARY KEY,
    collection_id VARCHAR(36) NOT NULL,
    operation VARCHAR(50) NOT NULL,
    policy_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Foreign keys
    CONSTRAINT fk_route_policy_collection 
        FOREIGN KEY (collection_id) REFERENCES collection(id) ON DELETE CASCADE,
    CONSTRAINT fk_route_policy_policy 
        FOREIGN KEY (policy_id) REFERENCES policy(id) ON DELETE CASCADE,
    
    -- Unique constraint on collection + operation
    CONSTRAINT uk_route_policy_collection_operation UNIQUE (collection_id, operation)
);

-- Indexes for route_policy table
CREATE INDEX idx_route_policy_collection_id ON route_policy(collection_id);
CREATE INDEX idx_route_policy_policy_id ON route_policy(policy_id);
CREATE INDEX idx_route_policy_operation ON route_policy(operation);

-- Table: field_policy (Requirement 11.8)
-- Links field operations to policies
CREATE TABLE field_policy (
    id VARCHAR(36) PRIMARY KEY,
    field_id VARCHAR(36) NOT NULL,
    operation VARCHAR(50) NOT NULL,
    policy_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Foreign keys
    CONSTRAINT fk_field_policy_field 
        FOREIGN KEY (field_id) REFERENCES field(id) ON DELETE CASCADE,
    CONSTRAINT fk_field_policy_policy 
        FOREIGN KEY (policy_id) REFERENCES policy(id) ON DELETE CASCADE,
    
    -- Unique constraint on field + operation
    CONSTRAINT uk_field_policy_field_operation UNIQUE (field_id, operation)
);

-- Indexes for field_policy table
CREATE INDEX idx_field_policy_field_id ON field_policy(field_id);
CREATE INDEX idx_field_policy_policy_id ON field_policy(policy_id);
CREATE INDEX idx_field_policy_operation ON field_policy(operation);


-- ============================================================================
-- OIDC PROVIDER TABLE
-- ============================================================================

-- Table: oidc_provider (Requirement 11.14)
-- Stores OpenID Connect identity provider configurations
CREATE TABLE oidc_provider (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    issuer VARCHAR(500) NOT NULL,
    jwks_uri VARCHAR(500) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    client_id VARCHAR(200),
    audience VARCHAR(200),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE,
    
    -- Unique constraint on provider name
    CONSTRAINT uk_oidc_provider_name UNIQUE (name),
    
    -- Check constraint for valid issuer URL format
    CONSTRAINT chk_oidc_provider_issuer CHECK (issuer ~ '^https?://')
);

-- Indexes for oidc_provider table
CREATE INDEX idx_oidc_provider_name ON oidc_provider(name);
CREATE INDEX idx_oidc_provider_active ON oidc_provider(active);
CREATE INDEX idx_oidc_provider_issuer ON oidc_provider(issuer);

-- ============================================================================
-- UI CONFIGURATION TABLES
-- ============================================================================

-- Table: ui_page (Requirement 11.9)
-- Stores UI page configurations
CREATE TABLE ui_page (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    path VARCHAR(200) NOT NULL,
    title VARCHAR(200),
    config JSONB,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE,
    
    -- Unique constraint on page path
    CONSTRAINT uk_ui_page_path UNIQUE (path)
);

-- Indexes for ui_page table
CREATE INDEX idx_ui_page_name ON ui_page(name);
CREATE INDEX idx_ui_page_path ON ui_page(path);
CREATE INDEX idx_ui_page_active ON ui_page(active);

-- Table: ui_page_policy (Requirement 11.10)
-- Links UI pages to authorization policies
CREATE TABLE ui_page_policy (
    id VARCHAR(36) PRIMARY KEY,
    page_id VARCHAR(36) NOT NULL,
    policy_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Foreign keys
    CONSTRAINT fk_ui_page_policy_page 
        FOREIGN KEY (page_id) REFERENCES ui_page(id) ON DELETE CASCADE,
    CONSTRAINT fk_ui_page_policy_policy 
        FOREIGN KEY (policy_id) REFERENCES policy(id) ON DELETE CASCADE,
    
    -- Unique constraint on page + policy
    CONSTRAINT uk_ui_page_policy UNIQUE (page_id, policy_id)
);

-- Indexes for ui_page_policy table
CREATE INDEX idx_ui_page_policy_page_id ON ui_page_policy(page_id);
CREATE INDEX idx_ui_page_policy_policy_id ON ui_page_policy(policy_id);

-- Table: ui_menu (Requirement 11.11)
-- Stores UI menu configurations
CREATE TABLE ui_menu (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE,
    
    -- Unique constraint on menu name
    CONSTRAINT uk_ui_menu_name UNIQUE (name)
);

-- Indexes for ui_menu table
CREATE INDEX idx_ui_menu_name ON ui_menu(name);

-- Table: ui_menu_item (Requirement 11.12)
-- Stores menu items within UI menus
CREATE TABLE ui_menu_item (
    id VARCHAR(36) PRIMARY KEY,
    menu_id VARCHAR(36) NOT NULL,
    label VARCHAR(100) NOT NULL,
    path VARCHAR(200) NOT NULL,
    icon VARCHAR(100),
    display_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Foreign key to ui_menu
    CONSTRAINT fk_ui_menu_item_menu 
        FOREIGN KEY (menu_id) REFERENCES ui_menu(id) ON DELETE CASCADE,
    
    -- Check constraint for non-negative display order
    CONSTRAINT chk_ui_menu_item_display_order CHECK (display_order >= 0)
);

-- Indexes for ui_menu_item table
CREATE INDEX idx_ui_menu_item_menu_id ON ui_menu_item(menu_id);
CREATE INDEX idx_ui_menu_item_display_order ON ui_menu_item(display_order);
CREATE INDEX idx_ui_menu_item_active ON ui_menu_item(active);

-- Table: ui_menu_item_policy (Requirement 11.13)
-- Links UI menu items to authorization policies
CREATE TABLE ui_menu_item_policy (
    id VARCHAR(36) PRIMARY KEY,
    menu_item_id VARCHAR(36) NOT NULL,
    policy_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Foreign keys
    CONSTRAINT fk_ui_menu_item_policy_menu_item 
        FOREIGN KEY (menu_item_id) REFERENCES ui_menu_item(id) ON DELETE CASCADE,
    CONSTRAINT fk_ui_menu_item_policy_policy 
        FOREIGN KEY (policy_id) REFERENCES policy(id) ON DELETE CASCADE,
    
    -- Unique constraint on menu_item + policy
    CONSTRAINT uk_ui_menu_item_policy UNIQUE (menu_item_id, policy_id)
);

-- Indexes for ui_menu_item_policy table
CREATE INDEX idx_ui_menu_item_policy_menu_item_id ON ui_menu_item_policy(menu_item_id);
CREATE INDEX idx_ui_menu_item_policy_policy_id ON ui_menu_item_policy(policy_id);


-- ============================================================================
-- PACKAGE MANAGEMENT TABLES
-- ============================================================================

-- Table: package (Requirement 11.15)
-- Stores configuration packages for export/import
CREATE TABLE package (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    version VARCHAR(50) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Unique constraint on package name + version
    CONSTRAINT uk_package_name_version UNIQUE (name, version)
);

-- Indexes for package table
CREATE INDEX idx_package_name ON package(name);
CREATE INDEX idx_package_version ON package(version);
CREATE INDEX idx_package_created_at ON package(created_at);

-- Table: package_item (Requirement 11.16)
-- Stores items within configuration packages
CREATE TABLE package_item (
    id VARCHAR(36) PRIMARY KEY,
    package_id VARCHAR(36) NOT NULL,
    item_type VARCHAR(50) NOT NULL,
    item_id VARCHAR(36) NOT NULL,
    content JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Foreign key to package
    CONSTRAINT fk_package_item_package 
        FOREIGN KEY (package_id) REFERENCES package(id) ON DELETE CASCADE,
    
    -- Check constraint for valid item types
    CONSTRAINT chk_package_item_type CHECK (
        item_type IN ('COLLECTION', 'FIELD', 'ROLE', 'POLICY', 'ROUTE_POLICY', 
                      'FIELD_POLICY', 'OIDC_PROVIDER', 'UI_PAGE', 'UI_MENU', 'UI_MENU_ITEM')
    )
);

-- Indexes for package_item table
CREATE INDEX idx_package_item_package_id ON package_item(package_id);
CREATE INDEX idx_package_item_item_type ON package_item(item_type);
CREATE INDEX idx_package_item_item_id ON package_item(item_id);

-- ============================================================================
-- MIGRATION MANAGEMENT TABLES
-- ============================================================================

-- Table: migration_run (Requirement 11.17)
-- Stores migration execution records
CREATE TABLE migration_run (
    id VARCHAR(36) PRIMARY KEY,
    collection_id VARCHAR(36) NOT NULL,
    from_version INTEGER NOT NULL,
    to_version INTEGER NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    error_message VARCHAR(2000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE,
    
    -- Check constraint for valid status values
    CONSTRAINT chk_migration_run_status CHECK (
        status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'ROLLED_BACK')
    ),
    
    -- Check constraint for version ordering
    CONSTRAINT chk_migration_run_versions CHECK (from_version < to_version OR from_version > to_version)
);

-- Indexes for migration_run table
CREATE INDEX idx_migration_run_collection_id ON migration_run(collection_id);
CREATE INDEX idx_migration_run_status ON migration_run(status);
CREATE INDEX idx_migration_run_created_at ON migration_run(created_at);

-- Table: migration_step (Requirement 11.18)
-- Stores individual steps within migration runs
CREATE TABLE migration_step (
    id VARCHAR(36) PRIMARY KEY,
    migration_run_id VARCHAR(36) NOT NULL,
    step_number INTEGER NOT NULL,
    operation VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    details JSONB,
    error_message VARCHAR(2000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE,
    
    -- Foreign key to migration_run
    CONSTRAINT fk_migration_step_migration_run 
        FOREIGN KEY (migration_run_id) REFERENCES migration_run(id) ON DELETE CASCADE,
    
    -- Unique constraint on migration_run + step_number
    CONSTRAINT uk_migration_step_run_number UNIQUE (migration_run_id, step_number),
    
    -- Check constraint for valid status values
    CONSTRAINT chk_migration_step_status CHECK (
        status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'SKIPPED')
    ),
    
    -- Check constraint for positive step number
    CONSTRAINT chk_migration_step_number CHECK (step_number > 0)
);

-- Indexes for migration_step table
CREATE INDEX idx_migration_step_migration_run_id ON migration_step(migration_run_id);
CREATE INDEX idx_migration_step_status ON migration_step(status);
CREATE INDEX idx_migration_step_step_number ON migration_step(step_number);

-- ============================================================================
-- ADDITIONAL INDEXES FOR COMMON QUERY PATTERNS
-- ============================================================================

-- Composite indexes for common join patterns
CREATE INDEX idx_field_collection_active ON field(collection_id, active);
CREATE INDEX idx_route_policy_collection_operation ON route_policy(collection_id, operation);
CREATE INDEX idx_field_policy_field_operation ON field_policy(field_id, operation);
CREATE INDEX idx_ui_menu_item_menu_order ON ui_menu_item(menu_id, display_order);

-- GIN indexes for JSONB columns to support JSON queries
CREATE INDEX idx_field_constraints_gin ON field USING GIN (constraints jsonb_path_ops);
CREATE INDEX idx_policy_rules_gin ON policy USING GIN (rules jsonb_path_ops);
CREATE INDEX idx_ui_page_config_gin ON ui_page USING GIN (config jsonb_path_ops);
CREATE INDEX idx_collection_version_schema_gin ON collection_version USING GIN (schema jsonb_path_ops);
CREATE INDEX idx_package_item_content_gin ON package_item USING GIN (content jsonb_path_ops);
CREATE INDEX idx_migration_step_details_gin ON migration_step USING GIN (details jsonb_path_ops);

-- ============================================================================
-- COMMENTS FOR DOCUMENTATION
-- ============================================================================

COMMENT ON TABLE collection IS 'Stores collection definitions - logical groupings of data entities with defined fields and operations';
COMMENT ON TABLE collection_version IS 'Stores immutable snapshots of collection schemas for versioning and historical tracking';
COMMENT ON TABLE field IS 'Stores field definitions within collections - typed attributes with optional constraints';
COMMENT ON TABLE field_version IS 'Stores snapshots of field definitions at specific collection versions';
COMMENT ON TABLE role IS 'Stores roles for role-based access control';
COMMENT ON TABLE policy IS 'Stores authorization policies with rules defining access permissions';
COMMENT ON TABLE route_policy IS 'Links collection operations (CRUD) to authorization policies';
COMMENT ON TABLE field_policy IS 'Links field operations to authorization policies for field-level security';
COMMENT ON TABLE oidc_provider IS 'Stores OpenID Connect identity provider configurations for JWT validation';
COMMENT ON TABLE ui_page IS 'Stores UI page configurations for the admin interface';
COMMENT ON TABLE ui_page_policy IS 'Links UI pages to authorization policies for page-level access control';
COMMENT ON TABLE ui_menu IS 'Stores UI menu configurations for navigation structure';
COMMENT ON TABLE ui_menu_item IS 'Stores menu items within UI menus defining navigation links';
COMMENT ON TABLE ui_menu_item_policy IS 'Links UI menu items to authorization policies for menu-level access control';
COMMENT ON TABLE package IS 'Stores configuration packages for export/import and environment promotion';
COMMENT ON TABLE package_item IS 'Stores individual items within configuration packages';
COMMENT ON TABLE migration_run IS 'Stores migration execution records tracking schema changes';
COMMENT ON TABLE migration_step IS 'Stores individual steps within migration runs with status tracking';
