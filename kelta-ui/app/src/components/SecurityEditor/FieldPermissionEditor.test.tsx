import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { FieldPermissionEditor } from './FieldPermissionEditor'

const collections = [{ id: 'c1', name: 'people' }]
const fields = [{ id: 'f1', name: 'ssn', type: 'string', collectionId: 'c1' }]

describe('FieldPermissionEditor', () => {
  it('offers MASKED as a visibility option', () => {
    render(
      <FieldPermissionEditor
        collections={collections}
        fields={fields}
        permissions={[{ fieldId: 'f1', visibility: 'VISIBLE' }]}
        onChange={vi.fn()}
      />
    )
    // The 4th visibility option (Masked) is rendered (per-field radio and/or bulk action).
    expect(screen.getAllByText('Masked').length).toBeGreaterThan(0)
  })

  it('counts masked fields in the summary', () => {
    render(
      <FieldPermissionEditor
        collections={collections}
        fields={fields}
        permissions={[{ fieldId: 'f1', visibility: 'MASKED' }]}
        onChange={vi.fn()}
        testId="fpe"
      />
    )
    expect(screen.getByTestId('fpe-summary').textContent).toContain('Masked: 1')
  })
})
