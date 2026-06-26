import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { I18nProvider } from '@/context/I18nContext'
import { AccessSection } from './AccessSection'

function renderSection(requiredPermission: string | undefined, onChange = vi.fn()) {
  render(
    <I18nProvider>
      <AccessSection requiredPermission={requiredPermission} onChange={onChange} />
    </I18nProvider>
  )
  return onChange
}

describe('AccessSection', () => {
  it('defaults to "Anyone (published)" when no permission is set', () => {
    renderSection(undefined)
    const select = screen.getByTestId('page-access-select') as HTMLSelectElement
    expect(select.value).toBe('__anyone__')
  })

  it('reflects a set permission', () => {
    renderSection('VIEW_SETUP')
    const select = screen.getByTestId('page-access-select') as HTMLSelectElement
    expect(select.value).toBe('VIEW_SETUP')
    // The catalog options are present.
    expect(screen.getByRole('option', { name: 'View Setup' })).toBeInTheDocument()
  })

  it('writes the selected permission name', () => {
    const onChange = renderSection(undefined)
    fireEvent.change(screen.getByTestId('page-access-select'), {
      target: { value: 'MANAGE_USERS' },
    })
    expect(onChange).toHaveBeenCalledWith('MANAGE_USERS')
  })

  it('clears to undefined when "Anyone (published)" is chosen', () => {
    const onChange = renderSection('VIEW_SETUP')
    fireEvent.change(screen.getByTestId('page-access-select'), { target: { value: '__anyone__' } })
    expect(onChange).toHaveBeenCalledWith(undefined)
  })
})
