import { describe, it, expect, beforeEach } from 'vitest'
import { componentRegistry } from './componentRegistry'

describe('ComponentRegistry', () => {
  beforeEach(() => {
    componentRegistry.clear()
  })

  describe('Field Renderers', () => {
    it('starts with no field renderers', () => {
      expect(componentRegistry.hasFieldRenderer('custom_type')).toBe(false)
      expect(componentRegistry.getFieldRenderer('custom_type')).toBeUndefined()
    })

    it('registers and retrieves a field renderer', () => {
      const MockComponent = () => null
      componentRegistry.registerFieldRenderer('custom_type', MockComponent)
      expect(componentRegistry.hasFieldRenderer('custom_type')).toBe(true)
      expect(componentRegistry.getFieldRenderer('custom_type')).toBe(MockComponent)
    })

    it('overwrites an existing field renderer', () => {
      const First = () => null
      const Second = () => null
      componentRegistry.registerFieldRenderer('custom_type', First)
      componentRegistry.registerFieldRenderer('custom_type', Second)
      expect(componentRegistry.getFieldRenderer('custom_type')).toBe(Second)
    })
  })

  describe('Page Components', () => {
    it('starts with no page components', () => {
      expect(componentRegistry.hasPageComponent('dashboard')).toBe(false)
      expect(componentRegistry.getPageComponent('dashboard')).toBeUndefined()
    })

    it('registers and retrieves a page component', () => {
      const MockPage = () => null
      componentRegistry.registerPageComponent('dashboard', MockPage)
      expect(componentRegistry.hasPageComponent('dashboard')).toBe(true)
      expect(componentRegistry.getPageComponent('dashboard')).toBe(MockPage)
    })
  })

  describe('Quick Actions', () => {
    it('starts with no quick actions', () => {
      expect(componentRegistry.hasQuickAction('approve')).toBe(false)
      expect(componentRegistry.getQuickAction('approve')).toBeUndefined()
    })

    it('registers and retrieves a quick action', () => {
      const MockAction = () => null
      componentRegistry.registerQuickAction('approve', MockAction)
      expect(componentRegistry.hasQuickAction('approve')).toBe(true)
      expect(componentRegistry.getQuickAction('approve')).toBe(MockAction)
    })
  })

  describe('Column Renderers', () => {
    it('starts with no column renderers', () => {
      expect(componentRegistry.hasColumnRenderer('progress')).toBe(false)
      expect(componentRegistry.getColumnRenderer('progress')).toBeUndefined()
    })

    it('registers and retrieves a column renderer', () => {
      const MockColumn = () => null
      componentRegistry.registerColumnRenderer('progress', MockColumn)
      expect(componentRegistry.hasColumnRenderer('progress')).toBe(true)
      expect(componentRegistry.getColumnRenderer('progress')).toBe(MockColumn)
    })
  })

  describe('Utilities', () => {
    it('returns stats with all counts', () => {
      const stats = componentRegistry.getStats()
      expect(stats).toEqual({
        fieldRenderers: 0,
        pageComponents: 0,
        quickActions: 0,
        columnRenderers: 0,
      })
    })

    it('returns correct counts after registrations', () => {
      const Mock = () => null
      componentRegistry.registerFieldRenderer('a', Mock)
      componentRegistry.registerFieldRenderer('b', Mock)
      componentRegistry.registerPageComponent('p1', Mock)
      componentRegistry.registerQuickAction('q1', Mock)
      componentRegistry.registerColumnRenderer('c1', Mock)
      componentRegistry.registerColumnRenderer('c2', Mock)
      componentRegistry.registerColumnRenderer('c3', Mock)

      const stats = componentRegistry.getStats()
      expect(stats).toEqual({
        fieldRenderers: 2,
        pageComponents: 1,
        quickActions: 1,
        columnRenderers: 3,
      })
    })

    it('clears all registrations', () => {
      const Mock = () => null
      componentRegistry.registerFieldRenderer('a', Mock)
      componentRegistry.registerPageComponent('p1', Mock)
      componentRegistry.registerQuickAction('q1', Mock)
      componentRegistry.registerColumnRenderer('c1', Mock)

      componentRegistry.clear()

      const stats = componentRegistry.getStats()
      expect(stats.fieldRenderers).toBe(0)
      expect(stats.pageComponents).toBe(0)
      expect(stats.quickActions).toBe(0)
      expect(stats.columnRenderers).toBe(0)
    })
  })
})
