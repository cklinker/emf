/**
 * Avatar & AvatarGroup Components
 *
 * Displays user avatars with initials, images, or fallback icons.
 * AvatarGroup renders overlapping avatars with an overflow indicator.
 *
 * Features:
 * - Deterministic background color based on user identity
 * - Initials extracted from name or email
 * - Image support with fallback on error
 * - Size variants: sm (24px), md (32px), lg (40px), xl (48px)
 * - AvatarGroup with configurable max display count
 */

import React, { useState } from 'react'
import { User } from 'lucide-react'
import { cn } from '@/lib/utils'

/**
 * Color palette for avatar backgrounds (12 colors with good white text contrast)
 */
const AVATAR_COLORS = [
  '#E53935',
  '#D81B60',
  '#8E24AA',
  '#5E35B1',
  '#3949AB',
  '#1E88E5',
  '#00897B',
  '#43A047',
  '#7CB342',
  '#F4511E',
  '#6D4C41',
  '#546E7A',
] as const

/**
 * Size variants for the avatar
 */
export type AvatarSize = 'sm' | 'md' | 'lg' | 'xl'

/**
 * Icon sizes mapped to avatar size variants
 */
const ICON_SIZES: Record<AvatarSize, number> = {
  sm: 12,
  md: 16,
  lg: 20,
  xl: 24,
}

/**
 * Props for the Avatar component
 */
export interface AvatarProps {
  /** User's full name (used for initials and color hashing) */
  name?: string
  /** User's email address (fallback for initials and color hashing) */
  email?: string
  /** User ID (used for deterministic color hashing) */
  userId?: string
  /** URL to the user's profile image */
  imageUrl?: string
  /** Size variant */
  size?: AvatarSize
  /** Optional custom class name */
  className?: string
}

/**
 * Generate a deterministic hash from a string.
 * Uses a simple djb2-like hash algorithm.
 */
function hashString(str: string): number {
  let hash = 5381
  for (let i = 0; i < str.length; i++) {
    hash = (hash * 33) ^ str.charCodeAt(i)
  }
  return Math.abs(hash)
}

/**
 * Get a deterministic color from the palette based on a string identifier.
 */
function getAvatarColor(identifier: string): string {
  const index = hashString(identifier) % AVATAR_COLORS.length
  return AVATAR_COLORS[index]
}

/**
 * Extract initials from a name or email.
 * - For names: first letter of first and last name
 * - For emails: first 2 characters of the local part
 */
function getInitials(name?: string, email?: string): string | null {
  if (name) {
    const parts = name.trim().split(/\s+/)
    if (parts.length >= 2) {
      return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
    }
    return parts[0].substring(0, 2).toUpperCase()
  }
  if (email) {
    const local = email.split('@')[0]
    return local.substring(0, 2).toUpperCase()
  }
  return null
}

const SIZE_CLASSES: Record<AvatarSize, string> = {
  sm: 'size-6 text-[10px]',
  md: 'size-8 text-xs',
  lg: 'size-10 text-sm',
  xl: 'size-12 text-base',
}

/**
 * Avatar Component
 *
 * Renders a circular avatar showing a user image, initials, or a fallback icon.
 *
 * @example
 * ```tsx
 * <Avatar name="Jane Doe" size="md" />
 * <Avatar email="jane@example.com" imageUrl="/photos/jane.jpg" />
 * <Avatar /> // shows fallback user icon
 * ```
 */
export function Avatar({
  name,
  email,
  userId,
  imageUrl,
  size = 'md',
  className,
}: AvatarProps): React.ReactElement {
  const [imageError, setImageError] = useState(false)

  const initials = getInitials(name, email)
  const identifier = userId || email || name || ''
  const bgColor = identifier ? getAvatarColor(identifier) : '#9E9E9E'
  const showImage = imageUrl && !imageError

  const baseClasses = cn(
    'inline-flex items-center justify-center rounded-full font-semibold text-white select-none shrink-0 overflow-hidden leading-none',
    SIZE_CLASSES[size],
    className
  )

  if (showImage) {
    return (
      <div
        className={baseClasses}
        style={{ backgroundColor: bgColor }}
        data-testid="avatar"
        title={name || email}
      >
        <img
          src={imageUrl}
          alt={name || email || 'User avatar'}
          className="size-full object-cover rounded-full"
          onError={() => setImageError(true)}
        />
      </div>
    )
  }

  if (initials) {
    return (
      <div
        className={baseClasses}
        style={{ backgroundColor: bgColor }}
        data-testid="avatar"
        title={name || email}
      >
        {initials}
      </div>
    )
  }

  // Fallback: generic user icon
  return (
    <div
      className={baseClasses}
      style={{ backgroundColor: '#9E9E9E' }}
      data-testid="avatar"
      title="User"
    >
      <User size={ICON_SIZES[size]} color="white" aria-hidden="true" />
    </div>
  )
}

/**
 * User data for AvatarGroup
 */
export interface AvatarGroupUser {
  name?: string
  email?: string
  userId?: string
}

/**
 * Props for the AvatarGroup component
 */
export interface AvatarGroupProps {
  /** Array of user objects to display */
  users: AvatarGroupUser[]
  /** Maximum number of avatars to display before showing overflow (default 5) */
  max?: number
  /** Size variant for all avatars in the group */
  size?: 'sm' | 'md' | 'lg'
}

const GROUP_MARGIN_CLASSES: Record<'sm' | 'md' | 'lg', string> = {
  sm: '-ml-1.5',
  md: '-ml-2',
  lg: '-ml-2.5',
}

/**
 * AvatarGroup Component
 *
 * Renders a row of overlapping avatars with an optional "+N" overflow indicator.
 *
 * @example
 * ```tsx
 * <AvatarGroup
 *   users={[
 *     { name: "Jane Doe" },
 *     { name: "John Smith" },
 *     { email: "bob@example.com" },
 *   ]}
 *   max={3}
 * />
 * ```
 */
export function AvatarGroup({ users, max = 5, size = 'md' }: AvatarGroupProps): React.ReactElement {
  const visible = users.slice(0, max)
  const overflowCount = users.length - max

  return (
    <div className="flex items-center" data-testid="avatar-group">
      {visible.map((user, index) => (
        <div
          key={user.userId || user.email || user.name || index}
          className={cn('relative', index > 0 && GROUP_MARGIN_CLASSES[size])}
        >
          <Avatar
            name={user.name}
            email={user.email}
            userId={user.userId}
            size={size}
            className="border-2 border-background dark:border-background box-content"
          />
        </div>
      ))}
      {overflowCount > 0 && (
        <div className={cn('relative', GROUP_MARGIN_CLASSES[size])}>
          <div
            className={cn(
              'inline-flex items-center justify-center rounded-full font-semibold select-none shrink-0 overflow-hidden leading-none border-2 border-background dark:border-background box-content',
              'bg-muted text-muted-foreground',
              SIZE_CLASSES[size]
            )}
            data-testid="avatar-group-overflow"
            title={`${overflowCount} more`}
          >
            +{overflowCount}
          </div>
        </div>
      )}
    </div>
  )
}

export default Avatar
