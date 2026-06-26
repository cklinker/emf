/**
 * Parity tests for the 2c layout built-ins (grid/row/column/divider). Asserts the 12-col grid track, the
 * per-child span classes, the editor-only empty-column placeholder, and that editor and runtime render
 * the same layout DOM (the 2a de-dup guarantee — one descriptor `Render` for both modes).
 */
import { describe, it, expect } from 'vitest'
import { render } from '@testing-library/react'
import { RenderTree } from '../renderTree'
import type { RenderNode } from '../types'
import '../builtins'

const tenantSlug = 'acme'

function gridTree(): RenderNode[] {
  return [
    {
      id: 'g',
      type: 'grid',
      props: {},
      children: [
        {
          id: 'c1',
          type: 'column',
          props: {},
          span: { base: 12, md: 6 },
          children: [{ id: 'h', type: 'heading', props: { text: 'Orders', level: 'h2' } }],
        },
        { id: 'c2', type: 'column', props: {}, span: { base: 6 }, children: [] },
      ],
    },
    { id: 'd', type: 'divider', props: {} },
  ]
}

describe('layout built-ins', () => {
  it('grid renders the 12-col track and wraps each child in its span classes', () => {
    const { getByTestId } = render(
      <RenderTree mode="editor" components={gridTree()} tenantSlug={tenantSlug} />
    )
    const grid = getByTestId('page-node-grid')
    expect(grid.className).toContain('grid-cols-12')
    // The first column carries span { base:12, md:6 } → its wrapper has both classes.
    const html = grid.innerHTML
    expect(html).toContain('col-span-12')
    expect(html).toContain('md:col-span-6')
  })

  it('row renders the 12-col track', () => {
    const { getByTestId } = render(
      <RenderTree
        mode="editor"
        components={[{ id: 'r', type: 'row', props: {}, children: [] }]}
        tenantSlug={tenantSlug}
      />
    )
    expect(getByTestId('page-node-row').className).toContain('grid-cols-12')
  })

  it('column shows the dashed placeholder only when empty in editor mode', () => {
    const empty: RenderNode[] = [{ id: 'c', type: 'column', props: {}, children: [] }]
    const editor = render(<RenderTree mode="editor" components={empty} tenantSlug={tenantSlug} />)
    expect(editor.getByTestId('page-node-column').className).toContain('border-dashed')
    editor.unmount()

    const runtime = render(<RenderTree mode="runtime" components={empty} tenantSlug={tenantSlug} />)
    // Runtime renders the stack (no dashed placeholder), still the same testid.
    expect(runtime.getByTestId('page-node-column').className).not.toContain('border-dashed')
  })

  it('divider renders an <hr>', () => {
    const { getByTestId } = render(
      <RenderTree
        mode="editor"
        components={[{ id: 'd', type: 'divider', props: {} }]}
        tenantSlug={tenantSlug}
      />
    )
    expect(getByTestId('page-node-divider').tagName).toBe('HR')
  })

  it('editor and runtime render identical layout DOM for grid > column(span) > heading', () => {
    const components: RenderNode[] = [
      {
        id: 'g',
        type: 'grid',
        props: {},
        children: [
          {
            id: 'c',
            type: 'column',
            props: {},
            span: { base: 12, md: 6 },
            children: [{ id: 'h', type: 'heading', props: { text: 'X', level: 'h2' } }],
          },
        ],
      },
    ]
    const editor = render(
      <RenderTree mode="editor" components={components} tenantSlug={tenantSlug} />
    )
    const editorGrid = editor.getByTestId('page-node-grid').outerHTML
    editor.unmount()
    const runtime = render(
      <RenderTree mode="runtime" components={components} tenantSlug={tenantSlug} />
    )
    const runtimeGrid = runtime.getByTestId('page-node-grid').outerHTML
    expect(editorGrid).toBe(runtimeGrid)
  })
})
