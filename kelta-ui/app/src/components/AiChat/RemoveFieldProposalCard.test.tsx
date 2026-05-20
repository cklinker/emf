import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { RemoveFieldProposalCard } from './RemoveFieldProposalCard'
import type { AiProposal } from './types'

function makeProposal(): AiProposal {
  return {
    id: 'p1',
    type: 'remove_field',
    status: 'pending',
    data: {
      collectionName: 'products',
      fieldName: 'old_sku',
      reason: 'Replaced by sku',
    },
    createdAt: new Date().toISOString(),
  }
}

describe('RemoveFieldProposalCard', () => {
  it('renders title, target field, destructive warning, and reason', () => {
    render(
      <RemoveFieldProposalCard proposal={makeProposal()} onApply={vi.fn()} onDismiss={vi.fn()} />
    )
    // Header title and primary action both say "Remove field" — disambiguate via roles.
    expect(screen.getAllByText(/Remove field/).length).toBeGreaterThanOrEqual(1)
    expect(screen.getByText('products.old_sku')).toBeInTheDocument()
    expect(screen.getByText(/data will be deleted/i)).toBeInTheDocument()
    expect(screen.getByText(/Replaced by sku/)).toBeInTheDocument()
  })

  it('renders applied state without action buttons', () => {
    const applied: AiProposal = { ...makeProposal(), status: 'applied' }
    render(
      <RemoveFieldProposalCard proposal={applied} onApply={vi.fn()} onDismiss={vi.fn()} />
    )
    expect(screen.getByText('Removed')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Remove field/ })).not.toBeInTheDocument()
  })
})
