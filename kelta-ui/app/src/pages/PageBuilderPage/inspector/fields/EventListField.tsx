/**
 * `kind:'event-list'` SHELL — authoring surface for `node.events` (the whole EventHandlers map).
 * Renders one tab per `supportedEvents` entry (declared on the descriptor in 2a); the active tab edits
 * that event's ordered `PageAction[]` (add / remove / reorder + a minimal per-action editor).
 *
 * EventListField authors PageAction[] only. The action RUNTIME (dispatching runFlow → /api/flows/{id}/execute,
 * navigate, createRecord, etc.) lands in slice 2e. Do not add execution here.
 */
import React, { useState } from 'react'
import { useI18n } from '../../../../context/I18nContext'
import type { PageComponent } from '../../model/pageModel'
import type { EventHandlers, EventName, PageAction, PropValue } from '../../model/pageModel'

export interface EventListFieldProps {
  /** The events this widget supports (descriptor.supportedEvents), e.g. ['onClick']. */
  supportedEvents: EventName[]
  /** Current value of node.events (the whole EventHandlers map). May be undefined. */
  value: EventHandlers | undefined
  /** Writes the whole EventHandlers map back to node.events (NOT a single handler). */
  onChange: (events: EventHandlers) => void
  node: PageComponent
  fieldId: string
}

/** The action types the shell can author (mirrors the PageAction union discriminant). */
const ACTION_TYPES: PageAction['action'][] = [
  'runFlow',
  'navigate',
  'openUrl',
  'createRecord',
  'updateRecord',
  'refreshData',
  'setVar',
  'showToast',
]

/** A default (empty-param) action for the chosen type — params are filled in by the per-action editor. */
function defaultAction(type: PageAction['action']): PageAction {
  switch (type) {
    case 'runFlow':
      return { action: 'runFlow', flowId: '', input: {} }
    case 'navigate':
      return { action: 'navigate', to: '' }
    case 'openUrl':
      return { action: 'openUrl', url: '' }
    case 'createRecord':
      return { action: 'createRecord', collection: '', attributes: {} }
    case 'updateRecord':
      return { action: 'updateRecord', collection: '', attributes: {} }
    case 'refreshData':
      return { action: 'refreshData', dataSource: '' }
    case 'setVar':
      return { action: 'setVar', name: '', value: '' }
    case 'showToast':
      return { action: 'showToast', level: 'info', message: '' }
  }
}

const INPUT_CLASS =
  'flex-1 p-1.5 text-xs text-foreground bg-background border border-border rounded focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/20'

