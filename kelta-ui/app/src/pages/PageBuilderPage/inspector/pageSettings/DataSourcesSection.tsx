/**
 * Data-sources section of the page-settings drawer (slice 2d). Pure config editor for
 * `config.dataSources` (`PageDataSource[]`): add/remove rows; edit name, mode (list/single), collection,
 * fields, limit, and (single) record id. Enforces {@link MAX_PAGE_DATA_SOURCES} — the add button disables
 * at the cap with an inline message. Persisted through `handleSavePage` (passes `dataSources` to
 * `mergeConfig`).
 */
import React from 'react'
import { Plus, Trash2 } from 'lucide-react'
import { useI18n } from '@/context/I18nContext'
import type { PageDataSource } from '../../pageConfig'
import { MAX_PAGE_DATA_SOURCES } from '../../model/limits'

const INPUT_CLASS =
  'p-2 text-sm text-foreground bg-background border border-border rounded transition-[border-color,box-shadow] focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/20'

export interface DataSourcesSectionProps {
  dataSources: PageDataSource[]
  onChange: (next: PageDataSource[]) => void
}

export function DataSourcesSection({
  dataSources,
  onChange,
}: DataSourcesSectionProps): React.ReactElement {
  const { t } = useI18n()
  const atCap = dataSources.length >= MAX_PAGE_DATA_SOURCES

  const update = (index: number, patch: Partial<PageDataSource>) => {
    onChange(dataSources.map((d, i) => (i === index ? { ...d, ...patch } : d)))
  }
  const remove = (index: number) => onChange(dataSources.filter((_, i) => i !== index))
  const add = () => {
    if (atCap) return
    onChange([...dataSources, { name: '', collection: '', mode: 'list', limit: 25 }])
  }

  return (
    <section className="flex flex-col gap-3" data-testid="page-settings-data-sources">
      <div className="flex items-center justify-between">
        <h4 className="text-sm font-semibold text-foreground">{t('builder.data.title')}</h4>
        <button
          type="button"
          className="inline-flex items-center gap-1 rounded border border-border px-2 py-1 text-xs font-medium text-foreground hover:bg-accent focus:outline-2 focus:outline-primary focus:outline-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
          onClick={add}
          disabled={atCap}
          data-testid="add-data-source-button"
        >
          <Plus className="h-3 w-3" />
          {t('builder.data.add')}
        </button>
      </div>

      {atCap && (
        <p className="text-xs text-destructive" data-testid="data-source-max-message">
          {t('builder.data.maxSources', { max: MAX_PAGE_DATA_SOURCES })}
        </p>
      )}

      {dataSources.length === 0 ? (
        <p className="text-xs text-muted-foreground" data-testid="data-sources-empty">
          {t('builder.data.empty')}
        </p>
      ) : (
        <ul className="flex flex-col gap-2">
          {dataSources.map((src, i) => (
            <li
              key={i}
              className="flex flex-col gap-2 rounded border border-border p-2"
              data-testid="data-source-row"
            >
              <div className="flex items-end gap-2">
                <label className="flex flex-1 flex-col gap-1 text-xs text-muted-foreground">
                  {t('builder.data.name')}
                  <input
                    className={INPUT_CLASS}
                    value={src.name}
                    placeholder={t('builder.data.namePlaceholder')}
                    onChange={(e) => update(i, { name: e.target.value })}
                    data-testid={`data-source-name-${i}`}
                  />
                </label>
                <label className="flex flex-col gap-1 text-xs text-muted-foreground">
                  {t('builder.data.mode.list')}/{t('builder.data.mode.single')}
                  <select
                    className={INPUT_CLASS}
                    value={src.mode ?? 'list'}
                    onChange={(e) => update(i, { mode: e.target.value as PageDataSource['mode'] })}
                    data-testid={`data-source-mode-${i}`}
                  >
                    <option value="list">{t('builder.data.mode.list')}</option>
                    <option value="single">{t('builder.data.mode.single')}</option>
                  </select>
                </label>
                <button
                  type="button"
                  className="mb-1 inline-flex h-8 w-8 items-center justify-center rounded border border-border text-muted-foreground hover:bg-accent focus:outline-2 focus:outline-primary focus:outline-offset-2"
                  onClick={() => remove(i)}
                  aria-label={t('builder.data.remove')}
                  data-testid={`data-source-remove-${i}`}
                >
                  <Trash2 className="h-4 w-4" />
                </button>
              </div>
              <div className="flex items-end gap-2">
                <label className="flex flex-1 flex-col gap-1 text-xs text-muted-foreground">
                  {t('builder.data.collection')}
                  <input
                    className={INPUT_CLASS}
                    value={src.collection}
                    placeholder={t('builder.data.collectionPlaceholder')}
                    onChange={(e) => update(i, { collection: e.target.value })}
                    data-testid={`data-source-collection-${i}`}
                  />
                </label>
                {src.mode === 'single' ? (
                  <label className="flex flex-1 flex-col gap-1 text-xs text-muted-foreground">
                    {t('builder.data.recordId')}
                    <input
                      className={INPUT_CLASS}
                      value={typeof src.recordId === 'string' ? src.recordId : ''}
                      onChange={(e) => update(i, { recordId: e.target.value })}
                      data-testid={`data-source-record-id-${i}`}
                    />
                  </label>
                ) : (
                  <label className="flex flex-col gap-1 text-xs text-muted-foreground">
                    {t('builder.data.limit')}
                    <input
                      type="number"
                      className={`${INPUT_CLASS} w-20`}
                      value={src.limit ?? 25}
                      onChange={(e) => update(i, { limit: Number(e.target.value) || undefined })}
                      data-testid={`data-source-limit-${i}`}
                    />
                  </label>
                )}
              </div>
              {src.mode !== 'single' && (
                <label className="flex flex-col gap-1 text-xs text-muted-foreground">
                  {t('builder.data.fields')}
                  <input
                    className={INPUT_CLASS}
                    value={(src.fields ?? []).join(', ')}
                    placeholder={t('builder.data.fieldsPlaceholder')}
                    onChange={(e) =>
                      update(i, {
                        fields: e.target.value
                          .split(',')
                          .map((f) => f.trim())
                          .filter((f) => f.length > 0),
                      })
                    }
                    data-testid={`data-source-fields-${i}`}
                  />
                </label>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
