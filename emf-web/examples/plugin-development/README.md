# @emf/plugin-sdk Development Guide

This directory contains examples demonstrating how to create plugins using the `@emf/plugin-sdk` package.

## Installation

```bash
npm install @emf/plugin-sdk @emf/sdk react react-dom
```

## Plugin Basics

### Creating a Simple Plugin

```typescript
import { BasePlugin, PluginContext } from '@emf/plugin-sdk';

export class HelloWorldPlugin extends BasePlugin {
  name = 'hello-world';
  version = '1.0.0';

  async init(context: PluginContext): Promise<void> {
    // Call parent init to store context
    super.init(context);
    
    console.log('HelloWorld plugin initialized');
    console.log('User:', this.getUser()?.username ?? 'anonymous');
  }

  async mount(container: HTMLElement): Promise<void> {
    container.innerHTML = `
      <div class="hello-world-plugin">
        <h1>Hello, World!</h1>
        <p>Welcome to the EMF Plugin System</p>
      </div>
    `;
  }

  async unmount(): Promise<void> {
    console.log('HelloWorld plugin unmounted');
  }
}
```

### Using the Plugin Context

```typescript
import { BasePlugin, PluginContext } from '@emf/plugin-sdk';

export class DataPlugin extends BasePlugin {
  name = 'data-plugin';
  version = '1.0.0';

  private container?: HTMLElement;

  async mount(container: HTMLElement): Promise<void> {
    this.container = container;
    
    // Access the EMF client
    const client = this.getClient();
    
    // Fetch data
    const users = await client.resource('users').list({ size: 5 });
    
    // Render the data
    container.innerHTML = `
      <div class="data-plugin">
        <h2>Recent Users</h2>
        <ul>
          ${users.data.map(user => `<li>${user.name}</li>`).join('')}
        </ul>
      </div>
    `;
  }

  async unmount(): Promise<void> {
    if (this.container) {
      this.container.innerHTML = '';
    }
  }
}
```

### Plugin with Navigation

```typescript
import { BasePlugin } from '@emf/plugin-sdk';

export class NavigationPlugin extends BasePlugin {
  name = 'navigation-plugin';
  version = '1.0.0';

  async mount(container: HTMLElement): Promise<void> {
    const router = this.getRouter();
    
    container.innerHTML = `
      <nav class="plugin-nav">
        <button id="go-home">Home</button>
        <button id="go-users">Users</button>
        <button id="go-settings">Settings</button>
      </nav>
    `;

    // Add click handlers
    container.querySelector('#go-home')?.addEventListener('click', () => {
      router.navigate('/');
    });
    
    container.querySelector('#go-users')?.addEventListener('click', () => {
      router.navigate('/users');
    });
    
    container.querySelector('#go-settings')?.addEventListener('click', () => {
      router.navigate('/settings');
    });
  }

  async unmount(): Promise<void> {
    // Event listeners are automatically cleaned up when innerHTML is cleared
  }
}
```

## Custom Field Renderers

Register custom renderers for specific field types:

### Basic Field Renderer

```tsx
import React from 'react';
import { ComponentRegistry, FieldRendererProps } from '@emf/plugin-sdk';

// Custom renderer for 'rating' field type
function RatingRenderer({ value, field, onChange, readOnly }: FieldRendererProps) {
  const rating = Number(value) || 0;
  const maxRating = 5;

  if (readOnly) {
    return (
      <div className="rating-display">
        {Array.from({ length: maxRating }, (_, i) => (
          <span key={i} className={i < rating ? 'star filled' : 'star'}>
            ★
          </span>
        ))}
      </div>
    );
  }

  return (
    <div className="rating-input">
      {Array.from({ length: maxRating }, (_, i) => (
        <button
          key={i}
          type="button"
          className={i < rating ? 'star filled' : 'star'}
          onClick={() => onChange?.(i + 1)}
        >
          ★
        </button>
      ))}
    </div>
  );
}

// Register the renderer
ComponentRegistry.registerFieldRenderer('rating', RatingRenderer);
```

### Color Picker Field Renderer

```tsx
import React from 'react';
import { ComponentRegistry, FieldRendererProps } from '@emf/plugin-sdk';

function ColorRenderer({ value, field, onChange, readOnly }: FieldRendererProps) {
  const color = String(value) || '#000000';

  if (readOnly) {
    return (
      <div className="color-display">
        <span
          className="color-swatch"
          style={{ backgroundColor: color }}
        />
        <span className="color-value">{color}</span>
      </div>
    );
  }

  return (
    <div className="color-input">
      <input
        type="color"
        value={color}
        onChange={(e) => onChange?.(e.target.value)}
      />
      <input
        type="text"
        value={color}
        onChange={(e) => onChange?.(e.target.value)}
        pattern="^#[0-9A-Fa-f]{6}$"
      />
    </div>
  );
}

ComponentRegistry.registerFieldRenderer('color', ColorRenderer);
```

### Rich Text Field Renderer

