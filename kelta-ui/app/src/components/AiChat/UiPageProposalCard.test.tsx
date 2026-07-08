/** UiPageProposalCard (app-intelligence slice 2). */
import React from 'react'
import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { UiPageProposalCard } from './UiPageProposalCard'
import type { AiProposal } from './types'

function renderCard(
  proposal: AiProposal,
  handlers?: Partial<{ onApply: () => void; onDismiss: () => void }>
) {
  return render(
    <MemoryRouter initialEntries={['/acme/setup']}>
      <Routes>
        <Route
          path="/:tenantSlug/setup"
          element={
            <UiPageProposalCard
              proposal={proposal}
              onApply={handlers?.onApply ?? vi.fn()}
              onDismiss={handlers?.onDismiss ?? vi.fn()}
            />
          }
        />
      </Routes>
    </MemoryRouter>
  )
}

const pending: AiProposal = {
  id: 'p1',
  type: 'ui_page',
  status: 'pending',
  createdAt: '2026-07-08T12:00:00Z',
  data: {
    name: 'customer_overview',
    title: 'Customer Overview',
    components: [
      { type: 'heading' },
      { type: 'grid', children: [{ type: 'metric' }, { type: 'table' }] },
    ],
    variables: [{ name: 'limit', type: 'number', default: 10 }],
    dataSources: [{ name: 'customers', collection: 'customers' }],
  },
}

describe('UiPageProposalCard', () => {
  it('renders the title, widget count, badges, and tree outline', () => {
    renderCard(pending)
    expect(screen.getByText('Customer Overview')).toBeInTheDocument()
    expect(screen.getByText('4 widgets')).toBeInTheDocument()
    expect(screen.getByText('1 var')).toBeInTheDocument()
    expect(screen.getByText('1 source')).toBeInTheDocument()
    expect(screen.getByText('heading')).toBeInTheDocument()
    expect(screen.getByText('metric')).toBeInTheDocument()
    expect(screen.getByText(/unpublished draft/i)).toBeInTheDocument()
  })

  it('fires onApply and onDismiss', () => {
    const onApply = vi.fn()
    const onDismiss = vi.fn()
    renderCard(pending, { onApply, onDismiss })
    fireEvent.click(screen.getByText('Apply'))
    expect(onApply).toHaveBeenCalledWith('p1')
    fireEvent.click(screen.getByText('Dismiss'))
    expect(onDismiss).toHaveBeenCalledWith('p1')
  })

  it('applied state collapses with an Open Page Builder link', () => {
    renderCard({ ...pending, status: 'applied' })
    expect(screen.getByText('Draft created')).toBeInTheDocument()
    expect(screen.getByTestId('ui-page-open-builder')).toHaveAttribute('href', '/acme/pages')
  })

  it('dismissed state collapses without a link', () => {
    renderCard({ ...pending, status: 'dismissed' })
    expect(screen.getByText('Dismissed')).toBeInTheDocument()
    expect(screen.queryByTestId('ui-page-open-builder')).toBeNull()
  })

  it('caps the outline at 20 rows and shows the remainder', () => {
    const many = {
      ...pending,
      data: {
        ...pending.data,
        components: Array.from({ length: 30 }, () => ({ type: 'text' })),
      },
    }
    renderCard(many)
    expect(screen.getAllByText('text')).toHaveLength(20)
    expect(screen.getByText('+10 more…')).toBeInTheDocument()
  })
})
