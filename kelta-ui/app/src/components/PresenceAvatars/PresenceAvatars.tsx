/**
 * PresenceAvatars (app-intelligence slice 3): overlapping initial circles for the
 * OTHER users viewing a resource. Renders nothing when alone — the common case costs
 * one presence subscription and no DOM. Self is filtered by userId OR email (the JWT
 * subject may be either, depending on the login flow — Phase 1 finding).
 */
import React from 'react'
import { usePresence } from '@/realtime'
import { useMyIdentity } from '@/hooks/useMyIdentity'
import type { PresenceUser } from '@/realtime'

const MAX_AVATARS = 5

function initialsOf(user: PresenceUser): string {
  const source = user.email || user.id || '?'
  const namePart = source.split('@')[0]
  const parts = namePart.split(/[._-]/).filter(Boolean)
  if (parts.length >= 2) {
    return (parts[0][0] + parts[1][0]).toUpperCase()
  }
  return namePart.slice(0, 2).toUpperCase()
}

export function PresenceAvatars({
  resource,
}: {
  resource: string | null
}): React.ReactElement | null {
  const users = usePresence(resource)
  const { identity } = useMyIdentity()

  const others = users.filter(
    (u) =>
      u.id !== identity?.userId &&
      u.id !== identity?.email &&
      (u.email === undefined || u.email !== identity?.email)
  )
  if (others.length === 0) return null

  const shown = others.slice(0, MAX_AVATARS)
  const overflow = others.length - shown.length

  return (
    <div
      className="flex items-center -space-x-2"
      role="group"
      aria-label={`${others.length} other ${others.length === 1 ? 'person' : 'people'} viewing`}
      data-testid="presence-avatars"
    >
      {shown.map((user) => (
        <span
          key={user.id}
          title={user.email || user.id}
          className="flex h-7 w-7 items-center justify-center rounded-full border-2 border-background bg-primary/15 text-[10px] font-semibold text-primary"
          data-testid={`presence-avatar-${user.id}`}
        >
          {initialsOf(user)}
        </span>
      ))}
      {overflow > 0 && (
        <span
          className="flex h-7 w-7 items-center justify-center rounded-full border-2 border-background bg-muted text-[10px] font-semibold text-muted-foreground"
          data-testid="presence-overflow"
        >
          +{overflow}
        </span>
      )}
    </div>
  )
}
