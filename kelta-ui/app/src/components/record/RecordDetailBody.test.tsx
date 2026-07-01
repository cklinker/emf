import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { RecordDetailBody } from './RecordDetailBody'
import type { LayoutSectionDto } from '@/hooks/usePageLayout'
import type { CollectionRecord } from '@/hooks/useCollectionRecords'

// Isolate the switch logic from the (heavy) real section renderer.
vi.mock('@/components/LayoutFieldSections/LayoutFieldSections', () => ({
  LayoutFieldSections: () => <div data-testid="layout-field-sections" />,
}))

const record: CollectionRecord = { id: 'rec-1', name: 'Ada' }

describe('RecordDetailBody', () => {
  it('renders LayoutFieldSections when the layout has sections', () => {
    const sections = [{ id: 's1' }] as unknown as LayoutSectionDto[]
    render(
      <RecordDetailBody
        sections={sections}
        schemaFields={[]}
        record={record}
        fallback={<div>fallback</div>}
      />
    )
    expect(screen.getByTestId('layout-field-sections')).toBeInTheDocument()
    expect(screen.queryByText('fallback')).toBeNull()
  })

  it('renders the fallback when there are no sections', () => {
    render(
      <RecordDetailBody
        sections={[]}
        schemaFields={[]}
        record={record}
        fallback={<div>fallback</div>}
      />
    )
    expect(screen.getByText('fallback')).toBeInTheDocument()
    expect(screen.queryByTestId('layout-field-sections')).toBeNull()
  })

  it('renders nothing when there are no sections and no fallback', () => {
    const { container } = render(
      <RecordDetailBody sections={undefined} schemaFields={[]} record={record} />
    )
    expect(container).toBeEmptyDOMElement()
    expect(screen.queryByTestId('layout-field-sections')).toBeNull()
  })
})
