import { describe, it, expect, afterEach, vi } from 'vitest'
import { render, screen, act } from '@testing-library/react'
import { OfflineIndicator } from './OfflineIndicator'

function setOnline(value: boolean) {
  Object.defineProperty(navigator, 'onLine', { configurable: true, value })
}

describe('OfflineIndicator', () => {
  afterEach(() => {
    setOnline(true)
    vi.restoreAllMocks()
  })

  it('renders nothing while online', () => {
    setOnline(true)
    const { container } = render(<OfflineIndicator />)
    expect(container).toBeEmptyDOMElement()
    expect(screen.queryByTestId('offline-indicator')).toBeNull()
  })

  it('shows the banner while offline', () => {
    setOnline(false)
    render(<OfflineIndicator />)
    const banner = screen.getByTestId('offline-indicator')
    expect(banner).toHaveAttribute('role', 'status')
    expect(banner).toHaveTextContent(/offline/i)
  })

  it('reacts to online/offline events', () => {
    setOnline(true)
    render(<OfflineIndicator />)
    expect(screen.queryByTestId('offline-indicator')).toBeNull()

    act(() => {
      setOnline(false)
      window.dispatchEvent(new Event('offline'))
    })
    expect(screen.getByTestId('offline-indicator')).toBeInTheDocument()

    act(() => {
      setOnline(true)
      window.dispatchEvent(new Event('online'))
    })
    expect(screen.queryByTestId('offline-indicator')).toBeNull()
  })
})
