/**
 * Unit tests for the legacy → v2 migration (slice 2c). Covers `needsMigration`, the row-grouping /
 * column-wrapping conversion, `width` clamping, idempotency (a fixpoint), a realistic legacy `config`
 * fixture read through the same `readComponents` path the builder uses, and the migrate→save→reload
 * round-trip via the §5.9 save path (`mergeConfig`).
 */
import { describe, it, expect } from 'vitest'
import type { PageComponent } from './pageModel'
import { migrateTree, needsMigration } from './migrate'
import { readComponents, readConfig, mergeConfig, type PageConfig } from '../pageConfig'
import type { UIPage } from '../PageBuilderPage'

interface LegacyNode extends PageComponent {
  position?: { row: number; column: number; width: number; height: number }
}

/** Build a page-shaped fixture (the worker stores the tree inside `config`). */
function pageWith(config: PageConfig): Partial<UIPage> {
  return { config } as unknown as Partial<UIPage>
}

function legacyNode(
  id: string,
  type: string,
  row: number,
  column: number,
  width: number
): LegacyNode {
  return { id, type, props: { label: id }, position: { row, column, width, height: 1 } }
}

describe('needsMigration', () => {
  it('is true when any node carries a position', () => {
    expect(needsMigration([legacyNode('a', 'heading', 0, 0, 12)])).toBe(true)
  })

  it('is false for a v2 tree (no position anywhere)', () => {
    const v2: PageComponent[] = [
      {
        id: 'g',
        type: 'grid',
        props: {},
        children: [{ id: 'c', type: 'column', props: {}, span: { base: 12 } }],
      },
    ]
    expect(needsMigration(v2)).toBe(false)
  })

  it('is true when a nested child carries a position', () => {
    const tree: LegacyNode[] = [
      { id: 'g', type: 'container', props: {}, children: [legacyNode('x', 'text', 0, 0, 6)] },
    ]
    expect(needsMigration(tree)).toBe(true)
  })
})

describe('migrateTree', () => {
  it('groups top-level nodes by row into columns under one grid (span.base = width)', () => {
    const legacy: LegacyNode[] = [
      legacyNode('a', 'heading', 0, 0, 12),
      legacyNode('b', 'text', 1, 0, 6),
      legacyNode('c', 'image', 1, 6, 6),
    ]
    const out = migrateTree(legacy)
    expect(out).toHaveLength(1)
    expect(out[0].type).toBe('grid')
    const cols = out[0].children!
    expect(cols.map((c) => c.type)).toEqual(['column', 'column', 'column'])
    expect(cols.map((c) => c.span!.base)).toEqual([12, 6, 6])
    // Each column wraps exactly one original node, with position stripped.
    expect(cols[0].children!.map((n) => n.id)).toEqual(['a'])
    expect((cols[0].children![0] as LegacyNode).position).toBeUndefined()
  })

  it('wraps a node without position in a full-width column', () => {
    const out = migrateTree([
      { id: 'x', type: 'text', props: {} } as LegacyNode,
      legacyNode('y', 'text', 0, 0, 4),
    ])
    const cols = out[0].children!
    const xCol = cols.find((c) => c.children![0].id === 'x')!
    expect(xCol.span!.base).toBe(12)
  })

  it('clamps width to 1..12', () => {
    const out = migrateTree([legacyNode('lo', 'text', 0, 0, 0), legacyNode('hi', 'text', 0, 1, 99)])
    const spans = out[0].children!.map((c) => c.span!.base)
    expect(spans).toEqual([1, 12])
  })

  it('strips position and recurses into nested legacy children', () => {
    const legacy: LegacyNode[] = [
      {
        id: 'g',
        type: 'container',
        props: {},
        position: { row: 0, column: 0, width: 12, height: 1 },
        children: [legacyNode('inner', 'text', 0, 0, 6)],
      },
    ]
    const out = migrateTree(legacy)
    const wrapped = out[0].children![0].children![0] // grid > column > container
    expect((wrapped as LegacyNode).position).toBeUndefined()
    expect((wrapped.children![0] as LegacyNode).position).toBeUndefined()
  })

  it('is idempotent — a fixpoint (deep-equal both ways)', () => {
    const legacy: LegacyNode[] = [
      legacyNode('a', 'heading', 0, 0, 12),
      legacyNode('b', 'text', 1, 0, 6),
    ]
    const once = migrateTree(legacy)
    const twice = migrateTree(once as LegacyNode[])
    expect(twice).toEqual(once)
  })

  it('a v2 tree (already migrated) is returned unchanged by needsMigration guard', () => {
    const v2: PageComponent[] = [
      {
        id: 'g',
        type: 'grid',
        props: {},
        children: [
          {
            id: 'c',
            type: 'column',
            props: {},
            span: { base: 12 },
            children: [{ id: 'h', type: 'heading', props: {} }],
          },
        ],
      },
    ]
    expect(needsMigration(v2)).toBe(false)
    // The caller skips migrateTree when schemaVersion === 2; even if called, it returns a structural copy.
    expect(migrateTree(v2 as LegacyNode[])).toEqual(v2)
  })
})

describe('migrate via the real config read path', () => {
  it('reads a realistic legacy page config and migrates to grid > column > widget', () => {
    // The shape the worker stores: { layout, components:[...legacy...] } with no schemaVersion.
    const page = pageWith({
      layout: { type: 'single' as const },
      components: [
        legacyNode('a', 'heading', 0, 0, 12),
        legacyNode('b', 'table', 1, 0, 8),
        legacyNode('c', 'text', 1, 8, 4),
      ] as unknown as PageConfig['components'],
    })
    const raw = readComponents(page) as unknown as LegacyNode[]
    expect(needsMigration(raw)).toBe(true)
    const migrated = migrateTree(raw)
    expect(migrated[0].type).toBe('grid')
    expect(migrated[0].children!.every((c) => c.type === 'column')).toBe(true)
    // Migration only rewrites `components`; config.layout is inert legacy and untouched.
    expect(readConfig(page).layout).toEqual({ type: 'single' })
  })

  it('migrate → save → reload round-trips deep-equal with schemaVersion:2 (no node dropped)', () => {
    const page = pageWith({
      layout: { type: 'single' as const },
      components: [
        legacyNode('a', 'heading', 0, 0, 12),
        legacyNode('b', 'text', 1, 0, 6),
      ] as unknown as PageConfig['components'],
    })
    const migrated = migrateTree(readComponents(page) as unknown as LegacyNode[])
    const savedConfig = mergeConfig(readConfig(page), {
      components: migrated,
      schemaVersion: 2,
    })
    const reloaded = readComponents(pageWith(savedConfig)) as unknown as LegacyNode[]
    expect(reloaded).toEqual(migrated)
    expect(savedConfig.schemaVersion).toBe(2)
    // A node's span survives the mergeConfig overlay; no position resurrected.
    expect(needsMigration(reloaded)).toBe(false)
  })
})
