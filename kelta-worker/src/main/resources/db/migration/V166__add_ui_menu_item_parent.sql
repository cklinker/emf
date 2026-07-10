-- Submenu support (apps/nav v2 follow-up): a menu item may nest under a parent
-- item of the same menu. A "group header" is an item with children and no path.
-- ON DELETE SET NULL: deleting a group floats its children to the top level
-- instead of silently removing them.

ALTER TABLE ui_menu_item
    ADD COLUMN parent_id character varying(36);

ALTER TABLE ui_menu_item
    ADD CONSTRAINT fk_ui_menu_item_parent
    FOREIGN KEY (parent_id) REFERENCES ui_menu_item (id) ON DELETE SET NULL;

CREATE INDEX idx_ui_menu_item_parent ON ui_menu_item (parent_id);

-- Group headers navigate nowhere themselves.
ALTER TABLE ui_menu_item
    ALTER COLUMN path DROP NOT NULL;
