/**
 * TopNavBar tests — collection tabs route to `…/o/<collection>` and page tabs to `…/p/<slug>`.
 *
 * Radix-backed Sheet is mocked to render its children directly (avoids ResizeObserver in jsdom).
 */
import React from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, within } from '@testing-library/react'
import { TopNavBar } from './TopNavBar'
import type { NavTab } from './TopNavBar'

const mockNavigate = vi.fn()
vi.mock('react-router-dom', () => ({
  useNavigate: () => mockNavigate,
  useParams: () => ({ tenantSlug: 'couchpicks' }),
  Link: ({ to, children, ...rest }: React.PropsWithChildren<{ to: string }>) => (
    <a href={to} {...rest}>
      {children}
    </a>
  ),
}))

vi.mock('@/hooks/useSystemPermissions', () => ({
  useSystemPermissions: () => ({ hasPermission: () => false }),
}))

vi.mock('@/components/ui/sheet', () => ({
  Sheet: ({ children }: React.PropsWithChildren) => <div>{children}</div>,
  SheetTrigger: ({ children }: React.PropsWithChildren<{ asChild?: boolean }>) => (
    <div>{children}</div>
  ),
  SheetContent: ({ children }: React.PropsWithChildren<Record<string, unknown>>) => (
    <div>{children}</div>
  ),
  SheetHeader: ({ children }: React.PropsWithChildren) => <div>{children}</div>,
  SheetTitle: ({ children }: React.PropsWithChildren) => <div>{children}</div>,
}))

const TABS: NavTab[] = [
  { key: '/resources/titles', kind: 'collection', target: 'titles', label: 'Titles' },
  { key: '/app/p/dashboard', kind: 'page', target: 'dashboard', label: 'Dashboard' },
]

describe('TopNavBar', () => {
  beforeEach(() => mockNavigate.mockClear())

  function desktopNav() {
    return within(screen.getByRole('navigation', { name: 'Object navigation' }))
  }

  it('renders both collection and page tabs', () => {
    render(<TopNavBar tabs={TABS} />)
    expect(desktopNav().getByText('Titles')).toBeInTheDocument()
    expect(desktopNav().getByText('Dashboard')).toBeInTheDocument()
  })

  it('navigates to the object list when a collection tab is clicked', () => {
    render(<TopNavBar tabs={TABS} />)
    fireEvent.click(desktopNav().getByText('Titles'))
    expect(mockNavigate).toHaveBeenCalledWith('/couchpicks/app/o/titles')
  })

  it('navigates to the custom page when a page tab is clicked', () => {
    render(<TopNavBar tabs={TABS} />)
    fireEvent.click(desktopNav().getByText('Dashboard'))
    expect(mockNavigate).toHaveBeenCalledWith('/couchpicks/app/p/dashboard')
  })

  it('groups page items under a Pages heading in the mobile menu', () => {
    render(<TopNavBar tabs={TABS} />)
    // Mobile sheet (mocked open) renders its Collections + Pages section headings.
    expect(screen.getByText('Collections')).toBeInTheDocument()
    expect(screen.getByText('Pages')).toBeInTheDocument()
  })
})
