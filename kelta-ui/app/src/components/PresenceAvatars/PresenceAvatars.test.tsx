/** PresenceAvatars (app-intelligence slice 3): others-only filter, overflow, empty. */
import React from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { PresenceAvatars } from './PresenceAvatars'
import type { PresenceUser } from '@/realtime'

const state: { users: PresenceUser[] } = { users: [] }

vi.mock('@/realtime', () => ({
  usePresence: () => state.users,
}))
const identityState: { identity?: { userId: string; email: string; profileId: string } } = {
  identity: { userId: 'u-me', email: 'me@example.com', profileId: 'p1' },
}

vi.mock('@/hooks/useMyIdentity', () => ({
  useMyIdentity: () => ({ identity: identityState.identity }),
}))

describe('PresenceAvatars', () => {
  beforeEach(() => {
    state.users = []
    identityState.identity = { userId: 'u-me', email: 'me@example.com', profileId: 'p1' }
  })

  it('renders nothing while identity is still loading (cannot filter self yet)', () => {
    identityState.identity = undefined
    state.users = [{ id: 'u-me', email: 'me@example.com' }]
    const { container } = render(<PresenceAvatars resource="record:orders/1" />)
    expect(container).toBeEmptyDOMElement()
  })

  it('renders nothing when alone', () => {
    state.users = [{ id: 'u-me', email: 'me@example.com' }]
    const { container } = render(<PresenceAvatars resource="record:orders/1" />)
    expect(container).toBeEmptyDOMElement()
  })

  it('filters self by id OR email (JWT subject may be either)', () => {
    state.users = [
      { id: 'me@example.com' }, // email-subject variant of self
      { id: 'u-me', email: 'me@example.com' }, // uuid-subject variant of self
      { id: 'u-bob', email: 'bob.ross@example.com' },
    ]
    render(<PresenceAvatars resource="record:orders/1" />)
    expect(screen.getByTestId('presence-avatars')).toBeInTheDocument()
    expect(screen.getByTestId('presence-avatar-u-bob')).toHaveTextContent('BR')
    expect(screen.queryByTestId('presence-avatar-u-me')).toBeNull()
  })

  it('caps avatars and shows the overflow count', () => {
    state.users = Array.from({ length: 8 }, (_, i) => ({
      id: `u-${i}`,
      email: `user${i}@example.com`,
    }))
    render(<PresenceAvatars resource="record:orders/1" />)
    expect(screen.getAllByTestId(/^presence-avatar-/)).toHaveLength(5)
    expect(screen.getByTestId('presence-overflow')).toHaveTextContent('+3')
  })
})