export function EventListField({
  supportedEvents,
  value,
  onChange,
  fieldId,
}: EventListFieldProps): React.ReactElement {
  const { t } = useI18n()
  const events = supportedEvents.length > 0 ? supportedEvents : (['onClick'] as EventName[])
  const [activeEvent, setActiveEvent] = useState<EventName>(events[0])
  const actions = value?.[activeEvent] ?? []
  const [pendingType, setPendingType] = useState<PageAction['action']>('runFlow')

  /** Splice `next` into the active event, dropping the key when empty. */
  const writeActions = (next: PageAction[]) => {
    const map: EventHandlers = { ...value }
    if (next.length === 0) {
      delete map[activeEvent]
    } else {
      map[activeEvent] = next
    }
    onChange(map)
  }

  const addAction = () => writeActions([...actions, defaultAction(pendingType)])
  const removeAction = (i: number) => writeActions(actions.filter((_, idx) => idx !== i))
  const moveAction = (i: number, dir: -1 | 1) => {
    const j = i + dir
    if (j < 0 || j >= actions.length) return
    const next = [...actions]
    ;[next[i], next[j]] = [next[j], next[i]]
    writeActions(next)
  }
  const editAction = (i: number, patch: Record<string, PropValue>) =>
    writeActions(actions.map((a, idx) => (idx === i ? ({ ...a, ...patch } as PageAction) : a)))

  return (
    <div className="flex flex-col gap-2" data-testid={fieldId}>
      {events.length > 1 && (
        <div className="flex gap-1 border-b border-border" role="tablist">
          {events.map((ev) => (
            <button
              key={ev}
              type="button"
              role="tab"
              aria-selected={ev === activeEvent}
              className={
                ev === activeEvent
                  ? 'border-b-2 border-primary px-2 py-1 text-xs font-medium text-foreground'
                  : 'px-2 py-1 text-xs text-muted-foreground hover:text-foreground'
              }
              onClick={() => setActiveEvent(ev)}
              data-testid={`event-tab-${ev}`}
            >
              {t(`builder.events.${ev}`)}
            </button>
          ))}
        </div>
      )}

      <ul className="flex flex-col gap-2">
        {actions.map((action, i) => (
          <li
            key={i}
            className="flex flex-col gap-1.5 rounded border border-border p-2"
            data-testid={`event-row-${activeEvent}-${i}`}
          >
            <div className="flex items-center gap-1">
              <button
                type="button"
                className="px-1 text-xs text-muted-foreground hover:text-foreground disabled:opacity-30"
                onClick={() => moveAction(i, -1)}
                disabled={i === 0}
                aria-label={t('builder.events.moveUp')}
                data-testid={`event-up-${activeEvent}-${i}`}
              >
                ↑
              </button>
              <button
                type="button"
                className="px-1 text-xs text-muted-foreground hover:text-foreground disabled:opacity-30"
                onClick={() => moveAction(i, 1)}
                disabled={i === actions.length - 1}
                aria-label={t('builder.events.moveDown')}
                data-testid={`event-down-${activeEvent}-${i}`}
              >
                ↓
              </button>
              <select
                className="flex-1 p-1.5 text-xs text-foreground bg-background border border-border rounded focus:outline-none focus:border-primary"
                value={action.action}
                onChange={(e) =>
                  writeActions(
                    actions.map((a, idx) =>
                      idx === i ? defaultAction(e.target.value as PageAction['action']) : a
                    )
                  )
                }
                data-testid={`event-type-${activeEvent}-${i}`}
              >
                {ACTION_TYPES.map((tp) => (
                  <option key={tp} value={tp}>
                    {t(`builder.events.action.${tp}`)}
                  </option>
                ))}
              </select>
              <button
                type="button"
                className="px-1 text-sm text-muted-foreground hover:text-destructive"
                onClick={() => removeAction(i)}
                aria-label={t('builder.events.removeAction')}
                data-testid={`event-remove-${activeEvent}-${i}`}
              >
                ×
              </button>
            </div>
            <ActionParams action={action} onChange={(patch) => editAction(i, patch)} />
          </li>
        ))}
      </ul>

      <div className="flex items-center gap-2">
        <select
          className="p-1.5 text-xs text-foreground bg-background border border-border rounded focus:outline-none focus:border-primary"
          value={pendingType}
          onChange={(e) => setPendingType(e.target.value as PageAction['action'])}
          data-testid={`event-add-type-${activeEvent}`}
          aria-label={t('builder.events.actionType')}
        >
          {ACTION_TYPES.map((tp) => (
            <option key={tp} value={tp}>
              {t(`builder.events.action.${tp}`)}
            </option>
          ))}
        </select>
        <button
          type="button"
          className="inline-flex items-center rounded border border-border px-2 py-1 text-xs font-medium text-foreground transition-colors hover:bg-accent focus:outline-2 focus:outline-primary focus:outline-offset-2"
          onClick={addAction}
          data-testid={`event-add-${activeEvent}`}
        >
          + {t('builder.events.addAction')}
        </button>
      </div>
    </div>
  )
}

const LABEL_CLASS = 'text-[11px] font-medium text-muted-foreground'

/** A labeled row wrapper for one sub-form field. */
function Field({
  label,
  children,
}: {
  label: string
  children: React.ReactNode
}): React.ReactElement {
  return (
    <label className="flex flex-col gap-1">
      <span className={LABEL_CLASS}>{label}</span>
      {children}
    </label>
  )
}

/**
 * Per-action sub-form (slice 2e §5.2). Each action type gets its minimal labeled config. Values are
 * stored verbatim into the PageAction; the runtime (executeAction) resolves bindable values at fire time.
 * String value cells accept either a literal or a `{{record.id}}`-style merge tag (resolved by the
 * runtime's interpolation/binding layer).
 */
