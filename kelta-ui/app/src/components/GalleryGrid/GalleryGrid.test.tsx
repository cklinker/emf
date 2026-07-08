import React from 'react'
import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { GalleryGrid } from './GalleryGrid'
import type { FieldDefinition } from '@/hooks/useCollectionSchema'
import type { CollectionRecord } from '@/hooks/useCollectionRecords'

const imageField: FieldDefinition = {
  id: 'f-photo',
  name: 'photo',
  displayName: 'Photo',
  type: 'url',
  required: false,
}
const cardFields: FieldDefinition[] = [
  { id: 'f-name', name: 'name', displayName: 'Name', type: 'string', required: false },
  { id: 'f-price', name: 'price', displayName: 'Price', type: 'currency', required: false },
  { id: 'f-a', name: 'a', displayName: 'A', type: 'string', required: false },
  { id: 'f-b', name: 'b', displayName: 'B', type: 'string', required: false },
  { id: 'f-c', name: 'c', displayName: 'C', type: 'string', required: false },
  { id: 'f-d', name: 'd', displayName: 'D', type: 'string', required: false },
]

const records: CollectionRecord[] = [
  {
    id: 'r1',
    name: 'Widget',
    price: 9.5,
    photo: 'https://example.com/w.png',
    a: 1,
    b: 2,
    c: 3,
    d: 4,
  },
  { id: 'r2', name: 'Gadget', price: 12, photo: 'not-a-url' },
  { id: 'r3', name: '', price: 1, photo: null },
]

function renderGrid(props: Partial<React.ComponentProps<typeof GalleryGrid>> = {}) {
  return render(
    <MemoryRouter>
      <GalleryGrid
        records={records}
        imageField={imageField}
        titleField="name"
        cardFields={cardFields}
        onCardClick={vi.fn()}
        {...props}
      />
    </MemoryRouter>
  )
}

describe('GalleryGrid', () => {
  it('renders a card per record with the title', () => {
    renderGrid()
    expect(screen.getByText('Widget')).toBeInTheDocument()
    expect(screen.getByText('Gadget')).toBeInTheDocument()
    // Empty title falls back to the record id
    expect(within(screen.getByTestId('gallery-card-r3')).getByText('r3')).toBeInTheDocument()
  })

  it('renders an image only for http(s) URLs, placeholder otherwise', () => {
    renderGrid()
    const withImage = screen.getByTestId('gallery-card-r1')
    expect(within(withImage).getByTestId('gallery-image')).toHaveAttribute(
      'src',
      'https://example.com/w.png'
    )
    const nonUrl = screen.getByTestId('gallery-card-r2')
    expect(within(nonUrl).queryByTestId('gallery-image')).toBeNull()
    expect(within(nonUrl).getByTestId('gallery-placeholder').textContent).toBe('G')
  })

  it('caps card body fields at four and excludes the title field', () => {
    renderGrid()
    const card = screen.getByTestId('gallery-card-r1')
    // Title field 'name' excluded; next 4 of price/a/b/c shown, 'd' dropped
    expect(within(card).getByText('Price')).toBeInTheDocument()
    expect(within(card).getByText('C')).toBeInTheDocument()
    expect(within(card).queryByText('D')).toBeNull()
    expect(within(card).queryByText('Name')).toBeNull()
  })

  it('fires onCardClick', () => {
    const onCardClick = vi.fn()
    renderGrid({ onCardClick })
    fireEvent.click(screen.getByTestId('gallery-card-r1'))
    expect(onCardClick).toHaveBeenCalledWith(records[0])
  })

  it('shows an empty state without records', () => {
    renderGrid({ records: [] })
    expect(screen.getByTestId('gallery-empty')).toBeInTheDocument()
  })
})