```tsx
import React, { useState } from 'react';
import { ComponentRegistry, FieldRendererProps } from '@emf/plugin-sdk';

function RichTextRenderer({ value, field, onChange, readOnly }: FieldRendererProps) {
  const content = String(value) || '';

  if (readOnly) {
    return (
      <div
        className="rich-text-display"
        dangerouslySetInnerHTML={{ __html: content }}
      />
    );
  }

  return (
    <div className="rich-text-editor">
      <div className="toolbar">
        <button type="button" onClick={() => document.execCommand('bold')}>
          <strong>B</strong>
        </button>
        <button type="button" onClick={() => document.execCommand('italic')}>
          <em>I</em>
        </button>
        <button type="button" onClick={() => document.execCommand('underline')}>
          <u>U</u>
        </button>
      </div>
      <div
        className="editor"
        contentEditable
        dangerouslySetInnerHTML={{ __html: content }}
        onBlur={(e) => onChange?.(e.currentTarget.innerHTML)}
      />
    </div>
  );
}

ComponentRegistry.registerFieldRenderer('richtext', RichTextRenderer);
```

### JSON Field Renderer

```tsx
import React, { useState } from 'react';
import { ComponentRegistry, FieldRendererProps } from '@emf/plugin-sdk';

function JsonRenderer({ value, field, onChange, readOnly }: FieldRendererProps) {
  const [error, setError] = useState<string | null>(null);
  
  const jsonString = typeof value === 'string' 
    ? value 
    : JSON.stringify(value, null, 2);

  if (readOnly) {
    return (
      <pre className="json-display">
        <code>{jsonString}</code>
      </pre>
    );
  }

  const handleChange = (newValue: string) => {
    try {
      const parsed = JSON.parse(newValue);
      setError(null);
      onChange?.(parsed);
    } catch (e) {
      setError('Invalid JSON');
    }
  };

  return (
    <div className="json-editor">
      <textarea
        value={jsonString}
        onChange={(e) => handleChange(e.target.value)}
        rows={10}
        className={error ? 'error' : ''}
      />
      {error && <span className="error-message">{error}</span>}
    </div>
  );
}

ComponentRegistry.registerFieldRenderer('json', JsonRenderer);
```

## Custom Page Components

Register custom pages that integrate with the EMF router:

### Basic Custom Page

```tsx
import React from 'react';
import { ComponentRegistry, PageComponentProps } from '@emf/plugin-sdk';

function DashboardPage({ client, user, params }: PageComponentProps) {
  const [stats, setStats] = React.useState<any>(null);

  React.useEffect(() => {
    async function loadStats() {
      const users = await client.resource('users').list({ size: 1 });
      const products = await client.resource('products').list({ size: 1 });
      
      setStats({
        totalUsers: users.pagination.total,
        totalProducts: products.pagination.total,
      });
    }
    loadStats();
  }, [client]);

  return (
    <div className="dashboard-page">
      <h1>Dashboard</h1>
      <p>Welcome, {user?.username ?? 'Guest'}!</p>
      
      {stats && (
        <div className="stats-grid">
          <div className="stat-card">
            <h3>Users</h3>
            <p className="stat-value">{stats.totalUsers}</p>
          </div>
          <div className="stat-card">
            <h3>Products</h3>
            <p className="stat-value">{stats.totalProducts}</p>
          </div>
        </div>
      )}
    </div>
  );
}

// Register the page component
ComponentRegistry.registerPageComponent(
  'custom-dashboard',
  '/dashboard',
  DashboardPage
);
```

### Page with Route Parameters

```tsx
import React from 'react';
import { ComponentRegistry, PageComponentProps } from '@emf/plugin-sdk';

function UserProfilePage({ client, user, params }: PageComponentProps) {
  const [profile, setProfile] = React.useState<any>(null);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);

  const userId = params.id;

  React.useEffect(() => {
    async function loadProfile() {
      try {
        setLoading(true);
        const userData = await client.resource('users').get(userId);
        setProfile(userData);
      } catch (e) {
        setError('Failed to load user profile');
      } finally {
        setLoading(false);
      }
    }
    
    if (userId) {
      loadProfile();
    }
  }, [client, userId]);

  if (loading) return <div>Loading...</div>;
  if (error) return <div className="error">{error}</div>;
  if (!profile) return <div>User not found</div>;

  return (
    <div className="user-profile-page">
      <h1>{profile.name}</h1>
      <p>Email: {profile.email}</p>
      <p>Role: {profile.role}</p>
      {/* Add more profile fields */}
    </div>
  );
}

// Register with route parameter
ComponentRegistry.registerPageComponent(
  'user-profile',
  '/users/:id/profile',
  UserProfilePage
);
```

### Settings Page

