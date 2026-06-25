/**
 * Schema-driven inspector. Given the selected node and its `widgetRegistry.get(node.type)` descriptor,
 * it loops over `descriptor.propSchema`, groups fields by `group`, and maps each `PropFieldKind` to a
 * field editor under `inspector/fields/*`. This is the SINGLE place that knows the kind→editor mapping;
 * there are zero per-type conditionals. Adding a kind = one field component + one case here.
 *
 * Write targets:
 *  - `span`       → node.span        (onChange({ span }))
 *  - `event-list` → node.events      (onChange({ events }); reads node.events, not node.props)
 *  - `children`   → read-only summary (no write)
 *  - everything else → node.props[key] via getByPath/setByPath (bindable fields wrap in BindableField)
 */
import React from 'react'
import { useI18n } from '../../../context/I18nContext'
import { widgetRegistry } from '../widgets/registry'
import type { PropFieldSchema, PropFieldKind } from '../widgets/types'
import type { EventHandlers, PageComponent, PropValue, ResponsiveSpan } from '../model/pageModel'
import { getByPath, setByPath, deleteByPath } from './propPath'
import { BindableField } from './BindableField'
import {
  TextField,
  TextareaField,
  NumberField,
  BooleanField,
  SelectField,
  ColorField,
  CollectionPickerField,
  FieldPickerField,
  ExpressionField,
  SpanField,
  ChildrenField,
  EventListField,
  type FieldEditorProps,
} from './fields'

export interface InspectorProps {
  /** The selected node, or null. */
  node: PageComponent | null
  /** Patch the node (props/events/span). Mirrors PropertyPanel's onChange(updates: Partial<PageComponent>). */
  onChange: (updates: Partial<PageComponent>) => void
}

const PANEL_CLASS = 'bg-background border border-border rounded-md p-4 overflow-y-auto'

/** Literal editors (everything that is NOT span/children/event-list). Bindable wrapping is handled above. */
const LITERAL_EDITORS: Partial<Record<PropFieldKind, React.ComponentType<FieldEditorProps>>> = {
  text: TextField,
  textarea: TextareaField,
  number: NumberField as React.ComponentType<FieldEditorProps>,
  boolean: BooleanField as React.ComponentType<FieldEditorProps>,
  select: SelectField,
  color: ColorField,
  'collection-picker': CollectionPickerField,
  'field-picker': FieldPickerField as React.ComponentType<FieldEditorProps>,
  expression: ExpressionField,
}

export function Inspector({ node, onChange }: InspectorProps): React.ReactElement {
  const { t } = useI18n()

  if (!node) {
    return (
      <div className={PANEL_CLASS} data-testid="property-panel">
        <h3 className="m-0 mb-4 text-sm font-semibold text-foreground">
          {t('builder.pages.properties')}
        </h3>
        <p className="text-muted-foreground text-sm text-center py-6">
          {t('builder.pages.selectComponent')}
        </p>
      </div>
    )
  }

  const descriptor = widgetRegistry.get(node.type)
  const schema = descriptor.propSchema ?? []

  // Group fields by `group`, preserving first-seen order; ungrouped fields go in a leading default group.
  const groupOrder: string[] = []
  const groups = new Map<string, PropFieldSchema[]>()
  for (const field of schema) {
    const key = field.group ?? ''
    if (!groups.has(key)) {
      groups.set(key, [])
      groupOrder.push(key)
    }
    groups.get(key)!.push(field)
  }

  const renderField = (field: PropFieldSchema): React.ReactNode => {
    const fieldId = `property-${field.key}`

    // span → node.span (NOT props)
    if (field.kind === 'span') {
      return (
        <SpanField
          schema={field}
          value={node.span}
          onChange={(span) => onChange({ span: span as ResponsiveSpan })}
          node={node}
          fieldId={fieldId}
        />
      )
    }

    // event-list → node.events (NOT props). Tabs come from descriptor.supportedEvents.
    if (field.kind === 'event-list') {
      return (
        <EventListField
          supportedEvents={descriptor.supportedEvents ?? []}
          value={node.events}
          onChange={(events: EventHandlers) => {
            if (Object.keys(events).length === 0) {
              onChange({ events: undefined })
            } else {
              onChange({ events })
            }
          }}
          node={node}
          fieldId={fieldId}
        />
      )
    }

    // children → read-only summary, no write.
    if (field.kind === 'children') {
      return (
        <ChildrenField
          schema={field}
          value={undefined}
          onChange={() => {}}
          node={node}
          fieldId={fieldId}
        />
      )
    }

    // Everything else writes node.props[key].
    const value = getByPath(node.props, field.key) as PropValue | undefined
    const writeProp = (v: PropValue | undefined) => {
      const props =
        v === undefined ? deleteByPath(node.props, field.key) : setByPath(node.props, field.key, v)
      onChange({ props })
    }

    const Editor = LITERAL_EDITORS[field.kind]
    if (!Editor) return null

    const literal = (args: { value: PropValue | undefined; onChange: (v: PropValue) => void }) => (
      <Editor
        schema={field}
        value={args.value}
        onChange={args.onChange}
        node={node}
        fieldId={fieldId}
      />
    )

    if (field.bindable) {
      return (
        <BindableField
          schema={field}
          value={value}
          onChange={(v) => writeProp(v)}
          node={node}
          fieldId={fieldId}
          renderLiteral={literal}
          literalDefault={descriptor.defaultProps[field.key] ?? ''}
        />
      )
    }

    return (
      <div className="flex flex-col gap-1">
        <span className="text-xs font-medium text-muted-foreground">{field.label}</span>
        {literal({ value, onChange: (v) => writeProp(v) })}
      </div>
    )
  }

  return (
    <div className={PANEL_CLASS} data-testid="property-panel">
      <h3 className="m-0 mb-4 text-sm font-semibold text-foreground">
        {t('builder.pages.properties')}
      </h3>
      <div className="flex flex-col gap-4">
        <div className="flex flex-col gap-1">
          <span className="text-xs font-medium text-muted-foreground">
            {t('builder.inspector.componentType')}
          </span>
          <span className="text-sm text-foreground capitalize" data-testid="property-type">
            {node.type}
          </span>
        </div>
        <div className="flex flex-col gap-1">
          <span className="text-xs font-medium text-muted-foreground">
            {t('builder.inspector.id')}
          </span>
          <input
            type="text"
            className="p-2 text-sm text-foreground bg-background border border-border rounded disabled:bg-muted disabled:text-muted-foreground"
            value={node.id}
            disabled
            data-testid="property-id"
          />
        </div>

        {schema.length === 0 ? (
          <p className="text-xs text-muted-foreground" data-testid="property-empty">
            {t('builder.inspector.noProperties')}
          </p>
        ) : (
          groupOrder.map((group) => (
            <div key={group || 'default'} className="flex flex-col gap-3">
              {group && (
                <h4
                  className="text-[11px] font-semibold uppercase tracking-wide text-muted-foreground"
                  data-testid={`property-group-${group}`}
                >
                  {t(`builder.inspector.group.${group}`)}
                </h4>
              )}
              {groups.get(group)!.map((field) => (
                <div key={field.key} className="flex flex-col gap-1">
                  {renderField(field)}
                </div>
              ))}
            </div>
          ))
        )}
      </div>
    </div>
  )
}
