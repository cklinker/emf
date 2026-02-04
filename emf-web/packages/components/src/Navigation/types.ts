import type { ReactNode } from 'react';
import type { User } from '@emf/sdk';

/**
 * Menu item definition
 */
export interface MenuItem {
  /**
   * Unique identifier
   */
  id: string;

  /**
   * Display label
   */
  label: string;

  /**
   * Navigation path (optional)
   */
  path?: string;

  /**
   * Icon element (optional)
   */
  icon?: ReactNode;

  /**
   * Child menu items (optional)
   */
  children?: MenuItem[];

  /**
   * Required roles to view this item (optional)
   */
  roles?: string[];

  /**
   * Click handler (optional)
   */
  onClick?: () => void;
}

/**
 * Props for Navigation component
 */
export interface NavigationProps {
  /**
   * Menu items to display
   */
  items: MenuItem[];

  /**
   * Current user (for role-based filtering)
   */
  currentUser?: User | null;

  /**
   * Navigation orientation
   */
  orientation?: 'horizontal' | 'vertical';

  /**
   * Whether the navigation is collapsible
   */
  collapsible?: boolean;

  /**
   * Custom class name
   */
  className?: string;

  /**
   * Test ID for testing
   */
  testId?: string;
}
