/**
 * Sidebar Component
 * 
 * Navigation sidebar with dynamic menu rendering from bootstrap configuration.
 * Supports nested menu items, collapsed state, and mobile responsive behavior.
 * 
 * Requirements:
 * - 1.3: Configure navigation menus based on menu definitions
 * - 17.4: Collapse navigation menu into hamburger menu on mobile
 */

import { useState, useCallback, useMemo } from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import type { MenuConfig, MenuItemConfig } from '../../types/config';
import { useI18n } from '../../context/I18nContext';
import styles from './Sidebar.module.css';

/**
 * Props for the Sidebar component
 */
export interface SidebarProps {
  /** Menu configurations to render */
  menus: MenuConfig[];
  /** Whether the sidebar is collapsed (desktop/tablet) */
  collapsed: boolean;
  /** Callback when toggle button is clicked */
  onToggle: () => void;
  /** Callback when a menu item is clicked (useful for closing mobile menu) */
  onItemClick?: () => void;
}

/**
 * Props for individual menu items
 */
interface MenuItemProps {
  /** Menu item configuration */
  item: MenuItemConfig;
  /** Current nesting level (0 = top level) */
  level: number;
  /** Whether the sidebar is collapsed */
  collapsed: boolean;
  /** Callback when item is clicked */
  onItemClick?: () => void;
}

/**
 * Get icon for a menu item
 * Supports common icon names with emoji fallbacks
 */
function getIcon(iconName?: string): string {
  if (!iconName) return '';
  
  // Map common icon names to emoji/unicode characters
  const iconMap: Record<string, string> = {
    home: '🏠',
    dashboard: '📊',
    collections: '📁',
    collection: '📁',
    folder: '📁',
    users: '👥',
    user: '👤',
    settings: '⚙️',
    config: '⚙️',
    security: '🔒',
    roles: '🔒',
    policies: '📋',
    policy: '📋',
    oidc: '🔑',
    auth: '🔑',
    key: '🔑',
    builder: '🔧',
    pages: '📄',
    page: '📄',
    menus: '☰',
    menu: '☰',
    packages: '📦',
    package: '📦',
    migrations: '🔄',
    migration: '🔄',
    browser: '🔍',
    resources: '📚',
    resource: '📚',
    plugins: '🔌',
    plugin: '🔌',
    extension: '🔌',
    help: '❓',
    docs: '📖',
    logout: '🚪',
  };
  
  return iconMap[iconName.toLowerCase()] || iconName;
}

/**
 * MenuItem component renders a single menu item with optional children
 */
