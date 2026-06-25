/**
 * `kind:'collection-picker'` → the data-source editor for `table`/`form`. The 2a descriptors carry a
 * single field with `key:'dataView'`, so this one editor edits the whole `dataView` object
 * (`{ collection, fields, limit }`) — behavior-preserving with the legacy PropertyPanel, including its
 * `property-collection` / `property-columns` / `property-limit` testids. Row limit is table-only.
 *
 * The collection input is free-text (parity with today). A richer collection/field picker backed by the
 * CollectionStore is a later refinement; keeping it free-text avoids a hard CollectionStoreProvider
 * dependency in the builder and matches the existing authoring flow + tests.
 */
import React from 'react'
import { useI18n } from '../../../../context/I18nContext'
import type { PropValue } from '../../model/pageModel'
import type { FieldEditorProps } from './types'

interface DataView {
  collection?: string
  fields?: string[]
  limit?: number
}

const INPUT_CLASS =
  'p-2 text-sm text-foreground bg-background border border-border rounded transition-[border-color,box-shadow] focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/20'

/** Read the raw dataView object (preserving exactly the keys present) for merge + display. */
function rawDataView(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : {}
}

function readDataView(value: unknown): DataView {
  const o = rawDataView(value)
  return {
    collection: typeof o.collection === 'string' ? o.collection : undefined,
    fields: Array.isArray(o.fields)
      ? o.fields.filter((f): f is string => typeof f === 'string')
      : [],
    limit: typeof o.limit === 'number' ? o.limit : undefined,
  }
}

export function CollectionPickerField({
  value,
  onChange,
  node,
  fieldId,
}: FieldEditorProps): React.ReactElement {
  const { t } = useI18n()
  const dataView = readDataView(value)
  const isTable = node.type === 'table'

  // Merge over the RAW object so we only write the keys we touch (parity with the legacy editor — a
  // freshly-added table never gets a `limit` key until the user sets one).
  const patch = (next: Partial<DataView>) =>
    onChange({ ...rawDataView(value), ...next } as PropValue)

  return (
    <div className="flex flex-col gap-4" data-testid={fieldId}>
      <div className="flex flex-col gap-1">
        <label
          className="text-xs font-medium text-muted-foreground"
          htmlFor={`${node.id}-collection`}
        >
          {t('builder.inspector.dataView.collection')}
        </label>
        <input
          id={`${node.id}-collection`}
          type="text"
          className={INPUT_CLASS}
          value={dataView.collection ?? ''}
          onChange={(e) => patch({ collection: e.target.value || undefined })}
          placeholder={t('builder.inspector.dataView.collectionPlaceholder')}
          data-testid="property-collection"
        />
      </div>
      <div className="flex flex-col gap-1">
        <label className="text-xs font-medium text-muted-foreground" htmlFor={`${node.id}-columns`}>
          {isTable
            ? t('builder.inspector.dataView.columns')
            : t('builder.inspector.dataView.fields')}
        </label>
        <input
          id={`${node.id}-columns`}
          type="text"
          className={INPUT_CLASS}
          value={(dataView.fields ?? []).join(', ')}
          onChange={(e) =>
            patch({
              fields: e.target.value
                .split(',')
                .map((f) => f.trim())
                .filter((f) => f.length > 0),
            })
          }
          placeholder={t('builder.inspector.dataView.columnsPlaceholder')}
          data-testid="property-columns"
        />
      </div>
      {isTable && (
        <div className="flex flex-col gap-1">
          <label className="text-xs font-medium text-muted-foreground" htmlFor={`${node.id}-limit`}>
            {t('builder.inspector.dataView.limit')}
          </label>
          <input
            id={`${node.id}-limit`}
            type="number"
            className={INPUT_CLASS}
            value={dataView.limit ?? ''}
            onChange={(e) => patch({ limit: e.target.value ? Number(e.target.value) : undefined })}
            placeholder="25"
            data-testid="property-limit"
          />
        </div>
      )}
    </div>
  )
}
