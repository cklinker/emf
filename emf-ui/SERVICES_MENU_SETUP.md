# Services Menu Setup

The Services page has been created and is ready to use. To add it to the navigation menu, you need to update the bootstrap configuration returned by the control plane.

## Adding Services to the Menu

The EMF UI dynamically loads its menu from the `/ui/config/bootstrap` endpoint. To add the Services menu item, update the bootstrap configuration in your control plane to include:

```json
{
  "menus": [
    {
      "id": "main",
      "name": "Main Navigation",
      "items": [
        {
          "id": "dashboard",
          "label": "Dashboard",
          "path": "/",
          "icon": "dashboard"
        },
        {
          "id": "collections",
          "label": "Collections",
          "path": "/collections",
          "icon": "collections"
        },
        {
          "id": "services",
          "label": "Services",
          "path": "/services",
          "icon": "settings"
        },
        {
          "id": "authorization",
          "label": "Authorization",
          "icon": "security",
          "children": [
            {
              "id": "roles",
              "label": "Roles",
              "path": "/roles",
              "icon": "roles"
            },
            {
              "id": "policies",
              "label": "Policies",
              "path": "/policies",
              "icon": "policies"
            },
            {
              "id": "oidc-providers",
              "label": "OIDC Providers",
              "path": "/oidc-providers",
              "icon": "oidc"
            }
          ]
        }
      ]
    }
  ]
}
```

## What's Been Created

1. **ServicesPage Component** (`emf-ui/app/src/pages/ServicesPage/ServicesPage.tsx`)
   - Full CRUD interface for managing domain services
   - Modal form for creating and editing services
   - Table view with status indicators
   - Delete confirmation dialog

2. **Route Configuration** (`emf-ui/app/src/App.tsx`)
   - Route added: `/services`
   - Protected with authentication
   - Requires ADMIN role (enforced by backend)

3. **Translations** (`emf-ui/app/src/i18n/translations/en.json`)
   - All UI text for the Services page
   - Form labels, placeholders, hints
   - Validation messages
   - Success/error messages

4. **Styling** (`emf-ui/app/src/pages/ServicesPage/ServicesPage.module.css`)
   - Consistent with other pages
   - Responsive design
   - Accessible components

## API Integration

The page uses the existing backend API:

- **GET** `/control/services` - List all services
- **POST** `/control/services` - Create a new service
- **GET** `/control/services/{id}` - Get service details
- **PUT** `/control/services/{id}` - Update a service
- **DELETE** `/control/services/{id}` - Delete a service (soft delete)

All endpoints require the `ADMIN` role.

## Service Fields

The form includes all service fields:

- **Name** (required) - Unique identifier (lowercase, numbers, hyphens)
- **Display Name** - Human-readable name
- **Description** - Service description
- **Base Path** - API base path (default: `/api`)
- **Environment** - Deployment environment (e.g., production, staging)
- **Database URL** - Database connection string

## Testing

To test the Services page:

1. Ensure you're logged in with an ADMIN role
2. Navigate to `/services` in your browser
3. Try creating, editing, and deleting services
4. Verify form validation works correctly
5. Check that the API calls succeed

## Next Steps

To make the Services menu item visible:

1. Update your bootstrap configuration endpoint in the control plane
2. Add the services menu item to the appropriate menu
3. Restart the UI or refresh the page to load the new configuration
4. The Services link should now appear in the sidebar
