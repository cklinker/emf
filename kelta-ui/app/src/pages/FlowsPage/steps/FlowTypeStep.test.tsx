/**
 * FlowTypeStep Tests
 *
 * Covers the flow-type picker in the create-flow wizard:
 * - All four trigger types are offered, including NATS Message
 * - Kafka is fully removed (messaging is NATS JetStream only)
 * - Selecting a type invokes onChange with the FlowType value
 */

import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi } from 'vitest'
import { FlowTypeStep } from './FlowTypeStep'

describe('FlowTypeStep', () => {
  it('renders all flow type options', () => {
    render(<FlowTypeStep value={null} onChange={vi.fn()} />)

    expect(screen.getByText('Record Change')).toBeInTheDocument()
    expect(screen.getByText('Scheduled')).toBeInTheDocument()
    expect(screen.getByText('API / Webhook')).toBeInTheDocument()
    expect(screen.getByText('NATS Message')).toBeInTheDocument()
  })

  it('does not offer a Kafka option', () => {
    render(<FlowTypeStep value={null} onChange={vi.fn()} />)

    expect(screen.queryByText(/kafka/i)).not.toBeInTheDocument()
  })

  it('calls onChange with NATS_TRIGGERED when the NATS option is clicked', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    render(<FlowTypeStep value={null} onChange={onChange} />)

    await user.click(screen.getByRole('button', { name: /NATS Message/ }))

    expect(onChange).toHaveBeenCalledWith('NATS_TRIGGERED')
  })
})
