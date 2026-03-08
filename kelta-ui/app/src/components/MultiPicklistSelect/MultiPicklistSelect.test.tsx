import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { MultiPicklistSelect } from './MultiPicklistSelect'
import { normalizeMultiPicklistValue } from './utils'

const mockOptions = ['xs', 's', 'm', 'l', 'xl', 'xxl']

describe('normalizeMultiPicklistValue', () => {
  it('returns empty array for null', () => {
    expect(normalizeMultiPicklistValue(null)).toEqual([])
  })

  it('returns empty array for undefined', () => {
    expect(normalizeMultiPicklistValue(undefined)).toEqual([])
  })

  it('returns empty array for empty string', () => {
    expect(normalizeMultiPicklistValue('')).toEqual([])
  })

  it('returns empty array for empty JSON array string', () => {
    expect(normalizeMultiPicklistValue('[]')).toEqual([])
  })

  it('returns empty array for empty PostgreSQL array literal', () => {
    expect(normalizeMultiPicklistValue('{}')).toEqual([])
  })

  it('passes through a JavaScript array as-is', () => {
    expect(normalizeMultiPicklistValue(['s', 'm', 'l'])).toEqual(['s', 'm', 'l'])
  })

  it('filters non-string and empty string values from arrays', () => {
    expect(normalizeMultiPicklistValue(['s', '', null, 'm', 42])).toEqual(['s', 'm'])
  })

  it('parses a JSON array string', () => {
    expect(normalizeMultiPicklistValue('["s","m","l"]')).toEqual(['s', 'm', 'l'])
  })

  it('parses a PostgreSQL array literal', () => {
    expect(normalizeMultiPicklistValue('{s,m,l}')).toEqual(['s', 'm', 'l'])
  })

  it('parses a PostgreSQL array literal with quoted values', () => {
    expect(normalizeMultiPicklistValue('{"hello world","foo"}')).toEqual(['hello world', 'foo'])
  })

  it('parses a comma-separated string', () => {
    expect(normalizeMultiPicklistValue('s,m,l')).toEqual(['s', 'm', 'l'])
  })

  it('wraps a single value string into an array', () => {
    expect(normalizeMultiPicklistValue('m')).toEqual(['m'])
  })
})

describe('MultiPicklistSelect', () => {
  let onChange: (values: string[]) => void

  beforeEach(() => {
    onChange = vi.fn<(values: string[]) => void>()
  })

  describe('Rendering', () => {
    it('shows placeholder when no values selected', () => {
      render(
        <MultiPicklistSelect
          value={[]}
          options={mockOptions}
          onChange={onChange}
          placeholder="Select sizes..."
        />
      )
      expect(screen.getByText('Select sizes...')).toBeInTheDocument()
    })

    it('shows selected values as badges', () => {
      render(
        <MultiPicklistSelect value={['s', 'm', 'l']} options={mockOptions} onChange={onChange} />
      )
      expect(screen.getByText('s')).toBeInTheDocument()
      expect(screen.getByText('m')).toBeInTheDocument()
      expect(screen.getByText('l')).toBeInTheDocument()
    })

    it('does not show unselected values as badges', () => {
      render(<MultiPicklistSelect value={['s']} options={mockOptions} onChange={onChange} />)
      expect(screen.getByText('s')).toBeInTheDocument()
      // 'xl' should not appear in the trigger (only in dropdown when opened)
      const trigger = screen.getByRole('combobox')
      expect(trigger).not.toHaveTextContent('xl')
    })
  })

  describe('Opening and Closing', () => {
    it('opens dropdown on click', () => {
      render(<MultiPicklistSelect value={[]} options={mockOptions} onChange={onChange} />)
      const trigger = screen.getByRole('combobox')
      fireEvent.click(trigger)
      expect(screen.getByRole('listbox')).toBeInTheDocument()
    })

    it('does not open when disabled', () => {
      render(<MultiPicklistSelect value={[]} options={mockOptions} onChange={onChange} disabled />)
      const trigger = screen.getByRole('combobox')
      fireEvent.click(trigger)
      expect(screen.queryByRole('listbox')).not.toBeInTheDocument()
    })
  })

  describe('Selection', () => {
    it('calls onChange with added value when clicking unselected option', () => {
      render(<MultiPicklistSelect value={['s']} options={mockOptions} onChange={onChange} />)
      const trigger = screen.getByRole('combobox')
      fireEvent.click(trigger)

      const option = screen.getByText('m')
      fireEvent.click(option)

      expect(onChange).toHaveBeenCalledWith(['s', 'm'])
    })

    it('calls onChange with removed value when clicking selected option', () => {
      render(
        <MultiPicklistSelect value={['s', 'm', 'l']} options={mockOptions} onChange={onChange} />
      )
      const trigger = screen.getByRole('combobox')
      fireEvent.click(trigger)

      const option = screen.getByRole('option', { name: /m/ })
      fireEvent.click(option)

      expect(onChange).toHaveBeenCalledWith(['s', 'l'])
    })

    it('removes value when clicking the X on a badge', () => {
      render(<MultiPicklistSelect value={['s', 'm']} options={mockOptions} onChange={onChange} />)

      const removeButton = screen.getByLabelText('Remove s')
      fireEvent.click(removeButton)

      expect(onChange).toHaveBeenCalledWith(['m'])
    })
  })

  describe('Accessibility', () => {
    it('sets aria-multiselectable on listbox', () => {
      render(<MultiPicklistSelect value={[]} options={mockOptions} onChange={onChange} />)
      fireEvent.click(screen.getByRole('combobox'))
      expect(screen.getByRole('listbox')).toHaveAttribute('aria-multiselectable', 'true')
    })

    it('marks selected options with aria-selected=true', () => {
      render(<MultiPicklistSelect value={['s', 'l']} options={mockOptions} onChange={onChange} />)
      fireEvent.click(screen.getByRole('combobox'))

      const options = screen.getAllByRole('option')
      // 's' is at index 1 (xs=0, s=1)
      expect(options[1]).toHaveAttribute('aria-selected', 'true')
      // 'l' is at index 3 (xs=0, s=1, m=2, l=3)
      expect(options[3]).toHaveAttribute('aria-selected', 'true')
      // 'xs' is at index 0 — not selected
      expect(options[0]).toHaveAttribute('aria-selected', 'false')
    })
  })
})