function ActionParams({
  action,
  onChange,
}: {
  action: PageAction
  onChange: (patch: Record<string, PropValue>) => void
}): React.ReactElement | null {
  const { t } = useI18n()
  switch (action.action) {
    case 'runFlow':
      return (
        <div className="flex flex-col gap-1.5">
          <Field label={t('builder.actions.runFlow.flowId')}>
            <input
              className={INPUT_CLASS}
              value={action.flowId}
              onChange={(e) => onChange({ flowId: e.target.value })}
              placeholder={t('builder.events.param.flowId')}
              data-testid="event-param-flowId"
            />
          </Field>
          <MapRows
            label={t('builder.actions.runFlow.inputs')}
            addLabel={t('builder.actions.runFlow.addInput')}
            testidPrefix="event-param-input"
            value={(action.input ?? {}) as Record<string, PropValue>}
            onChange={(input) => onChange({ input })}
          />
          <label className="flex items-center gap-1.5">
            <input
              type="checkbox"
              checked={!!action.awaitResult}
              onChange={(e) => onChange({ awaitResult: e.target.checked })}
              data-testid="event-param-awaitResult"
            />
            <span className={LABEL_CLASS}>{t('builder.actions.runFlow.awaitResult')}</span>
          </label>
        </div>
      )
    case 'navigate':
      return (
        <div className="flex flex-col gap-1.5">
          <Field label={t('builder.actions.navigate.to')}>
            <input
              className={INPUT_CLASS}
              value={action.to}
              onChange={(e) => onChange({ to: e.target.value })}
              placeholder={t('builder.events.param.to')}
              data-testid="event-param-to"
            />
          </Field>
          <NewTabToggle action={action} onChange={onChange} t={t} />
        </div>
      )
    case 'openUrl':
      return (
        <div className="flex flex-col gap-1.5">
          <Field label={t('builder.actions.openUrl.url')}>
            <input
              className={INPUT_CLASS}
              value={typeof action.url === 'string' ? action.url : ''}
              onChange={(e) => onChange({ url: e.target.value })}
              placeholder={t('builder.events.param.url')}
              data-testid="event-param-url"
            />
          </Field>
          <NewTabToggle action={action} onChange={onChange} t={t} />
        </div>
      )
    case 'createRecord':
    case 'updateRecord':
      return (
        <div className="flex flex-col gap-1.5">
          <Field label={t('builder.actions.record.collection')}>
            <input
              className={INPUT_CLASS}
              value={action.collection}
              onChange={(e) => onChange({ collection: e.target.value })}
              placeholder={t('builder.events.param.collection')}
              data-testid="event-param-collection"
            />
          </Field>
          {action.action === 'updateRecord' && (
            <Field label={t('builder.actions.record.recordId')}>
              <input
                className={INPUT_CLASS}
                value={typeof action.recordId === 'string' ? action.recordId : ''}
                onChange={(e) => onChange({ recordId: e.target.value })}
                placeholder={t('builder.actions.record.recordId')}
                data-testid="event-param-recordId"
              />
            </Field>
          )}
          <MapRows
            label={t('builder.actions.record.attributes')}
            addLabel={t('builder.actions.record.addAttribute')}
            testidPrefix="event-param-attr"
            value={(action.attributes ?? {}) as Record<string, PropValue>}
            onChange={(attributes) => onChange({ attributes })}
          />
        </div>
      )
    case 'refreshData':
      return (
        <Field label={t('builder.actions.refreshData.dataSource')}>
          <input
            className={INPUT_CLASS}
            value={action.dataSource}
            onChange={(e) => onChange({ dataSource: e.target.value })}
            placeholder={t('builder.events.param.dataSource')}
            data-testid="event-param-dataSource"
          />
        </Field>
      )
    case 'setVar':
      return (
        <div className="flex gap-1.5">
          <Field label={t('builder.actions.setVar.name')}>
            <input
              className={INPUT_CLASS}
              value={action.name}
              onChange={(e) => onChange({ name: e.target.value })}
              placeholder={t('builder.events.param.varName')}
              data-testid="event-param-name"
            />
          </Field>
          <Field label={t('builder.actions.setVar.value')}>
            <input
              className={INPUT_CLASS}
              value={typeof action.value === 'string' ? action.value : ''}
              onChange={(e) => onChange({ value: e.target.value })}
              placeholder={t('builder.events.param.varValue')}
              data-testid="event-param-value"
            />
          </Field>
        </div>
      )
    case 'showToast':
      return (
        <div className="flex gap-1.5">
          <Field label={t('builder.actions.showToast.level')}>
            <select
              className="p-1.5 text-xs text-foreground bg-background border border-border rounded focus:outline-none focus:border-primary"
              value={action.level}
              onChange={(e) => onChange({ level: e.target.value })}
              data-testid="event-param-level"
            >
              <option value="info">{t('builder.events.toast.info')}</option>
              <option value="success">{t('builder.events.toast.success')}</option>
              <option value="error">{t('builder.events.toast.error')}</option>
            </select>
          </Field>
          <Field label={t('builder.actions.showToast.message')}>
            <input
              className={INPUT_CLASS}
              value={typeof action.message === 'string' ? action.message : ''}
              onChange={(e) => onChange({ message: e.target.value })}
              placeholder={t('builder.events.param.message')}
              data-testid="event-param-message"
            />
          </Field>
        </div>
      )
  }
}

