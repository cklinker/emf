/**
 * `form` widget tests (slice 2f). The runtime branch renders @kelta/components' ResourceForm bound to
 * the configured collection; the editor branch renders the static `page-node-form` placeholder. The
 * full typed-submit / Zod / authz behaviour lives in @kelta/components' own ResourceForm.test.tsx — here
 * we assert the WIDGET WIRING (resolved props → ResourceForm; onSave toast; registry-backed field
 * renderers; placeholder/empty states) so the page-builder contract holds without re-testing ResourceForm.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { I18nProvider } from '@/context/I18nContext'

// Capture the props ResourceForm is mounted with, and the field-renderer registry it gets installed.
// A hoisted holder avoids the TDZ that a bare `let` referenced inside the mock factory would hit.
const holder = vi.hoisted(() => ({
  resourceFormProps: vi.fn(),
  registry: null as {
    hasFieldRenderer: (t: string) => boolean
    getFieldRenderer: (t: string) => unknown
  } | null,
}))

vi.mock('@kelta/components', () => ({
  ResourceForm: (props: Record<string, unknown>) => {
    holder.resourceFormProps(props)
    return (
      <button
        type="button"
        data-testid="resource-form"
        data-resource={props.resourceName as string}
        data-record={(props.recordId as string) ?? ''}
        data-readonly={String(props.readOnly === true)}
        onClick={() => (props.onSave as (d: unknown) => void)?.({ id: 'new' })}
      >
        form
      </button>
    )
  },
  setComponentRegistry: (reg: typeof holder.registry) => {
    holder.registry = reg
  },
  getComponentRegistry: () => holder.registry,
}))

const toastSuccess = vi.fn()
vi.mock('sonner', () => ({ toast: { success: (m: string) => toastSuccess(m) } }))

// usePicklistOptions/useLookupOptions reach for useApi — stub it (the registry renderers may mount).
vi.mock('@/context/ApiContext', () => ({
  useApi: () => ({ apiClient: { getList: vi.fn().mockResolvedValue([]) } }),
}))

import { RenderTree } from '../renderTree'
import { registerBuiltinWidgets } from './index'
import type { RenderNode } from '../types'

function renderTree(nodes: RenderNode[], mode: 'editor' | 'runtime' = 'runtime') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <I18nProvider>
        <RenderTree components={nodes} tenantSlug="acme" mode={mode} />
      </I18nProvider>
    </QueryClientProvider>
  )
}

describe('form widget (slice 2f)', () => {
  beforeEach(() => {
    registerBuiltinWidgets()
    holder.resourceFormProps.mockClear()
    toastSuccess.mockClear()
  })

  it('mounts ResourceForm bound to the configured collection in runtime mode', () => {
    renderTree([{ id: 'f', type: 'form', props: { dataView: { collection: 'orders' } } }])
    const form = screen.getByTestId('resource-form')
    expect(form).toHaveAttribute('data-resource', 'orders')
    expect(screen.getByTestId('page-node-form')).toBeInTheDocument()
  })

  it('passes the resolved recordId + readOnly through to ResourceForm (edit mode)', () => {
    renderTree([
      {
        id: 'f',
        type: 'form',
        // recordId arrives ALREADY resolved (resolved-node invariant) — a plain string here.
        props: { dataView: { collection: 'orders' }, recordId: 'rec-1', readOnly: true },
      },
    ])
    const form = screen.getByTestId('resource-form')
    expect(form).toHaveAttribute('data-record', 'rec-1')
    expect(form).toHaveAttribute('data-readonly', 'true')
  })

  it('fires the default success toast on save (placeholder until 2e wires onSubmit)', async () => {
    const user = (await import('@testing-library/user-event')).default.setup()
    renderTree([{ id: 'f', type: 'form', props: { dataView: { collection: 'orders' } } }])
    await user.click(screen.getByTestId('resource-form'))
    await waitFor(() => expect(toastSuccess).toHaveBeenCalledWith('Saved'))
  })

  it('renders the static placeholder in editor mode (no ResourceForm mount)', () => {
    renderTree([{ id: 'f', type: 'form', props: { dataView: { collection: 'orders' } } }], 'editor')
    expect(screen.getByTestId('page-node-form')).toHaveTextContent('Form')
    expect(screen.queryByTestId('resource-form')).not.toBeInTheDocument()
  })

  it('shows the "no data source" empty state when the collection is unset (runtime)', () => {
    renderTree([{ id: 'f', type: 'form', props: {} }])
    expect(screen.getByTestId('page-node-form')).toHaveTextContent(/no data source/i)
    expect(screen.queryByTestId('resource-form')).not.toBeInTheDocument()
  })

  it('installs rich field renderers into ResourceForm for picklist/lookup/multi/rich-text', () => {
    // registerBuiltinWidgets() → registerFormFieldRenderers() → setComponentRegistry(...) ran on import.
    expect(holder.registry).not.toBeNull()
    const reg = holder.registry!
    for (const t of [
      'picklist',
      'multi_picklist',
      'reference',
      'lookup',
      'master_detail',
      'rich_text',
    ]) {
      expect(reg.hasFieldRenderer(t)).toBe(true)
      expect(reg.getFieldRenderer(t)).toBeTypeOf('function')
    }
    // A plain text field is NOT overridden (ResourceForm renders its default input).
    expect(reg.hasFieldRenderer('string')).toBe(false)
  })
})
