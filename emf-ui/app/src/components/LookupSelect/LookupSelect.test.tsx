import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { LookupSelect } from './LookupSelect'
import type { LookupOption } from './LookupSelect'

const mockOptions: LookupOption[] = [
  { id: 'id-1', label: 'T-Shirts' },
  { id: 'id-2', label: 'Jeans' },
  { id: 'id-3', label: 'Dresses' },
  { id: 'id-4', label: 'Jackets' },
  { id: 'id-5', label: 'Sneakers' },
]

describe('LookupSelect', () => {
  let onChange: ReturnType<typeof vi.fn>

  beforeEach(() => {
    onChange = vi.fn()
  })

  describe('Rendering', () => {
    it('renders with placeholder when no value selected', () => {
      render(
        <LookupSelect
          value=""
          options={mockOptions}
          onChange={onChange}
          placeholder="Select category..."
          data-testid="lookup"
        />
      )
      expect(screen.getByText('Select category...')).toBeInTheDocument()
    })

    it('renders selected option label when value matches', () => {
      render(
        <LookupSelect value="id-2" options={mockOptions} onChange={onChange} data-testid="lookup" />
      )
      expect(screen.getByText('Jeans')).toBeInTheDocument()
    })

    it('shows raw ID when value does not match any option', () => {
      render(
        <LookupSelect
          value="unknown-id-999"
          options={mockOptions}
          onChange={onChange}
          data-testid="lookup"
        />
      )
      expect(screen.getByText('unknown-id-999')).toBeInTheDocument()
    })

    it('renders with disabled state', () => {
      render(
        <LookupSelect
          value=""
          options={mockOptions}
          onChange={onChange}
          disabled
          data-testid="lookup"
        />
      )
      expect(screen.getByTestId('lookup-trigger')).toBeDisabled()
    })
  })

  describe('Dropdown', () => {
    it('opens dropdown on click', async () => {
      render(
        <LookupSelect value="" options={mockOptions} onChange={onChange} data-testid="lookup" />
      )
      await userEvent.click(screen.getByTestId('lookup-trigger'))
      expect(screen.getByTestId('lookup-dropdown')).toBeInTheDocument()
      expect(screen.getByTestId('lookup-search')).toBeInTheDocument()
    })

    it('shows all options when opened', async () => {
      render(
        <LookupSelect value="" options={mockOptions} onChange={onChange} data-testid="lookup" />
      )
      await userEvent.click(screen.getByTestId('lookup-trigger'))
      const dropdown = screen.getByTestId('lookup-dropdown')
      mockOptions.forEach((opt) => {
        expect(within(dropdown).getByText(opt.label)).toBeInTheDocument()
      })
    })

    it('does not open when disabled', async () => {
      render(
        <LookupSelect
          value=""
          options={mockOptions}
          onChange={onChange}
          disabled
          data-testid="lookup"
        />
      )
      await userEvent.click(screen.getByTestId('lookup-trigger'))
      expect(screen.queryByTestId('lookup-dropdown')).not.toBeInTheDocument()
    })

    it('closes dropdown on Escape key', async () => {
      render(
        <LookupSelect value="" options={mockOptions} onChange={onChange} data-testid="lookup" />
      )
      await userEvent.click(screen.getByTestId('lookup-trigger'))
      expect(screen.getByTestId('lookup-dropdown')).toBeInTheDocument()

      fireEvent.keyDown(screen.getByTestId('lookup'), { key: 'Escape' })
      expect(screen.queryByTestId('lookup-dropdown')).not.toBeInTheDocument()
    })

    it('closes dropdown on click outside', async () => {
      render(
        <div>
          <div data-testid="outside">Outside</div>
          <LookupSelect value="" options={mockOptions} onChange={onChange} data-testid="lookup" />
        </div>
      )
      await userEvent.click(screen.getByTestId('lookup-trigger'))
      expect(screen.getByTestId('lookup-dropdown')).toBeInTheDocument()

      fireEvent.mouseDown(screen.getByTestId('outside'))
      expect(screen.queryByTestId('lookup-dropdown')).not.toBeInTheDocument()
    })
  })

  describe('Search', () => {
    it('filters options by search text', async () => {
      render(
        <LookupSelect value="" options={mockOptions} onChange={onChange} data-testid="lookup" />
      )
      await userEvent.click(screen.getByTestId('lookup-trigger'))

      const searchInput = screen.getByTestId('lookup-search')
      await userEvent.type(searchInput, 'dress')

      const dropdown = screen.getByTestId('lookup-dropdown')
      expect(within(dropdown).getByText('Dresses')).toBeInTheDocument()
      expect(within(dropdown).queryByText('T-Shirts')).not.toBeInTheDocument()
      expect(within(dropdown).queryByText('Jeans')).not.toBeInTheDocument()
    })

    it('shows no results message when search has no matches', async () => {
      render(
        <LookupSelect value="" options={mockOptions} onChange={onChange} data-testid="lookup" />
      )
      await userEvent.click(screen.getByTestId('lookup-trigger'))

      const searchInput = screen.getByTestId('lookup-search')
      await userEvent.type(searchInput, 'zzzzzzz')

      expect(screen.getByText('No records found')).toBeInTheDocument()
    })

    it('search is case-insensitive', async () => {
      render(
        <LookupSelect value="" options={mockOptions} onChange={onChange} data-testid="lookup" />
      )
      await userEvent.click(screen.getByTestId('lookup-trigger'))

      const searchInput = screen.getByTestId('lookup-search')
      await userEvent.type(searchInput, 'JACK')

      const dropdown = screen.getByTestId('lookup-dropdown')
      expect(within(dropdown).getByText('Jackets')).toBeInTheDocument()
    })
  })

  describe('Selection', () => {
    it('selects option on click and calls onChange with ID', async () => {
      render(
        <LookupSelect value="" options={mockOptions} onChange={onChange} data-testid="lookup" />
      )
      await userEvent.click(screen.getByTestId('lookup-trigger'))
      await userEvent.click(screen.getByText('Dresses'))

      expect(onChange).toHaveBeenCalledWith('id-3')
      // Dropdown should close after selection
      expect(screen.queryByTestId('lookup-dropdown')).not.toBeInTheDocument()
    })

    it('clears selection when clear button is clicked', async () => {
      render(
        <LookupSelect value="id-1" options={mockOptions} onChange={onChange} data-testid="lookup" />
      )
      await userEvent.click(screen.getByTestId('lookup-clear'))
      expect(onChange).toHaveBeenCalledWith('')
    })

    it('does not show clear button when required', () => {
      render(
        <LookupSelect
          value="id-1"
          options={mockOptions}
          onChange={onChange}
          required
          data-testid="lookup"
        />
      )
      expect(screen.queryByTestId('lookup-clear')).not.toBeInTheDocument()
    })

    it('does not show clear button when no value selected', () => {
      render(
        <LookupSelect value="" options={mockOptions} onChange={onChange} data-testid="lookup" />
      )
      expect(screen.queryByTestId('lookup-clear')).not.toBeInTheDocument()
    })
  })

  describe('Keyboard Navigation', () => {
    it('opens dropdown with Enter key', async () => {
      render(
        <LookupSelect value="" options={mockOptions} onChange={onChange} data-testid="lookup" />
      )
      const trigger = screen.getByTestId('lookup-trigger')
      trigger.focus()
      fireEvent.keyDown(screen.getByTestId('lookup'), { key: 'Enter' })
      expect(screen.getByTestId('lookup-dropdown')).toBeInTheDocument()
    })

    it('opens dropdown with ArrowDown key', async () => {
      render(
        <LookupSelect value="" options={mockOptions} onChange={onChange} data-testid="lookup" />
      )
      const trigger = screen.getByTestId('lookup-trigger')
      trigger.focus()
      fireEvent.keyDown(screen.getByTestId('lookup'), { key: 'ArrowDown' })
      expect(screen.getByTestId('lookup-dropdown')).toBeInTheDocument()
    })

    it('navigates options with ArrowDown and selects with Enter', async () => {
      render(
        <LookupSelect value="" options={mockOptions} onChange={onChange} data-testid="lookup" />
      )
      await userEvent.click(screen.getByTestId('lookup-trigger'))

      // Arrow down to first option
      fireEvent.keyDown(screen.getByTestId('lookup'), { key: 'ArrowDown' })
      // Arrow down to second option
      fireEvent.keyDown(screen.getByTestId('lookup'), { key: 'ArrowDown' })
      // Select it
      fireEvent.keyDown(screen.getByTestId('lookup'), { key: 'Enter' })

      expect(onChange).toHaveBeenCalledWith('id-2')
    })
  })

  describe('Empty options', () => {
    it('shows no results when options array is empty', async () => {
      render(<LookupSelect value="" options={[]} onChange={onChange} data-testid="lookup" />)
      await userEvent.click(screen.getByTestId('lookup-trigger'))
      expect(screen.getByText('No records found')).toBeInTheDocument()
    })
  })

  describe('ARIA', () => {
    it('has correct aria attributes on trigger', () => {
      render(
        <LookupSelect value="" options={mockOptions} onChange={onChange} data-testid="lookup" />
      )
      const trigger = screen.getByTestId('lookup-trigger')
      expect(trigger).toHaveAttribute('aria-haspopup', 'listbox')
      expect(trigger).toHaveAttribute('aria-expanded', 'false')
    })

    it('updates aria-expanded when opened', async () => {
      render(
        <LookupSelect value="" options={mockOptions} onChange={onChange} data-testid="lookup" />
      )
      const trigger = screen.getByTestId('lookup-trigger')
      await userEvent.click(trigger)
      expect(trigger).toHaveAttribute('aria-expanded', 'true')
    })

    it('options have correct role and aria-selected', async () => {
      render(
        <LookupSelect value="id-2" options={mockOptions} onChange={onChange} data-testid="lookup" />
      )
      await userEvent.click(screen.getByTestId('lookup-trigger'))

      const options = screen.getAllByRole('option')
      const jeansOption = options.find((el) => el.textContent === 'Jeans')
      const tshirtOption = options.find((el) => el.textContent === 'T-Shirts')

      expect(jeansOption).toHaveAttribute('aria-selected', 'true')
      expect(tshirtOption).toHaveAttribute('aria-selected', 'false')
    })
  })
})
