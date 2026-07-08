import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { ApprovalActionDialog } from './ApprovalActionDialog'
import { I18nProvider } from '../../context/I18nContext'

function renderDialog(props: Partial<Parameters<typeof ApprovalActionDialog>[0]> = {}) {
  const onConfirm = vi.fn()
  const onCancel = vi.fn()
  render(
    <I18nProvider>
      <ApprovalActionDialog
        open
        mode="approve"
        onConfirm={onConfirm}
        onCancel={onCancel}
        {...props}
      />
    </I18nProvider>
  )
  return { onConfirm, onCancel }
}

describe('ApprovalActionDialog', () => {
  it('passes the trimmed comment to onConfirm', () => {
    const { onConfirm } = renderDialog()
    fireEvent.change(screen.getByTestId('approval-comment'), {
      target: { value: '  looks good  ' },
    })
    fireEvent.click(screen.getByTestId('approval-confirm'))
    expect(onConfirm).toHaveBeenCalledWith('looks good')
  })

  it('passes undefined when the comment is empty', () => {
    const { onConfirm } = renderDialog()
    fireEvent.click(screen.getByTestId('approval-confirm'))
    expect(onConfirm).toHaveBeenCalledWith(undefined)
  })

  it('disables the confirm button while pending', () => {
    const { onConfirm } = renderDialog({ isPending: true })
    fireEvent.click(screen.getByTestId('approval-confirm'))
    expect(onConfirm).not.toHaveBeenCalled()
  })

  it('shows the destructive action label in reject mode', () => {
    renderDialog({ mode: 'reject' })
    expect(screen.getByTestId('approval-confirm').textContent).toContain('Reject')
  })
})
