import type { EMFClient, User } from '@emf/sdk';

/**
 * Router interface for plugin navigation
 */
export interface Router {
  navigate(path: string): void;
  getCurrentPath(): string;
}

/**
 * Context provided to plugins during initialization
 */
export interface PluginContext {
  /**
   * EMF client instance
   */
  client: EMFClient;

  /**
   * Current user (may be null if not authenticated)
   */
  user: User | null;

  /**
   * Router instance for navigation
   */
  router: Router;
}

/**
 * Plugin interface that all plugins must implement
 */
export interface Plugin {
  /**
   * Unique plugin name
   */
  name: string;

  /**
   * Plugin version
   */
  version: string;

  /**
   * Initialize the plugin with context
   */
  init(context: PluginContext): void | Promise<void>;

  /**
   * Mount the plugin UI to a container element
   */
  mount(container: HTMLElement): void | Promise<void>;

  /**
   * Unmount the plugin and clean up resources
   */
  unmount(): void | Promise<void>;
}
