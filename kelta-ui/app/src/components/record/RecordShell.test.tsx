import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { RecordShell } from './RecordShell'

describe('RecordShell', () => {
  it('renders only the loading state when isLoading', () => {
    render(
      <RecordShell
        variant="enduser"
        isLoading
        body={<div>body</div>}
        breadcrumb={<div>crumb</div>}
      />
    )
    expect(screen.getByTestId('record-shell-loading')).toBeInTheDocument()
    expect(screen.queryByText('body')).toBeNull()
    expect(screen.queryByText('crumb')).toBeNull()
  })

  it('renders the status slot instead of the frame (error / not-found branch)', () => {
    render(
      <RecordShell
        variant="admin"
        statusSlot={<div>not found</div>}
        body={<div>body</div>}
        tabBar={<div>tabs</div>}
      />
    )
    const status = screen.getByTestId('record-shell-status')
    expect(status).toHaveAttribute('data-variant', 'admin')
    expect(screen.getByText('not found')).toBeInTheDocument()
    expect(screen.queryByText('body')).toBeNull()
    expect(screen.queryByText('tabs')).toBeNull()
  })

  it('composes every slot in order when rendering the frame', () => {
    render(
      <RecordShell
        variant="enduser"
        breadcrumb={<div>crumb</div>}
        header={<div>header</div>}
        body={<div>body</div>}
        rail={<div>rail</div>}
        tabBar={<div>tabs</div>}
        belowTabs={<div>below</div>}
        dialogs={<div>dialog</div>}
      />
    )
    const shell = screen.getByTestId('record-shell')
    expect(shell).toHaveAttribute('data-variant', 'enduser')
    for (const text of ['crumb', 'header', 'body', 'rail', 'tabs', 'below', 'dialog']) {
      expect(screen.getByText(text)).toBeInTheDocument()
    }
  })

  it('omits the rail when no rail slot is supplied', () => {
    render(<RecordShell variant="admin" body={<div>body</div>} />)
    expect(screen.getByText('body')).toBeInTheDocument()
    expect(screen.queryByText('rail')).toBeNull()
  })

  it('renders the section nav as a left column of the body grid when supplied', () => {
    render(
      <RecordShell
        variant="enduser"
        body={<div>body</div>}
        sectionNav={<div>nav</div>}
        rail={<div>rail</div>}
      />
    )
    const navCell = screen.getByTestId('record-shell-section-nav')
    expect(navCell).toHaveTextContent('nav')
    expect(navCell.parentElement).toHaveClass('lg:grid-cols-[280px_minmax(0,1fr)_340px]')
  })

  it('uses the two-column grid when the section nav is supplied without a rail', () => {
    render(<RecordShell variant="admin" body={<div>body</div>} sectionNav={<div>nav</div>} />)
    const navCell = screen.getByTestId('record-shell-section-nav')
    expect(navCell.parentElement).toHaveClass('lg:grid-cols-[280px_minmax(0,1fr)]')
  })

  it('omits the section nav cell when no sectionNav slot is supplied', () => {
    render(<RecordShell variant="enduser" body={<div>body</div>} rail={<div>rail</div>} />)
    expect(screen.queryByTestId('record-shell-section-nav')).toBeNull()
  })
})
