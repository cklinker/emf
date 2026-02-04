import type { Plugin, PluginContext } from './types';

/**
 * Base plugin class that provides common functionality
 */
export abstract class BasePlugin implements Plugin {
  /**
   * Plugin name (must be overridden)
   */
  abstract name: string;

  /**
   * Plugin version (must be overridden)
   */
  abstract version: string;

  /**
   * Plugin context (available after init)
   */
  protected context?: PluginContext;

  /**
   * Initialize the plugin with context
   */
  init(context: PluginContext): void | Promise<void> {
    this.context = context;
  }

  /**
   * Mount the plugin UI (must be overridden)
   */
  abstract mount(container: HTMLElement): void | Promise<void>;

  /**
   * Unmount the plugin (must be overridden)
   */
  abstract unmount(): void | Promise<void>;

  /**
   * Get the EMF client (throws if not initialized)
   */
  protected getClient() {
    if (!this.context) {
      throw new Error('Plugin not initialized');
    }
    return this.context.client;
  }

  /**
   * Get the current user (throws if not initialized)
   */
  protected getUser() {
    if (!this.context) {
      throw new Error('Plugin not initialized');
    }
    return this.context.user;
  }

  /**
   * Get the router (throws if not initialized)
   */
  protected getRouter() {
    if (!this.context) {
      throw new Error('Plugin not initialized');
    }
    return this.context.router;
  }
}
