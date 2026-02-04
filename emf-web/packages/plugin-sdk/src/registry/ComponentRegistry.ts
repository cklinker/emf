import type {
  FieldRendererComponent,
  PageComponent,
  RegisteredPageComponent,
} from './types';

/**
 * Registry for custom field renderers and page components
 */
export class ComponentRegistry {
  private static fieldRenderers = new Map<string, FieldRendererComponent>();
  private static pageComponents = new Map<string, RegisteredPageComponent>();

  /**
   * Register a custom field renderer for a field type
   */
  static registerFieldRenderer(fieldType: string, renderer: FieldRendererComponent): void {
    ComponentRegistry.fieldRenderers.set(fieldType, renderer);
  }

  /**
   * Get a field renderer for a field type
   */
  static getFieldRenderer(fieldType: string): FieldRendererComponent | undefined {
    return ComponentRegistry.fieldRenderers.get(fieldType);
  }

  /**
   * Check if a field renderer is registered for a type
   */
  static hasFieldRenderer(fieldType: string): boolean {
    return ComponentRegistry.fieldRenderers.has(fieldType);
  }

  /**
   * Get all registered field renderer types
   */
  static getFieldRendererTypes(): string[] {
    return Array.from(ComponentRegistry.fieldRenderers.keys());
  }

  /**
   * Register a custom page component with a route
   */
  static registerPageComponent(name: string, route: string, component: PageComponent): void {
    ComponentRegistry.pageComponents.set(name, { name, route, component });
  }

  /**
   * Get a page component by name
   */
  static getPageComponent(name: string): RegisteredPageComponent | undefined {
    return ComponentRegistry.pageComponents.get(name);
  }

  /**
   * Check if a page component is registered
   */
  static hasPageComponent(name: string): boolean {
    return ComponentRegistry.pageComponents.has(name);
  }

  /**
   * Get all registered page components
   */
  static getAllPageComponents(): RegisteredPageComponent[] {
    return Array.from(ComponentRegistry.pageComponents.values());
  }

  /**
   * Clear all registered field renderers
   */
  static clearFieldRenderers(): void {
    ComponentRegistry.fieldRenderers.clear();
  }

  /**
   * Clear all registered page components
   */
  static clearPageComponents(): void {
    ComponentRegistry.pageComponents.clear();
  }

  /**
   * Clear all registrations
   */
  static clearAll(): void {
    ComponentRegistry.clearFieldRenderers();
    ComponentRegistry.clearPageComponents();
  }
}
