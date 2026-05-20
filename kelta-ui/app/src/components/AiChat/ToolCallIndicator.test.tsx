import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ToolCallIndicator } from './ToolCallIndicator'

describe('ToolCallIndicator', () => {
  it('renders a friendly label for known tools', () => {
    render(
      <ToolCallIndicator
        call={{ id: 'toolu_1', name: 'get_collection_schema', status: 'pending' }}
      />
    )
    expect(screen.getByText(/Inspecting collection/i)).toBeInTheDocument()
  })

  it('falls back to the tool name for unknown tools', () => {
    render(<ToolCallIndicator call={{ id: 'toolu_2', name: 'foo_bar', status: 'pending' }} />)
    expect(screen.getByText('foo_bar')).toBeInTheDocument()
  })

  it('includes summary text when provided', () => {
    render(
      <ToolCallIndicator
        call={{ id: 'toolu_3', name: 'query_records', status: 'done', summary: '5 rows' }}
      />
    )
    expect(screen.getByText(/5 rows/)).toBeInTheDocument()
  })

  it('shows error state visually', () => {
    const { container } = render(
      <ToolCallIndicator call={{ id: 'toolu_4', name: 'get_picklist', status: 'error' }} />
    )
    expect(container.firstChild).toHaveClass('text-destructive')
  })
})
