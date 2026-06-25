/**
 * Variables section of the page-settings drawer (slice 2d). Pure config editor for `config.variables`
 * (`PageVariable[]`): add/remove rows; edit name, type, and default. The drawer owns the state and
 * persists it through `handleSavePage` (which passes `variables` to `mergeConfig`).
 */
import React from 'react'
import { Plus, Trash2 } from 'lucide-react'
import { useI18n } from '@/context/I18nContext'
import type { PageVariable } from '../../pageConfig'

const VAR_TYPES: PageVariable['type'][] = ['string', 'number', 'boolean', 'json']

const INPUT_CLASS =
  'p-2 text-sm text-foreground bg-background border border-border rounded transition-[border-color,box-shadow] focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/20'

export interface VariablesSectionProps {
  variables: PageVariable[]
  onChange: (next: PageVariable[]) => void
}

export function VariablesSection({
  variables,
  onChange,
}: VariablesSectionProps): React.ReactElement {
  const { t } = useI18n()

  const update = (index: number, patch: Partial<PageVariable>) => {
    onChange(variables.map((v, i) => (i === index ? { ...v, ...patch } : v)))
  }
  const remove = (index: number) => onChange(variables.filter((_, i) => i !== index))
  const add = () => onChange([...variables, { name: '', type: 'string', default: '' }])

  return (
    <section className="flex flex-col gap-3" data-testid="page-settings-variables">
      <div className="flex items-center justify-between">
        <h4 className="text-sm font-semibold text-foreground">{t('builder.variables.title')}</h4>
        <button
          type="button"
          className="inline-flex items-center gap-1 rounded border border-border px-2 py-1 text-xs font-medium text-foreground hover:bg-accent focus:outline-2 focus:outline-primary focus:outline-offset-2"
          onClick={add}
          data-testid="add-variable-button"
        >
          <Plus className="h-3 w-3" />
          {t('builder.variables.add')}
        </button>
      </div>

      {variables.length === 0 ? (
        <p className="text-xs text-muted-foreground" data-testid="variables-empty">
          {t('builder.variables.empty')}
        </p>
      ) : (
        <ul className="flex flex-col gap-2">
          {variables.map((variable, i) => (
            <li
              key={i}
              className="flex items-end gap-2 rounded border border-border p-2"
              data-testid="variable-row"
            >
              <label className="flex flex-1 flex-col gap-1 text-xs text-muted-foreground">
                {t('builder.variables.name')}
                <input
                  className={INPUT_CLASS}
                  value={variable.name}
                  placeholder={t('builder.variables.namePlaceholder')}
                  onChange={(e) => update(i, { name: e.target.value })}
                  data-testid={`variable-name-${i}`}
                />
              </label>
              <label className="flex flex-col gap-1 text-xs text-muted-foreground">
                {t('builder.variables.type')}
                <select
                  className={INPUT_CLASS}
                  value={variable.type}
                  onChange={(e) => update(i, { type: e.target.value as PageVariable['type'] })}
                  data-testid={`variable-type-${i}`}
                >
                  {VAR_TYPES.map((tp) => (
                    <option key={tp} value={tp}>
                      {tp}
                    </option>
                  ))}
                </select>
              </label>
              <label className="flex flex-1 flex-col gap-1 text-xs text-muted-foreground">
                {t('builder.variables.default')}
                <input
                  className={INPUT_CLASS}
                  value={variable.default == null ? '' : String(variable.default)}
                  onChange={(e) => update(i, { default: e.target.value })}
                  data-testid={`variable-default-${i}`}
                />
              </label>
              <button
                type="button"
                className="mb-1 inline-flex h-8 w-8 items-center justify-center rounded border border-border text-muted-foreground hover:bg-accent focus:outline-2 focus:outline-primary focus:outline-offset-2"
                onClick={() => remove(i)}
                aria-label={t('builder.variables.remove')}
                data-testid={`variable-remove-${i}`}
              >
                <Trash2 className="h-4 w-4" />
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
