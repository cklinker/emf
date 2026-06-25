import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { widgetRegistry } from './registry'
import { registerBuiltinWidgets } from './builtins'
import { componentRegistry } from '@/services/componentRegistry'

describe('widgetRegistry', () => {
  beforeEach(() => {
    registerBuiltinWidgets()
  })

  afterEach(() => {
    componentRegistry.clear()
  })

  it('registers the built-in widgets', () => {
    expect(widgetRegistry.get('heading').label).toBe('Heading')
    expect(widgetRegistry.get('table').category).toBe('data')
    expect(widgetRegistry.has('container')).toBe(true)
  })

  it('places form in the input category and table in data', () => {
    expect(widgetRegistry.get('form').category).toBe('input')
    expect(widgetRegistry.get('table').category).toBe('data')
  })

  it('registers the eight standalone typed inputs alongside form in the input category (slice 2f)', () => {
    const byCat = widgetRegistry.listByCategory()
    const inputTypes = byCat.input.map((w) => w.type)
    expect(inputTypes).toEqual(
      expect.arrayContaining([
        'form',
        'text-input',
        'number-input',
        'checkbox',
        'dropdown',
        'datepicker',
        'lookup',
        'multi-picklist',
        'rich-text',
      ])
    )
    // The dropdown's field-picker is restricted to picklist fields (the 2f filter hint).
    const dropdown = widgetRegistry.get('dropdown')
    const fieldProp = dropdown.propSchema.find((p) => p.kind === 'field-picker')
    expect(fieldProp?.fieldTypeFilter).toEqual(['picklist'])
  })

  it('groups widgets by category', () => {
    const byCat = widgetRegistry.listByCategory()
    expect(byCat.content.map((w) => w.type)).toEqual(
      expect.arrayContaining(['heading', 'text', 'button', 'image'])
    )
    expect(byCat.layout.map((w) => w.type)).toEqual(expect.arrayContaining(['card', 'container']))
  })

  it('falls back to a registered plugin page component as a synthetic descriptor', () => {
    const Plugin = () => null
    componentRegistry.registerPageComponent('my-plugin-widget', Plugin)

    const descriptor = widgetRegistry.get('my-plugin-widget')
    expect(descriptor.type).toBe('my-plugin-widget')
    expect(descriptor.category).toBe('content')
    expect(descriptor.propSchema).toEqual([])
  })

  it('returns an unknown-default descriptor for an unregistered type', () => {
    const descriptor = widgetRegistry.get('definitely-not-a-widget')
    expect(descriptor.type).toBe('definitely-not-a-widget')
    expect(descriptor.propSchema).toEqual([])
  })

  it('exposes supportedEvents on event-capable widgets', () => {
    expect(widgetRegistry.get('button').supportedEvents).toEqual(['onClick'])
  })
})
