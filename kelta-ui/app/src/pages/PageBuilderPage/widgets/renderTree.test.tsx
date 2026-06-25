import { render, screen } from '@testing-library/react'
import { describe, it, expect, beforeEach } from 'vitest'
import { RenderTree } from './renderTree'
import { registerBuiltinWidgets } from './builtins'
import type { RenderNode } from './types'

describe('RenderTree', () => {
  beforeEach(() => {
    registerBuiltinWidgets()
  })

  it('renders an empty state when there are no components', () => {
    render(<RenderTree components={[]} tenantSlug="acme" />)
    expect(screen.getByTestId('page-empty')).toBeInTheDocument()
  })

  it('renders a heading and honors its level (the deliberate 2a behavior change)', () => {
    const nodes: RenderNode[] = [
      { id: 'h1', type: 'heading', props: { text: 'Hello', level: 'h3' } },
    ]
    render(<RenderTree components={nodes} tenantSlug="acme" />)
    const heading = screen.getByTestId('page-node-heading')
    expect(heading.tagName).toBe('H3')
    expect(heading).toHaveTextContent('Hello')
  })

  it('defaults a level-less heading to h2 (legacy parity)', () => {
    render(
      <RenderTree
        components={[{ id: 'h', type: 'heading', props: { text: 'Plain' } }]}
        tenantSlug="acme"
      />
    )
    expect(screen.getByTestId('page-node-heading').tagName).toBe('H2')
  })

  it('renders text, button and image built-ins with their testids', () => {
    const nodes: RenderNode[] = [
      { id: 't', type: 'text', props: { content: 'Body' } },
      { id: 'b', type: 'button', props: { label: 'Go' } },
      { id: 'i', type: 'image', props: { src: 'x.png', alt: 'Pic' } },
    ]
    render(<RenderTree components={nodes} tenantSlug="acme" />)
    expect(screen.getByTestId('page-node-text')).toHaveTextContent('Body')
    expect(screen.getByTestId('page-node-button')).toHaveTextContent('Go')
    expect(screen.getByTestId('page-node-image')).toHaveAttribute('alt', 'Pic')
  })

  it('renders container children through the shared path', () => {
    const nodes: RenderNode[] = [
      {
        id: 'c',
        type: 'container',
        children: [{ id: 'h', type: 'heading', props: { text: 'Nested' } }],
      },
    ]
    render(<RenderTree components={nodes} tenantSlug="acme" />)
    expect(screen.getByTestId('page-node-container')).toBeInTheDocument()
    expect(screen.getByTestId('page-node-heading')).toHaveTextContent('Nested')
  })

  it('renders a data table as a placeholder in editor mode (no live fetch)', () => {
    render(
      <RenderTree
        components={[{ id: 'tbl', type: 'table', props: { dataView: { collection: 'orders' } } }]}
        tenantSlug="acme"
        mode="editor"
      />
    )
    expect(screen.getByTestId('page-node-table')).toHaveTextContent('Table')
  })

  it('renders an unknown type with the unknown placeholder', () => {
    render(<RenderTree components={[{ id: 'x', type: 'mystery' }]} tenantSlug="acme" />)
    expect(screen.getByTestId('page-node-unknown')).toHaveTextContent('mystery')
  })

  it('matches the golden snapshot for the built-in widget set (editor mode)', () => {
    const nodes: RenderNode[] = [
      { id: 'h', type: 'heading', props: { text: 'Title', level: 'h2' } },
      { id: 't', type: 'text', props: { content: 'Paragraph' } },
      { id: 'b', type: 'button', props: { label: 'Click' } },
      { id: 'i', type: 'image', props: { src: 'img.png', alt: 'Alt' } },
      {
        id: 'card',
        type: 'card',
        children: [{ id: 'ch', type: 'heading', props: { text: 'In card' } }],
      },
      {
        id: 'cont',
        type: 'container',
        children: [{ id: 'ct', type: 'text', props: { content: 'In container' } }],
      },
      { id: 'tbl', type: 'table', props: { dataView: { collection: 'orders' } } },
      { id: 'frm', type: 'form', props: { dataView: { collection: 'orders', fields: ['name'] } } },
    ]
    const { container } = render(<RenderTree components={nodes} tenantSlug="acme" mode="editor" />)
    expect(container).toMatchSnapshot()
  })
})
