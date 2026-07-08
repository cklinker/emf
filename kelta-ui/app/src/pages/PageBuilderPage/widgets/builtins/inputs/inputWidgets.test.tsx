/**
 * Per-input tests (slice 2f): each standalone typed-input widget maps its bound field's `FieldType` to
 * the correct control, seeds from the already-resolved `defaultValue`, renders disabled in editor mode,
 * shows the i18n empty state when unconfigured, and (rich-text) SANITIZES HTML-bearing display output.
 *
 * `useFieldDef` / option hooks are mocked so these tests assert the control mapping without MSW; the
 * picklist endpoint resolution itself is covered by `usePicklistOptions.test.ts`.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { I18nProvider } from '@/context/I18nContext'
import type { FieldDefinition } from '@/hooks/useCollectionSchema'

// --- mocks -----------------------------------------------------------------
const fieldDefHolder = vi.hoisted(() => ({ current: undefined as FieldDefinition | undefined }))
vi.mock('./useFieldDef', () => ({
  useFieldDef: () => ({ fieldDef: fieldDefHolder.current, isLoading: false }),
}))
vi.mock('@/hooks/usePicklistOptions', () => ({
  usePicklistOptions: () => ({ options: ['Open', 'Closed'], isLoading: false }),
  resolvePicklistSource: () => ({ sourceId: 'x', sourceType: 'FIELD' }),
}))
vi.mock('./useLookupOptions', () => ({
  useLookupOptions: () => ({
    options: [{ id: 'a', label: 'Acme' }],
    isLoading: false,
  }),
}))

import { RenderTree } from '../../renderTree'
import { registerBuiltinWidgets } from '../index'
import type { RenderNode } from '../../types'

function field(
  partial: Partial<FieldDefinition> & { type: FieldDefinition['type'] }
): FieldDefinition {
  return {
    id: 'f1',
    name: 'fld',
    required: false,
    ...partial,
  }
}

function renderInput(node: RenderNode, mode: 'editor' | 'runtime' = 'runtime') {
  return render(
    <I18nProvider>
      <RenderTree components={[node]} tenantSlug="acme" mode={mode} />
    </I18nProvider>
  )
}

beforeEach(() => {
  registerBuiltinWidgets()
  fieldDefHolder.current = undefined
})

describe('text-input', () => {
  it('renders an email input for an email field, seeded from the resolved default', () => {
    fieldDefHolder.current = field({ type: 'email', name: 'email' })
    renderInput({
      id: 'i',
      type: 'text-input',
      props: { collection: 'orders', field: 'email', defaultValue: 'ada@example.com' },
    })
    const input = screen.getByTestId('page-input-text').querySelector('input')!
    expect(input.type).toBe('email')
    expect(input.value).toBe('ada@example.com')
  })

  it('renders a url input for a url field', () => {
    fieldDefHolder.current = field({ type: 'url', name: 'site' })
    renderInput({ id: 'i', type: 'text-input', props: { collection: 'orders', field: 'site' } })
    expect(screen.getByTestId('page-input-text').querySelector('input')!.type).toBe('url')
  })

  it('renders disabled for an auto_number field', () => {
    fieldDefHolder.current = field({ type: 'auto_number', name: 'num' })
    renderInput({ id: 'i', type: 'text-input', props: { collection: 'orders', field: 'num' } })
    expect(screen.getByTestId('page-input-text').querySelector('input')!.disabled).toBe(true)
  })
})

describe('number-input', () => {
  it('renders a number input; currency adds step="0.01"', () => {
    fieldDefHolder.current = field({ type: 'currency', name: 'total' })
    renderInput({
      id: 'i',
      type: 'number-input',
      props: { collection: 'orders', field: 'total', defaultValue: 12.5 },
    })
    const input = screen.getByTestId('page-input-number').querySelector('input')!
    expect(input.type).toBe('number')
    expect(input.step).toBe('0.01')
    expect(input.value).toBe('12.5')
  })
})

describe('checkbox', () => {
  it('maps boolean to a Checkbox, seeded from the resolved default', () => {
    fieldDefHolder.current = field({ type: 'boolean', name: 'flag' })
    renderInput({
      id: 'i',
      type: 'checkbox',
      props: { collection: 'orders', field: 'flag', defaultValue: true },
    })
    const box = screen.getByTestId('page-input-checkbox').querySelector('[role="checkbox"]')!
    expect(box.getAttribute('data-state')).toBe('checked')
  })
})

describe('dropdown', () => {
  it('renders a native select with one option per picklist value (+ Select… placeholder)', () => {
    fieldDefHolder.current = field({ type: 'picklist', name: 'status' })
    renderInput({
      id: 'i',
      type: 'dropdown',
      props: { collection: 'orders', field: 'status', defaultValue: 'Open' },
    })
    const select = screen.getByTestId('page-input-dropdown').querySelector('select')!
    expect(select.tagName).toBe('SELECT')
    const options = Array.from(select.querySelectorAll('option')).map((o) => o.textContent)
    expect(options).toEqual(['Select…', 'Open', 'Closed'])
    expect(select.value).toBe('Open')
  })
})

describe('datepicker', () => {
  it('maps date → input[type=date] and datetime → datetime-local', () => {
    fieldDefHolder.current = field({ type: 'date', name: 'due' })
    const { unmount } = renderInput({
      id: 'i',
      type: 'datepicker',
      props: { collection: 'orders', field: 'due' },
    })
    expect(screen.getByTestId('page-input-date').querySelector('input')!.type).toBe('date')
    unmount()

    fieldDefHolder.current = field({ type: 'datetime', name: 'at' })
    renderInput({ id: 'i', type: 'datepicker', props: { collection: 'orders', field: 'at' } })
    expect(screen.getByTestId('page-input-date').querySelector('input')!.type).toBe(
      'datetime-local'
    )
  })
})

describe('lookup', () => {
  it('maps a reference field to a LookupSelect trigger', () => {
    fieldDefHolder.current = field({
      type: 'master_detail',
      name: 'customer',
      referenceTarget: 'accounts',
    })
    renderInput({ id: 'i', type: 'lookup', props: { collection: 'orders', field: 'customer' } })
    expect(screen.getByTestId('page-input-lookup-select-trigger')).toBeInTheDocument()
  })
})

describe('multi-picklist', () => {
  it('maps multi_picklist to a MultiPicklistSelect with the picklist options', () => {
    fieldDefHolder.current = field({ type: 'multi_picklist', name: 'tags' })
    renderInput({ id: 'i', type: 'multi-picklist', props: { collection: 'orders', field: 'tags' } })
    expect(screen.getByTestId('page-input-multipicklist')).toBeInTheDocument()
  })
})

describe('rich-text', () => {
  it('renders the RichTextEditor in runtime mode', () => {
    fieldDefHolder.current = field({ type: 'rich_text', name: 'notes' })
    renderInput({ id: 'i', type: 'rich-text', props: { collection: 'orders', field: 'notes' } })
    expect(screen.getByTestId('page-input-richtext')).toBeInTheDocument()
    expect(screen.getByTestId('page-input-richtext-editor')).toBeInTheDocument()
  })

  it('renders SANITIZED display (no script/onerror) in editor preview mode', () => {
    fieldDefHolder.current = field({ type: 'rich_text', name: 'notes' })
    const hostile = '<b>ok</b><img src=x onerror="alert(1)"><script>alert(2)</script>'
    const { container } = renderInput(
      {
        id: 'i',
        type: 'rich-text',
        props: { collection: 'orders', field: 'notes', defaultValue: hostile },
      },
      'editor'
    )
    const display = screen.getByTestId('page-input-richtext-display')
    // The same FieldRenderer rich_text sanitizer strips ALL tags → no executable HTML reaches the DOM.
    expect(display.querySelector('img')).toBeNull()
    expect(display.querySelector('script')).toBeNull()
    expect(container.innerHTML).not.toContain('onerror')
    expect(container.innerHTML).not.toContain('<script')
    // Benign text content survives (tags stripped to text).
    expect(display).toHaveTextContent('ok')
  })
})

describe('shared behaviour', () => {
  it('shows the i18n empty state when no field is configured', () => {
    renderInput({ id: 'i', type: 'dropdown', props: { collection: '', field: '' } })
    expect(screen.getByTestId('page-input-dropdown')).toHaveTextContent(/no field configured/i)
  })

  it('renders disabled in editor mode (preview only)', () => {
    fieldDefHolder.current = field({ type: 'picklist', name: 'status' })
    renderInput(
      { id: 'i', type: 'dropdown', props: { collection: 'orders', field: 'status' } },
      'editor'
    )
    expect(screen.getByTestId('page-input-dropdown').querySelector('select')!.disabled).toBe(true)
  })

  it('surfaces the advisory required marker', () => {
    fieldDefHolder.current = field({ type: 'string', name: 'name' })
    renderInput({
      id: 'i',
      type: 'text-input',
      props: { collection: 'orders', field: 'name', required: true },
    })
    expect(screen.getByLabelText('Required')).toBeInTheDocument()
  })
})
