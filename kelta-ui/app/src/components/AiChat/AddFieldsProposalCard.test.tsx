import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { AddFieldsProposalCard } from './AddFieldsProposalCard'
import type { AiProposal } from './types'

function makeProposal(overrides: Partial<AiProposal> = {}): AiProposal {
  return {
    id: 'p1',
    type: 'add_fields',
    status: 'pending',
    data: {
      collectionName: 'customers',
      fields: [
        { name: 'loyalty_points', type: 'INTEGER', nullable: false },
        { name: 'marketing_consent', type: 'BOOLEAN' },
      ],
    },
    createdAt: new Date().toISOString(),
    ...overrides,
  }
}

describe('AddFieldsProposalCard', () => {
  it('renders the target collection and field rows for a pending proposal', () => {
    render(
      <AddFieldsProposalCard
        proposal={makeProposal()}
        onApply={vi.fn()}
        onDismiss={vi.fn()}
      />
    )
    expect(screen.getByText('Add fields')).toBeInTheDocument()
    expect(screen.getByText('→ customers')).toBeInTheDocument()
    expect(screen.getByText('loyalty_points')).toBeInTheDocument()
    expect(screen.getByText('marketing_consent')).toBeInTheDocument()
  })

  it('calls onApply with the proposal id', () => {
    const onApply = vi.fn()
    render(
      <AddFieldsProposalCard proposal={makeProposal()} onApply={onApply} onDismiss={vi.fn()} />
    )
    fireEvent.click(screen.getByText('Apply'))
    expect(onApply).toHaveBeenCalledWith('p1')
  })

  it('renders collapsed view when applied', () => {
    render(
      <AddFieldsProposalCard
        proposal={makeProposal({ status: 'applied' })}
        onApply={vi.fn()}
        onDismiss={vi.fn()}
      />
    )
    expect(screen.getByText('Applied')).toBeInTheDocument()
    expect(screen.queryByText('Apply')).not.toBeInTheDocument()
  })
})
