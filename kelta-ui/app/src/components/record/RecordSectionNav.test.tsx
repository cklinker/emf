import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { RecordSectionNav } from './RecordSectionNav'

// Mock I18n — return the fallback (or key) so assertions target stable strings.
vi.mock('@/context/I18nContext', () => ({
  useI18n: () => ({
    t: (key: string, paramsOrFallback?: Record<string, string | number> | string) =>
      typeof paramsOrFallback === 'string' ? paramsOrFallback : key,
  }),
}))

const items = [
  { anchorId: 'record-section-a', label: 'Overview', count: 9 },
  { anchorId: 'record-section-b', label: 'Content', count: 2 },
]

describe('RecordSectionNav', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
  })

  it('renders one entry per section with its field count', () => {
    render(<RecordSectionNav items={items} />)
    expect(screen.getByTestId('record-section-nav')).toBeInTheDocument()
    expect(screen.getByText('Overview')).toBeInTheDocument()
    expect(screen.getByText('9')).toBeInTheDocument()
    expect(screen.getByText('Content')).toBeInTheDocument()
    expect(screen.getByText('2')).toBeInTheDocument()
  })

  it('renders nothing when there are no items', () => {
    const { container } = render(<RecordSectionNav items={[]} />)
    expect(container.firstChild).toBeNull()
  })

  it('scrolls to the section anchor and marks the entry active on click', () => {
    const target = document.createElement('div')
    target.id = 'record-section-b'
    const scrollIntoView = vi.fn()
    target.scrollIntoView = scrollIntoView
    document.body.appendChild(target)

    render(<RecordSectionNav items={items} />)
    const button = screen.getByTestId('section-nav-record-section-b')
    fireEvent.click(button)

    expect(scrollIntoView).toHaveBeenCalledWith({ behavior: 'smooth', block: 'start' })
    expect(button).toHaveAttribute('aria-current', 'location')
  })

  it('exposes an accessible nav landmark', () => {
    render(<RecordSectionNav items={items} />)
    expect(screen.getByRole('navigation', { name: 'Sections' })).toBeInTheDocument()
  })
})
