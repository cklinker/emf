/**
 * Data-binding built-ins (slice 2d):
 *  - `field-value` — a read-only bound value rendered through {@link FieldRenderer} (21 field types,
 *    FLS-stripped server-side). props are ALREADY resolved by `renderNode` (resolved-node invariant) —
 *    this descriptor just reads `node.props.source`.
 *  - `list` / `repeater` — binds an array (`scope.data.<name>` or any bound prop) and renders its
 *    `children` once per row under an `item`-augmented scope, capped at {@link MAX_REPEATER_ROWS}. This
 *    is the ONE descriptor allowed to re-resolve: it does so SOLELY for the per-row scope, by calling
 *    `renderChild(child, rowScope)` (which re-runs `renderNode` under that scope) — it never calls
 *    `resolveBindings` on its own props.
 */
/* eslint-disable react-refresh/only-export-components -- widget-descriptor module, not an HMR component file */
import React from 'react'
import { Diamond, ListOrdered } from 'lucide-react'
import { FieldRenderer } from '@/components/FieldRenderer/FieldRenderer'
import type { FieldType } from '@/hooks/useCollectionSchema'
import { useI18n } from '@/context/I18nContext'
import type { WidgetDescriptor, WidgetRenderProps, RenderNode } from '../types'
import type { BindingScope } from '../../model/bindingScope'
import { MAX_REPEATER_ROWS } from '../../model/limits'

/** The field types selectable in the `field-value` "Render as" dropdown. */
const FIELD_TYPE_OPTIONS: { label: string; value: string }[] = [
  { label: 'Text', value: 'string' },
  { label: 'Number', value: 'number' },
  { label: 'Boolean', value: 'boolean' },
  { label: 'Date', value: 'date' },
  { label: 'Date & time', value: 'datetime' },
  { label: 'Currency', value: 'currency' },
  { label: 'Percent', value: 'percent' },
  { label: 'Email', value: 'email' },
  { label: 'Phone', value: 'phone' },
  { label: 'URL', value: 'url' },
]

function FieldValueRender({ node }: WidgetRenderProps): React.ReactElement {
  // node.props is ALREADY fully resolved by renderNode — read the resolved values directly.
  const fieldType = (
    typeof node.props?.fieldType === 'string' ? node.props.fieldType : 'string'
  ) as FieldType | string
  return (
    <span data-testid="page-node-field-value">
      <FieldRenderer type={fieldType as FieldType} value={node.props?.source ?? null} />
    </span>
  )
}

const fieldValue: WidgetDescriptor = {
  type: 'field-value',
  label: 'Field value',
  icon: Diamond,
  category: 'data',
  defaultProps: { source: '', fieldType: 'string' },
  propSchema: [
    { key: 'source', label: 'Value', kind: 'expression', bindable: true, group: 'data' },
    {
      key: 'fieldType',
      label: 'Render as',
      kind: 'select',
      group: 'data',
      options: FIELD_TYPE_OPTIONS,
    },
  ],
  Render: FieldValueRender,
}

/** Renders a single repeater row: the node's children under the per-row scope. */
function RepeatRow({
  node,
  scope,
  renderChild,
}: {
  node: RenderNode
  scope: BindingScope
  renderChild: WidgetRenderProps['renderChild']
}): React.ReactElement {
  return <>{(node.children ?? []).map((child) => renderChild(child, scope))}</>
}

function RepeaterRender({ node, scope, renderChild }: WidgetRenderProps): React.ReactElement {
  const { t } = useI18n()
  // node.props.source is ALREADY resolved (at the repeater's own scope) — read it directly.
  const source = node.props?.source
  const rows = Array.isArray(source) ? source : []
  const visible = rows.slice(0, MAX_REPEATER_ROWS)
  return (
    <div className="flex flex-col gap-4" data-testid="page-node-repeater">
      {visible.map((row, i) => {
        const rowObj = (row && typeof row === 'object' ? row : {}) as Record<string, unknown>
        // Per-row scope: item = row, and record aliased to the row so children can use either.
        const rowScope: BindingScope = { ...scope, item: rowObj, record: rowObj }
        const key = typeof rowObj.id === 'string' || typeof rowObj.id === 'number' ? rowObj.id : i
        return (
          <div key={key} data-testid="page-node-repeater-row">
            <RepeatRow node={node} scope={rowScope} renderChild={renderChild} />
          </div>
        )
      })}
      {rows.length > MAX_REPEATER_ROWS && (
        <div className="text-sm text-muted-foreground" data-testid="page-node-repeater-truncated">
          {t('builder.repeater.truncated', { shown: MAX_REPEATER_ROWS, total: rows.length })}
        </div>
      )}
    </div>
  )
}

const repeater: WidgetDescriptor = {
  type: 'repeater',
  label: 'Repeater',
  icon: ListOrdered,
  category: 'data',
  defaultProps: { source: { $bind: '', mode: 'path' } },
  propSchema: [
    { key: 'source', label: 'Items (array)', kind: 'expression', bindable: true, group: 'data' },
  ],
  acceptsChildren: true,
  Render: RepeaterRender,
}

/** `list` is an alias of `repeater` (same descriptor, different type string + label). */
const list: WidgetDescriptor = { ...repeater, type: 'list', label: 'List' }

export const dataBindingWidgets: WidgetDescriptor[] = [fieldValue, repeater, list]