/** Shared "New tab" checkbox for navigate/openUrl. */
function NewTabToggle({
  action,
  onChange,
  t,
}: {
  action: Extract<PageAction, { action: 'navigate' | 'openUrl' }>
  onChange: (patch: Record<string, PropValue>) => void
  t: (key: string) => string
}): React.ReactElement {
  return (
    <label className="flex items-center gap-1.5">
      <input
        type="checkbox"
        checked={!!action.newTab}
        onChange={(e) => onChange({ newTab: e.target.checked })}
        data-testid="event-param-newTab"
      />
      <span className={LABEL_CLASS}>{t('builder.actions.navigate.newTab')}</span>
    </label>
  )
}

/**
 * Key→value rows editor for an action's `input`/`attributes` map. Keys are free-text; values accept a
 * literal or a `{{merge.tag}}` (resolved by the runtime). Emits the whole map immutably on every edit.
 */
function MapRows({
  label,
  addLabel,
  testidPrefix,
  value,
  onChange,
}: {
  label: string
  addLabel: string
  testidPrefix: string
  value: Record<string, PropValue>
  onChange: (next: Record<string, PropValue>) => void
}): React.ReactElement {
  const entries = Object.entries(value)

  const writeKey = (oldKey: string, newKey: string) => {
    const next: Record<string, PropValue> = {}
    for (const [k, v] of entries) next[k === oldKey ? newKey : k] = v
    onChange(next)
  }
  const writeVal = (key: string, v: PropValue) => onChange({ ...value, [key]: v })
  const removeKey = (key: string) => {
    const next = { ...value }
    delete next[key]
    onChange(next)
  }
  const addRow = () => {
    // Find a free placeholder key so the new empty row is addressable.
    let i = entries.length + 1
    let key = `key${i}`
    while (key in value) key = `key${++i}`
    onChange({ ...value, [key]: '' })
  }

  return (
    <div className="flex flex-col gap-1">
      <span className={LABEL_CLASS}>{label}</span>
      {entries.map(([key, val], i) => (
        <div className="flex items-center gap-1" key={i} data-testid={`${testidPrefix}-row-${i}`}>
          <input
            className={INPUT_CLASS}
            value={key}
            onChange={(e) => writeKey(key, e.target.value)}
            data-testid={`${testidPrefix}-key-${i}`}
          />
          <input
            className={INPUT_CLASS}
            value={typeof val === 'string' ? val : JSON.stringify(val)}
            onChange={(e) => writeVal(key, e.target.value)}
            data-testid={`${testidPrefix}-value-${i}`}
          />
          <button
            type="button"
            className="px-1 text-sm text-muted-foreground hover:text-destructive"
            onClick={() => removeKey(key)}
            data-testid={`${testidPrefix}-remove-${i}`}
          >
            ×
          </button>
        </div>
      ))}
      <button
        type="button"
        className="self-start rounded border border-border px-2 py-1 text-xs text-foreground hover:bg-accent"
        onClick={addRow}
        data-testid={`${testidPrefix}-add`}
      >
        + {addLabel}
      </button>
    </div>
  )
}
