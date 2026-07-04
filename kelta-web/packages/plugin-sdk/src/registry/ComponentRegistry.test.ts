import { describe, it, expect, beforeEach } from 'vitest';
import { createElement, type ReactElement } from 'react';
import { ComponentRegistry } from './ComponentRegistry';
import type { FieldRendererComponent, PageComponent } from './types';

const makeFieldRenderer = (): FieldRendererComponent => (): ReactElement => createElement('div');
const makePageComponent = (): PageComponent => (): ReactElement => createElement('div');

describe('ComponentRegistry', () => {
  beforeEach(() => {
    ComponentRegistry.clearAll();
  });

  describe('field renderers', () => {
    it('starts empty', () => {
      expect(ComponentRegistry.hasFieldRenderer('progress_bar')).toBe(false);
      expect(ComponentRegistry.getFieldRenderer('progress_bar')).toBeUndefined();
      expect(ComponentRegistry.getFieldRendererTypes()).toEqual([]);
    });

    it('registers and retrieves a field renderer', () => {
      const renderer = makeFieldRenderer();
      ComponentRegistry.registerFieldRenderer('progress_bar', renderer);
      expect(ComponentRegistry.hasFieldRenderer('progress_bar')).toBe(true);
      expect(ComponentRegistry.getFieldRenderer('progress_bar')).toBe(renderer);
    });

    it('overwrites an existing field renderer', () => {
      const first = makeFieldRenderer();
      const second = makeFieldRenderer();
      ComponentRegistry.registerFieldRenderer('progress_bar', first);
      ComponentRegistry.registerFieldRenderer('progress_bar', second);
      expect(ComponentRegistry.getFieldRenderer('progress_bar')).toBe(second);
    });

    it('lists registered field renderer types sorted alphabetically', () => {
      ComponentRegistry.registerFieldRenderer('zeta_field', makeFieldRenderer());
      ComponentRegistry.registerFieldRenderer('alpha_field', makeFieldRenderer());
      ComponentRegistry.registerFieldRenderer('mid_field', makeFieldRenderer());
      expect(ComponentRegistry.listFieldRenderers()).toEqual([
        'alpha_field',
        'mid_field',
        'zeta_field',
      ]);
    });

    it('clears field renderers', () => {
      ComponentRegistry.registerFieldRenderer('progress_bar', makeFieldRenderer());
      ComponentRegistry.clearFieldRenderers();
      expect(ComponentRegistry.listFieldRenderers()).toEqual([]);
    });
  });

  describe('page components', () => {
    it('starts empty', () => {
      expect(ComponentRegistry.hasPageComponent('dashboard')).toBe(false);
      expect(ComponentRegistry.getPageComponent('dashboard')).toBeUndefined();
      expect(ComponentRegistry.getAllPageComponents()).toEqual([]);
    });

    it('registers and retrieves a page component with its route', () => {
      const component = makePageComponent();
      ComponentRegistry.registerPageComponent('dashboard', '/dashboard', component);
      expect(ComponentRegistry.hasPageComponent('dashboard')).toBe(true);
      expect(ComponentRegistry.getPageComponent('dashboard')).toEqual({
        name: 'dashboard',
        route: '/dashboard',
        component,
      });
    });

    it('lists registered page component names sorted alphabetically', () => {
      ComponentRegistry.registerPageComponent('widgets', '/widgets', makePageComponent());
      ComponentRegistry.registerPageComponent('analytics', '/analytics', makePageComponent());
      expect(ComponentRegistry.listPageComponents()).toEqual(['analytics', 'widgets']);
    });

    it('clears page components', () => {
      ComponentRegistry.registerPageComponent('dashboard', '/dashboard', makePageComponent());
      ComponentRegistry.clearPageComponents();
      expect(ComponentRegistry.listPageComponents()).toEqual([]);
    });
  });

  describe('clearAll', () => {
    it('clears every registration type', () => {
      ComponentRegistry.registerFieldRenderer('progress_bar', makeFieldRenderer());
      ComponentRegistry.registerPageComponent('dashboard', '/dashboard', makePageComponent());
      ComponentRegistry.clearAll();
      expect(ComponentRegistry.listFieldRenderers()).toEqual([]);
      expect(ComponentRegistry.listPageComponents()).toEqual([]);
    });
  });
});