```tsx
import React from 'react';
import { ComponentRegistry, PageComponentProps } from '@emf/plugin-sdk';

function SettingsPage({ client, user }: PageComponentProps) {
  const [settings, setSettings] = React.useState({
    theme: 'light',
    notifications: true,
    language: 'en',
  });

  const handleSave = async () => {
    // Save settings via API
    await client.resource('user-settings').update(user!.id, settings);
    alert('Settings saved!');
  };

  return (
    <div className="settings-page">
      <h1>Settings</h1>
      
      <div className="setting-group">
        <label>Theme</label>
        <select
          value={settings.theme}
          onChange={(e) => setSettings({ ...settings, theme: e.target.value })}
        >
          <option value="light">Light</option>
          <option value="dark">Dark</option>
          <option value="auto">Auto</option>
        </select>
      </div>

      <div className="setting-group">
        <label>
          <input
            type="checkbox"
            checked={settings.notifications}
            onChange={(e) => setSettings({ ...settings, notifications: e.target.checked })}
          />
          Enable Notifications
        </label>
      </div>

      <div className="setting-group">
        <label>Language</label>
        <select
          value={settings.language}
          onChange={(e) => setSettings({ ...settings, language: e.target.value })}
        >
          <option value="en">English</option>
          <option value="es">Spanish</option>
          <option value="fr">French</option>
        </select>
      </div>

      <button onClick={handleSave}>Save Settings</button>
    </div>
  );
}

ComponentRegistry.registerPageComponent(
  'settings',
  '/settings',
  SettingsPage
);
```

## Complete Plugin Example

Here's a complete plugin that registers both field renderers and page components:

```typescript
import React from 'react';
import { BasePlugin, ComponentRegistry, PluginContext } from '@emf/plugin-sdk';

// Field Renderers
function StatusBadgeRenderer({ value, readOnly }: FieldRendererProps) {
  const status = String(value);
  const colors: Record<string, string> = {
    active: 'green',
    pending: 'yellow',
    inactive: 'gray',
    error: 'red',
  };
  
  return (
    <span
      className="status-badge"
      style={{ backgroundColor: colors[status] || 'gray' }}
    >
      {status}
    </span>
  );
}

function AvatarRenderer({ value, readOnly }: FieldRendererProps) {
  const url = String(value);
  return (
    <img
      src={url || '/default-avatar.png'}
      alt="Avatar"
      className="avatar"
      style={{ width: 40, height: 40, borderRadius: '50%' }}
    />
  );
}

// Page Components
function AnalyticsPage({ client, user }: PageComponentProps) {
  return (
    <div className="analytics-page">
      <h1>Analytics Dashboard</h1>
      {/* Analytics content */}
    </div>
  );
}

function ReportsPage({ client, user, params }: PageComponentProps) {
  return (
    <div className="reports-page">
      <h1>Reports</h1>
      {/* Reports content */}
    </div>
  );
}

// Main Plugin Class
export class EnterprisePlugin extends BasePlugin {
  name = 'enterprise-plugin';
  version = '2.0.0';

  async init(context: PluginContext): Promise<void> {
    super.init(context);

    // Register field renderers
    ComponentRegistry.registerFieldRenderer('status', StatusBadgeRenderer);
    ComponentRegistry.registerFieldRenderer('avatar', AvatarRenderer);

    // Register page components
    ComponentRegistry.registerPageComponent('analytics', '/analytics', AnalyticsPage);
    ComponentRegistry.registerPageComponent('reports', '/reports', ReportsPage);

    console.log(`${this.name} v${this.version} initialized`);
  }

  async mount(container: HTMLElement): Promise<void> {
    // Plugin UI (if needed)
    container.innerHTML = `
      <div class="enterprise-plugin-widget">
        <h3>Enterprise Features</h3>
        <p>Analytics and reporting enabled</p>
      </div>
    `;
  }

  async unmount(): Promise<void> {
    // Cleanup (optional: unregister components)
    // Note: Usually you don't unregister as other plugins might depend on them
    console.log(`${this.name} unmounted`);
  }
}

// Export for use
export default EnterprisePlugin;
```

## Loading Plugins

```typescript
import { EMFClient } from '@emf/sdk';
import { EnterprisePlugin } from './plugins/enterprise-plugin';

// Create plugin context
const client = new EMFClient({ baseUrl: 'https://api.example.com' });
const context = {
  client,
  user: { id: '1', username: 'admin', roles: ['admin'] },
  router: {
    navigate: (path: string) => window.location.href = path,
    getCurrentPath: () => window.location.pathname,
  },
};

// Initialize and mount plugin
const plugin = new EnterprisePlugin();
await plugin.init(context);
await plugin.mount(document.getElementById('plugin-container')!);

// Later, when unmounting
await plugin.unmount();
```

## Best Practices

1. **Always call `super.init(context)`** in your init method to ensure the context is stored
2. **Clean up resources** in the unmount method (event listeners, timers, subscriptions)
3. **Handle errors gracefully** in field renderers and page components
4. **Use TypeScript** for better type safety and IDE support
5. **Test your plugins** in isolation before integrating with the main application
6. **Document your plugin's** field types and routes for other developers
7. **Version your plugins** semantically to communicate breaking changes
