import { describe, it, expect } from 'vitest'
import { readComponents, readConfig, mergeConfig } from './pageConfig'
import type { PageComponent } from './PageBuilderPage'

const comp = (id: string): PageComponent => ({
  id,
  type: 'heading',
  props: {},
  position: { row: 0, column: 0, width: 12, height: 1 },
})

describe('pageConfig', () => {
  describe('readComponents', () => {
    it('prefers config.components', () => {
      const page = { config: { components: [comp('a')] }, components: [comp('legacy')] }
      expect(readComponents(page).map((c) => c.id)).toEqual(['a'])
    })

    it('falls back to legacy top-level components', () => {
      expect(readComponents({ components: [comp('legacy')] }).map((c) => c.id)).toEqual(['legacy'])
    })

    it('returns [] for null/empty', () => {
      expect(readComponents(null)).toEqual([])
      expect(readComponents({})).toEqual([])
    })
  })

  describe('readConfig', () => {
    it('returns the config object or {}', () => {
      expect(readConfig({ config: { layout: { type: 'single' } } })).toEqual({
        layout: { type: 'single' },
      })
      expect(readConfig({})).toEqual({})
      expect(readConfig(null)).toEqual({})
    })
  })

  describe('mergeConfig', () => {
    it('overlays components while preserving layout', () => {
      const result = mergeConfig(
        { layout: { type: 'grid' }, components: [comp('old')] },
        {
          components: [comp('new')],
        }
      )
      expect(result.layout).toEqual({ type: 'grid' })
      expect(result.components?.map((c) => c.id)).toEqual(['new'])
    })

    it('overlays layout while preserving components', () => {
      const result = mergeConfig(
        { layout: { type: 'single' }, components: [comp('keep')] },
        {
          layout: { type: 'sidebar' },
        }
      )
      expect(result.layout).toEqual({ type: 'sidebar' })
      expect(result.components?.map((c) => c.id)).toEqual(['keep'])
    })
  })
})
