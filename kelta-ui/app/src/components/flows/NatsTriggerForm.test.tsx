/**
 * NatsTriggerForm Tests
 *
 * Covers the NATS trigger configuration form:
 * - Topic input is required
 * - Helper text shows the resulting platform trigger subject pattern
 * - onChange propagates topic edits
 */

import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi } from 'vitest'
import { NatsTriggerForm } from './NatsTriggerForm'

describe('NatsTriggerForm', () => {
  it('renders a required topic input', () => {
    render(<NatsTriggerForm config={{}} onChange={vi.fn()} />)

    const input = screen.getByLabelText(/Topic/)
    expect(input).toBeInTheDocument()
    expect(input).toBeRequired()
  })

  it('shows the trigger subject pattern in the helper text', () => {
    render(<NatsTriggerForm config={{}} onChange={vi.fn()} />)

    expect(screen.getByText('kelta.trigger.{tenant}.{topic}')).toBeInTheDocument()
  })

  it('previews the subject with the entered topic', () => {
    render(<NatsTriggerForm config={{ topic: 'order-events' }} onChange={vi.fn()} />)

    expect(screen.getByText('kelta.trigger.{tenant}.order-events')).toBeInTheDocument()
  })

  it('calls onChange with the updated topic', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    render(<NatsTriggerForm config={{}} onChange={onChange} />)

    await user.type(screen.getByLabelText(/Topic/), 'x')

    expect(onChange).toHaveBeenCalledWith({ topic: 'x' })
  })
})
