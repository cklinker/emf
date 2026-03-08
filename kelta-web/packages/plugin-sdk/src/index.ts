/**
 * @kelta/plugin-sdk - Plugin development toolkit for Kelta
 *
 * This package provides interfaces and utilities for building plugins
 * that extend Kelta UIs with custom functionality.
 */

// Plugin interface
export { BasePlugin } from './plugin/BasePlugin';
export type { Plugin, PluginContext, Router } from './plugin/types';

// Component registry
export { ComponentRegistry } from './registry/ComponentRegistry';
export type {
  FieldRendererComponent,
  FieldRendererProps,
  PageComponent,
  PageComponentProps,
  RegisteredPageComponent,
} from './registry/types';