function MenuItem({ item, level, collapsed, onItemClick }: MenuItemProps): JSX.Element {
  const [isExpanded, setIsExpanded] = useState(false);
  const location = useLocation();
  const hasChildren = item.children && item.children.length > 0;
  
  // Check if this item or any of its children is active
  const isActive = useMemo(() => {
    if (item.path && location.pathname === item.path) {
      return true;
    }
    if (hasChildren) {
      return item.children!.some(child => 
        child.path && location.pathname === child.path
      );
    }
    return false;
  }, [item, location.pathname, hasChildren]);
  
  // Auto-expand if a child is active
  useMemo(() => {
    if (isActive && hasChildren) {
      setIsExpanded(true);
    }
  }, [isActive, hasChildren]);
  
  /**
   * Toggle expansion of nested items
   */
  const handleToggle = useCallback((e: React.MouseEvent) => {
    if (hasChildren) {
      e.preventDefault();
      setIsExpanded(prev => !prev);
    }
  }, [hasChildren]);
  
  /**
   * Handle item click
   */
  const handleClick = useCallback(() => {
    if (!hasChildren && onItemClick) {
      onItemClick();
    }
  }, [hasChildren, onItemClick]);
  
  const icon = getIcon(item.icon);
  const itemClasses = [
    styles.menuItem,
    styles[`menuItem--level${Math.min(level, 3)}`],
    isActive ? styles['menuItem--active'] : '',
    collapsed ? styles['menuItem--collapsed'] : '',
  ].filter(Boolean).join(' ');
  
  const contentClasses = [
    styles.menuItemContent,
    hasChildren ? styles['menuItemContent--hasChildren'] : '',
  ].filter(Boolean).join(' ');
  
  // Render as NavLink if it has a path and no children
  if (item.path && !hasChildren) {
    return (
      <li className={itemClasses} data-testid={`menu-item-${item.id}`}>
        <NavLink
          to={item.path}
          className={({ isActive: linkActive }) => 
            `${contentClasses} ${linkActive ? styles['menuItemContent--active'] : ''}`
          }
          onClick={handleClick}
          title={collapsed ? item.label : undefined}
          aria-current={isActive ? 'page' : undefined}
        >
          {icon && (
            <span className={styles.menuItemIcon} aria-hidden="true">
              {icon}
            </span>
          )}
          {!collapsed && (
            <span className={styles.menuItemLabel}>{item.label}</span>
          )}
        </NavLink>
      </li>
    );
  }
  
  // Render as expandable button if it has children
  if (hasChildren) {
    return (
      <li className={itemClasses} data-testid={`menu-item-${item.id}`}>
        <button
          type="button"
          className={contentClasses}
          onClick={handleToggle}
          aria-expanded={isExpanded}
          aria-controls={`submenu-${item.id}`}
          title={collapsed ? item.label : undefined}
        >
          {icon && (
            <span className={styles.menuItemIcon} aria-hidden="true">
              {icon}
            </span>
          )}
          {!collapsed && (
            <>
              <span className={styles.menuItemLabel}>{item.label}</span>
              <span 
                className={`${styles.expandIcon} ${isExpanded ? styles['expandIcon--expanded'] : ''}`}
                aria-hidden="true"
              >
                ▸
              </span>
            </>
          )}
        </button>
        
        {/* Nested menu items */}
        {!collapsed && (
          <ul
            id={`submenu-${item.id}`}
            className={`${styles.submenu} ${isExpanded ? styles['submenu--expanded'] : ''}`}
            role="group"
            aria-label={`${item.label} submenu`}
          >
            {item.children!.map(child => (
              <MenuItem
                key={child.id}
                item={child}
                level={level + 1}
                collapsed={collapsed}
                onItemClick={onItemClick}
              />
            ))}
          </ul>
        )}
      </li>
    );
  }
  
  // Render as non-interactive item (no path, no children)
  return (
    <li className={itemClasses} data-testid={`menu-item-${item.id}`}>
      <span className={contentClasses} title={collapsed ? item.label : undefined}>
        {icon && (
          <span className={styles.menuItemIcon} aria-hidden="true">
            {icon}
          </span>
        )}
        {!collapsed && (
          <span className={styles.menuItemLabel}>{item.label}</span>
        )}
      </span>
    </li>
  );
}

/**
 * Sidebar component provides navigation with dynamic menu rendering.
 * 
 * Features:
 * - Renders navigation menus from bootstrap configuration
 * - Supports nested menu items with expand/collapse
 * - Handles collapsed state for desktop/tablet
 * - Accessible with keyboard navigation and ARIA attributes
 * - Highlights active menu items based on current route
 * 
 * @example
 * ```tsx
 * <Sidebar
 *   menus={config.menus}
 *   collapsed={sidebarCollapsed}
 *   onToggle={toggleSidebar}
 *   onItemClick={closeMobileSidebar}
 * />
 * ```
 */
export function Sidebar({ 
  menus, 
  collapsed, 
  onToggle,
  onItemClick,
}: SidebarProps): JSX.Element {
  const { t } = useI18n();
  
  const sidebarClasses = [
    styles.sidebar,
    collapsed ? styles['sidebar--collapsed'] : '',
  ].filter(Boolean).join(' ');
  
  return (
    <nav 
      className={sidebarClasses}
      aria-label={t('navigation.main')}
      data-testid="sidebar"
    >
      {/* Menu sections */}
      {menus.map(menu => (
        <div 
          key={menu.id} 
          className={styles.menuSection}
          data-testid={`menu-section-${menu.id}`}
        >
          {/* Menu section header */}
          {!collapsed && menu.name && (
            <h2 className={styles.menuSectionTitle}>
              {menu.name}
            </h2>
          )}
          
          {/* Menu items */}
          <ul 
            className={styles.menuList}
            role="menubar"
            aria-label={menu.name || t('navigation.menu')}
          >
            {menu.items.map(item => (
              <MenuItem
                key={item.id}
                item={item}
                level={0}
                collapsed={collapsed}
                onItemClick={onItemClick}
              />
            ))}
          </ul>
        </div>
      ))}
      
      {/* Empty state */}
      {menus.length === 0 && (
        <div className={styles.emptyState} data-testid="sidebar-empty">
          {!collapsed && (
            <p className={styles.emptyStateText}>
              {t('navigation.noMenus')}
            </p>
          )}
        </div>
      )}
    </nav>
  );
}

export default Sidebar;
