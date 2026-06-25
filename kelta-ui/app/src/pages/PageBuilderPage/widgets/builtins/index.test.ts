/**
 * Registration (slice 2g). After `registerBuiltinWidgets()`, the breadth widgets resolve to real
 * descriptors (not the unknown default) in their declared categories, `tab-panel` resolves but is
 * palette-hidden, and `image` is the upgraded descriptor (has an `objectFit` prop-schema entry).
 */
import { describe, it, expect, beforeEach } from 'vitest'
import { widgetRegistry } from '../registry'
import { registerBuiltinWidgets } from './index'

beforeEach(() => {
  registerBuiltinWidgets()
})

describe('2g widget registration', () => {
  it('registers chart, tabs, nav, icon, link as real descriptors', () => {
    for (const type of ['chart', 'tabs', 'nav', 'icon', 'link']) {
      const descriptor = widgetRegistry.get(type)
      expect(descriptor.type).toBe(type)
      // A real descriptor has a non-empty propSchema; the unknown/plugin fallbacks have `[]`.
      expect(descriptor.propSchema.length).toBeGreaterThan(0)
    }
  })

  it('registers tab-panel but flags it palette-hidden', () => {
    const panel = widgetRegistry.get('tab-panel')
    expect(panel.type).toBe('tab-panel')
    expect(panel.paletteHidden).toBe(true)
  })

  it('places nav and tabs in navigation, and chart in chart', () => {
    const byCat = widgetRegistry.listByCategory()
    expect(byCat.navigation.map((w) => w.type)).toEqual(expect.arrayContaining(['nav', 'tabs']))
    expect(byCat.chart.map((w) => w.type)).toEqual(expect.arrayContaining(['chart']))
  })

  it('upgrades the image descriptor with an objectFit prop-schema entry', () => {
    const image = widgetRegistry.get('image')
    expect(image.type).toBe('image')
    expect(image.propSchema.map((p) => p.key)).toEqual(expect.arrayContaining(['objectFit']))
  })
})
