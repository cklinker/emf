-- Apps (nav v2, app-platform slice 3): a ui_menu row is the "app" unit.
-- icon       — lucide icon name shown in the app switcher
-- is_default — the app selected when the user has no stored preference
-- active     — inactive apps are hidden from the end-user shell entirely
ALTER TABLE ui_menu
    ADD COLUMN icon VARCHAR(100),
    ADD COLUMN is_default BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT true;
