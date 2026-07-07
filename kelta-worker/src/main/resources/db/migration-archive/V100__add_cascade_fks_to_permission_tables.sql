-- V100: Add foreign key constraints with CASCADE to permission tables
-- Fixes orphaned profile_object_permission and profile_field_permission records
-- when collections or fields are deleted.

-- Step 1: Clean up orphaned profile_object_permission records
-- (references collections that no longer exist)
DELETE FROM profile_object_permission
WHERE collection_id NOT IN (SELECT id FROM collection);

-- Step 2: Clean up orphaned profile_field_permission records
-- (references fields or collections that no longer exist)
DELETE FROM profile_field_permission
WHERE field_id NOT IN (SELECT id FROM field);

DELETE FROM profile_field_permission
WHERE collection_id NOT IN (SELECT id FROM collection);

-- Step 3: Add foreign key from profile_object_permission.collection_id → collection.id
ALTER TABLE profile_object_permission
    ADD CONSTRAINT fk_profile_obj_perm_collection
    FOREIGN KEY (collection_id) REFERENCES collection(id) ON DELETE CASCADE;

-- Step 4: Add foreign key from profile_field_permission.collection_id → collection.id
ALTER TABLE profile_field_permission
    ADD CONSTRAINT fk_profile_field_perm_collection
    FOREIGN KEY (collection_id) REFERENCES collection(id) ON DELETE CASCADE;

-- Step 5: Add foreign key from profile_field_permission.field_id → field.id
ALTER TABLE profile_field_permission
    ADD CONSTRAINT fk_profile_field_perm_field
    FOREIGN KEY (field_id) REFERENCES field(id) ON DELETE CASCADE;
