import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { FieldRenderer } from './FieldRenderer'

function renderField(props: React.ComponentProps<typeof FieldRenderer>) {
  return render(
    <MemoryRouter>
      <FieldRenderer {...props} />
    </MemoryRouter>
  )
}

describe('FieldRenderer', () => {
  describe('null/undefined values', () => {
    it('renders em dash for null value', () => {
      renderField({ type: 'string', value: null })
      expect(screen.getByText('—')).toBeDefined()
    })

    it('renders em dash for undefined value', () => {
      renderField({ type: 'string', value: undefined })
      expect(screen.getByText('—')).toBeDefined()
    })
  })

  describe('string type', () => {
    it('renders string value', () => {
      renderField({ type: 'string', value: 'Hello World' })
      expect(screen.getByText('Hello World')).toBeDefined()
    })

    it('truncates long strings with tooltip', () => {
      const longText = 'A'.repeat(150)
      renderField({ type: 'string', value: longText, truncate: true })
      expect(screen.getByText(`${'A'.repeat(100)}...`)).toBeDefined()
    })

    it('does not truncate when truncate is false', () => {
      const longText = 'A'.repeat(150)
      renderField({ type: 'string', value: longText, truncate: false })
      expect(screen.getByText(longText)).toBeDefined()
    })
  })

  describe('number type', () => {
    it('renders formatted number', () => {
      renderField({ type: 'number', value: 1234567 })
      // Formatted with locale-specific separators
      expect(screen.getByText(/1.*234.*567/)).toBeDefined()
    })

    it('renders zero', () => {
      renderField({ type: 'number', value: 0 })
      expect(screen.getByText('0')).toBeDefined()
    })
  })

  describe('boolean type', () => {
    it('renders check icon for true', () => {
      renderField({ type: 'boolean', value: true })
      expect(screen.getByText('Yes')).toBeDefined() // sr-only text
    })

    it('renders X icon for false', () => {
      renderField({ type: 'boolean', value: false })
      expect(screen.getByText('No')).toBeDefined() // sr-only text
    })
  })

  describe('date type', () => {
    it('renders formatted date', () => {
      renderField({ type: 'date', value: '2024-06-15' })
      // Should contain "Jun" and "2024" at minimum
      const el = screen.getByText(/Jun/)
      expect(el).toBeDefined()
    })

    it('renders empty string for invalid date', () => {
      const { container } = renderField({ type: 'date', value: '' })
      expect(container.textContent).toBe('')
    })
  })

  describe('currency type', () => {
    it('renders formatted currency value', () => {
      renderField({ type: 'currency', value: 1234.56 })
      expect(screen.getByText(/1.*234\.56/)).toBeDefined()
    })
  })

  describe('percent type', () => {
    it('renders value with percent sign', () => {
      renderField({ type: 'percent', value: 42.5 })
      expect(screen.getByText('42.50%')).toBeDefined()
    })
  })

  describe('email type', () => {
    it('renders email link with mailto', () => {
      renderField({ type: 'email', value: 'test@example.com' })
      const link = screen.getByText('test@example.com')
      expect(link.closest('a')?.getAttribute('href')).toBe('mailto:test@example.com')
    })
  })

  describe('phone type', () => {
    it('renders phone link with tel:', () => {
      renderField({ type: 'phone', value: '+1-555-0100' })
      const link = screen.getByText('+1-555-0100')
      expect(link.closest('a')?.getAttribute('href')).toBe('tel:+1-555-0100')
    })
  })

  describe('url type', () => {
    it('renders external link', () => {
      renderField({ type: 'url', value: 'https://example.com' })
      const link = screen.getByText('https://example.com')
      expect(link.closest('a')?.getAttribute('href')).toBe('https://example.com')
      expect(link.closest('a')?.getAttribute('target')).toBe('_blank')
    })

    it('prepends https:// for urls without protocol', () => {
      renderField({ type: 'url', value: 'example.com' })
      const link = screen.getByText('example.com')
      expect(link.closest('a')?.getAttribute('href')).toBe('https://example.com')
    })
  })

  describe('picklist type', () => {
    it('renders value as badge', () => {
      renderField({ type: 'picklist', value: 'Active' })
      expect(screen.getByText('Active')).toBeDefined()
    })
  })

  describe('multi_picklist type', () => {
    it('renders array values as multiple badges', () => {
      renderField({ type: 'multi_picklist', value: ['Red', 'Blue', 'Green'] })
      expect(screen.getByText('Red')).toBeDefined()
      expect(screen.getByText('Blue')).toBeDefined()
      expect(screen.getByText('Green')).toBeDefined()
    })
  })

  describe('reference/lookup/master_detail types', () => {
    it('renders display label when provided', () => {
      renderField({
        type: 'master_detail',
        value: 'uuid-123',
        displayLabel: 'Acme Corp',
      })
      expect(screen.getByText('Acme Corp')).toBeDefined()
    })

    it('renders link when tenantSlug and targetCollection provided', () => {
      renderField({
        type: 'master_detail',
        value: 'uuid-123',
        displayLabel: 'Acme Corp',
        tenantSlug: 'demo',
        targetCollection: 'accounts',
      })
      const link = screen.getByText('Acme Corp')
      expect(link.closest('a')?.getAttribute('href')).toBe('/demo/app/o/accounts/uuid-123')
    })

    it('falls back to ID when no display label', () => {
      renderField({ type: 'lookup', value: 'uuid-456' })
      expect(screen.getByText('uuid-456')).toBeDefined()
    })
  })

  describe('encrypted type', () => {
    it('renders masked value', () => {
      renderField({ type: 'encrypted', value: 'secret-value' })
      expect(screen.getByText('••••••••')).toBeDefined()
    })
  })

  describe('json type', () => {
    it('renders JSON preview', () => {
      renderField({ type: 'json', value: { key: 'value' } })
      expect(screen.getByText('{"key":"value"}')).toBeDefined()
    })

    it('truncates long JSON', () => {
      const bigObj = { longKey: 'A'.repeat(100) }
      renderField({ type: 'json', value: bigObj })
      // Should show truncated preview with "..."
      const codeElement = document.querySelector('code')
      expect(codeElement?.textContent).toContain('...')
    })
  })

  describe('geolocation type', () => {
    it('renders lat/long from object', () => {
      renderField({
        type: 'geolocation',
        value: { latitude: 30.2672, longitude: -97.7431 },
      })
      expect(screen.getByText(/30.2672.*-97.7431/)).toBeDefined()
    })

    it('renders string value as fallback', () => {
      renderField({ type: 'geolocation', value: '30.2672, -97.7431' })
      expect(screen.getByText('30.2672, -97.7431')).toBeDefined()
    })
  })

  describe('auto_number type', () => {
    it('renders with monospace styling', () => {
      renderField({ type: 'auto_number', value: 'INV-0001' })
      expect(screen.getByText('INV-0001')).toBeDefined()
    })
  })

  describe('rich_text type', () => {
    it('strips HTML tags', () => {
      renderField({
        type: 'rich_text',
        value: '<p>Hello <strong>World</strong></p>',
      })
      expect(screen.getByText('Hello World')).toBeDefined()
    })
  })

  describe('external_id type', () => {
    it('renders as plain string', () => {
      renderField({ type: 'external_id', value: 'EXT-12345' })
      expect(screen.getByText('EXT-12345')).toBeDefined()
    })
  })

  describe('formula type', () => {
    it('renders computed value', () => {
      renderField({ type: 'formula', value: '42' })
      expect(screen.getByText('42')).toBeDefined()
    })
  })

  describe('rollup_summary type', () => {
    it('renders formatted number', () => {
      renderField({ type: 'rollup_summary', value: 1500 })
      expect(screen.getByText(/1.*500/)).toBeDefined()
    })
  })
})
