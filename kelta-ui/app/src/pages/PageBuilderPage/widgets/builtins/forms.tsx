/**
 * The `form` typed-input widget (slice 2f).
 *
 * In `mode:'runtime'` the `form` widget renders through `@kelta/components`' `ResourceForm` — schema
 * driven, Zod-validated, with field-level authz and a pluggable field-renderer registry. The richer
 * picklist / lookup / multi-picklist / rich-text controls are wired in via `setComponentRegistry`
 * (see `registerFormFieldRenderers.ts`) so the `form` widget is as typed as the standalone inputs,
 * WITHOUT forking `ResourceForm`. Submission stays on the authorized JSON:API path (Cerbos + write-FLS
 * server-side; the worker validates required/type/unique).
 *
 * In `mode:'editor'` it renders the SAME static placeholder 2a established (`page-node-form`), so the
 * canvas needs no live data and the 2a golden snapshot stays stable.
 */
/* eslint-disable react-refresh/only-export-components -- widget-descriptor module, not an HMR component file */
import React from 'react'
import { FileEdit } from 'lucide-react'
import { ResourceForm } from '@kelta/components'
import { useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { useI18n } from '@/context/I18nContext'
import type { WidgetDescriptor, WidgetRenderProps } from '../types'
import { asString } from '../util'

/** Read `props.dataView.collection` (shared shape with the `table` widget). */
function readFormCollection(props: Record<string, unknown> | undefined): string | undefined {
  const dv = props?.dataView
  if (dv && typeof dv === 'object') {
    return asString((dv as Record<string, unknown>).collection) || undefined
  }
  return undefined
}

/** Static editor-mode placeholder — identical to the 2a `form` placeholder (keeps the golden snapshot). */
function FormPlaceholder(): React.ReactElement {
  const { t } = useI18n()
  return (
    <div
      className="flex flex-col items-center justify-center gap-2 rounded-md border border-dashed border-border bg-muted p-6 text-muted-foreground"
      data-testid="page-node-form"
    >
      <span>
        <FileEdit size={24} />
      </span>
      <span>{t('builder.form.label')}</span>
    </div>
  )
}

/** "No data source configured" empty box (collection unset). */
function FormEmpty(): React.ReactElement {
  const { t } = useI18n()
  return (
    <div
      className="rounded-md border border-dashed border-border p-4 text-sm text-muted-foreground"
      data-testid="page-node-form"
    >
      {t('builder.form.noDataSource')}
    </div>
  )
}

/** Runtime form: ResourceForm bound to the configured collection. */
function ResourceFormWidget({ node }: { node: WidgetRenderProps['node'] }): React.ReactElement {
  const { t } = useI18n()
  const queryClient = useQueryClient()
  const collection = readFormCollection(node.props)
  // recordId / readOnly are already binding-resolved by renderNode (resolved-node invariant).
  const recordId = typeof node.props?.recordId === 'string' ? node.props.recordId : undefined
  const readOnly = node.props?.readOnly === true

  if (!collection) return <FormEmpty />

  return (
    <div data-testid="page-node-form">
      <ResourceForm
        className="kelta-page-form"
        resourceName={collection}
        recordId={recordId}
        readOnly={readOnly}
        onSave={() => {
          void queryClient.invalidateQueries({ queryKey: ['page-table', collection] })
          // events.onSubmit runtime is wired in 2e; 2f fires a default success toast.
          toast.success(t('builder.form.saved'))
        }}
        onCancel={() => {
          /* builder runtime: no-op */
        }}
      />
    </div>
  )
}

function FormRender({ node, mode }: WidgetRenderProps): React.ReactElement {
  if (mode === 'editor') return <FormPlaceholder />
  return <ResourceFormWidget node={node} />
}

const form: WidgetDescriptor = {
  type: 'form',
  label: 'Form',
  icon: FileEdit,
  category: 'input',
  acceptsChildren: false,
  defaultProps: { dataView: { collection: '' }, mode: 'create', readOnly: false },
  propSchema: [
    { key: 'dataView.collection', label: 'Collection', kind: 'collection-picker', group: 'Data' },
    {
      key: 'mode',
      label: 'Mode',
      kind: 'select',
      group: 'Data',
      options: [
        { value: 'create', label: 'Create' },
        { value: 'edit', label: 'Edit' },
      ],
    },
    { key: 'recordId', label: 'Record id', kind: 'expression', bindable: true, group: 'Data' },
    { key: 'readOnly', label: 'Read-only', kind: 'boolean', group: 'Data' },
    { key: 'events', label: 'Events', kind: 'event-list', group: 'Behaviour' },
  ],
  supportedEvents: ['onSubmit'],
  Render: FormRender,
}

export const formWidgets: WidgetDescriptor[] = [form]
