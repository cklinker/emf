import React from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ApprovalProcessesPage } from './ApprovalProcessesPage'
import { createTestWrapper, setupAuthMocks, getMockAxiosInstance, resetMockAxios } from '../../test/testUtils'

vi.mock('../../components/FieldExpressionPicker', () => ({
  FieldExpressionPicker: vi.fn(
    ({
      open,
      mode,
      rootCollectionId,
      allowedTypes,
      onInsert,
      testId,
    }: {
      open: boolean
      mode?: string
      rootCollectionId: string | null
      allowedTypes?: string[]
      onInsert: (token: string) => void
      testId?: string
    }) => {
      if (!open) return null
      return (
        <div
          data-testid={testId ?? 'field-expression-picker'}
          data-mode={mode}
          data-collection-id={rootCollectionId ?? ''}
          data-allowed-types={allowedTypes?.join(',')}
        >
          <button onClick={() => onInsert('status')}>Insert status</button>
        </div>
      )
    }
  ),
}))

describe('ApprovalProcessesPage – FieldExpressionPicker adoption', () => {
  beforeEach(() => {
    setupAuthMocks()
    resetMockAxios()
    const mockAxios = getMockAxiosInstance()
    // listProcesses → empty list
    mockAxios.get.mockResolvedValue({ data: { data: [], metadata: { totalCount: 0 } } })
  })

  it('shows the Insert field button in the Create Approval Process form', async () => {
    const Wrapper = createTestWrapper()
    render(
      <Wrapper>
        <ApprovalProcessesPage />
      </Wrapper>
    )

    // Wait for the page to load (no spinner)
    await waitFor(() => expect(screen.queryByLabelText(/loading/i)).not.toBeInTheDocument(), {
      timeout: 3000,
    })

    // Open the create form
    await userEvent.click(screen.getByTestId('add-approval-process-button'))

    expect(screen.getByTestId('entry-criteria-insert-field')).toBeInTheDocument()
  })

  it('opens the picker with mode=expression when Insert field is clicked', async () => {
    const Wrapper = createTestWrapper()
    render(
      <Wrapper>
        <ApprovalProcessesPage />
      </Wrapper>
    )

    await waitFor(() => expect(screen.queryByLabelText(/loading/i)).not.toBeInTheDocument(), {
      timeout: 3000,
    })

    await userEvent.click(screen.getByTestId('add-approval-process-button'))
    await userEvent.click(screen.getByTestId('entry-criteria-insert-field'))

    const picker = screen.getByTestId('approval-entry-criteria-picker')
    expect(picker).toBeInTheDocument()
    expect(picker).toHaveAttribute('data-mode', 'expression')
  })

  it('uses the form collectionId as rootCollectionId', async () => {
    const Wrapper = createTestWrapper()
    render(
      <Wrapper>
        <ApprovalProcessesPage />
      </Wrapper>
    )

    await waitFor(() => expect(screen.queryByLabelText(/loading/i)).not.toBeInTheDocument(), {
      timeout: 3000,
    })

    await userEvent.click(screen.getByTestId('add-approval-process-button'))

    // Type a collection id into the collection field
    const collectionInput = screen.getByTestId('approval-process-collection-id-input')
    await userEvent.clear(collectionInput)
    await userEvent.type(collectionInput, 'col-invoices')

    await userEvent.click(screen.getByTestId('entry-criteria-insert-field'))

    expect(screen.getByTestId('approval-entry-criteria-picker')).toHaveAttribute(
      'data-collection-id',
      'col-invoices'
    )
  })

  it('inserts the token into the entry criteria textarea', async () => {
    const Wrapper = createTestWrapper()
    render(
      <Wrapper>
        <ApprovalProcessesPage />
      </Wrapper>
    )

    await waitFor(() => expect(screen.queryByLabelText(/loading/i)).not.toBeInTheDocument(), {
      timeout: 3000,
    })

    await userEvent.click(screen.getByTestId('add-approval-process-button'))
    await userEvent.click(screen.getByTestId('entry-criteria-insert-field'))
    await userEvent.click(screen.getByText('Insert status'))

    const textarea = screen.getByTestId(
      'approval-process-entry-criteria-input'
    ) as HTMLTextAreaElement
    expect(textarea.value).toBe('status')
  })
})
