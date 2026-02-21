package com.emf.controlplane.entity;

/**
 * Enumeration of all system-level permissions.
 * System permissions control access to platform features (setup, management, etc.)
 * as opposed to object-level CRUD or field-level visibility.
 */
public enum SystemPermission {

    VIEW_SETUP("Access setup/admin pages"),
    CUSTOMIZE_APPLICATION("Manage collections, fields, picklists, validation rules, record types, layouts"),
    MANAGE_USERS("Create, edit, and deactivate users, assign profiles"),
    MANAGE_GROUPS("Create, edit, and delete groups, manage membership"),
    MANAGE_SHARING("Configure sharing rules"),
    MANAGE_WORKFLOWS("Create and edit workflow rules, flows, scheduled jobs"),
    MANAGE_REPORTS("Create and edit reports and dashboards"),
    MANAGE_EMAIL_TEMPLATES("Create and edit email templates"),
    MANAGE_CONNECTED_APPS("Create and edit connected apps, webhooks, scripts"),
    MANAGE_DATA("Export data, bulk operations"),
    API_ACCESS("Use API directly"),
    VIEW_ALL_DATA("Read all records regardless of sharing"),
    MODIFY_ALL_DATA("Edit all records regardless of sharing"),
    MANAGE_APPROVALS("Configure approval processes"),
    MANAGE_LISTVIEWS("Create and edit list views");

    private final String description;

    SystemPermission(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
