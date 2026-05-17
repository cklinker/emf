/**
 * DetailExtrasPanel
 *
 * Configures the two layout-level overrides that don't fit the existing
 * section/field/related-list model:
 *
 *   - `headerConfig` — record-header overrides (title fields, avatar source,
 *     meta-row items). When null/unset, ObjectDetailPage auto-derives a
 *     sensible meta row from the record.
 *   - `railBlocks`  — ordered list of right-rail blocks (MetadataCard,
 *     StatStrip, ScoreCard, TagsCard, AICard, Timeline). When null/empty,
 *     ObjectDetailPage falls back to a single auto-derived system-info
 *     MetadataCard.
 *
 * Header config is presented as a structured form (multi-select field
 * pickers + meta-row editor). Rail blocks are edited as JSON because
 * authoring structured editors for six discriminated kinds is a much larger
 * scope — the JSON view ships configurability today, structured editors
 * can land per-kind later without changing the persisted shape.
 */

import React, { useCallback, useMemo, useState } from 'react'
import { Plus, X } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import type { RailBlockDto, RecordHeaderConfigDto } from '@/hooks/usePageLayout'
import { useLayoutEditor } from './LayoutEditorContext'

interface MultiSelectFieldPickerProps {
  label: string
  values: string[]
  availableFieldNames: string[]
  onChange: (next: string[]) => void
}

function MultiSelectFieldPicker({
  label,
  values,
  availableFieldNames,
  onChange,
}: MultiSelectFieldPickerProps): React.ReactElement {
  const [selectValue, setSelectValue] = useState('')
  const remaining = availableFieldNames.filter((n) => !values.includes(n))

  const addField = (name: string): void => {
    if (!name || values.includes(name)) return
    onChange([...values, name])
    setSelectValue('')
  }

  return (
    <div className="space-y-2">
      <Label className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
        {label}
      </Label>
      <div className="flex flex-wrap gap-1.5">
        {values.length === 0 && (
          <span className="text-xs text-muted-foreground">None — leave empty to auto-derive.</span>
        )}
        {values.map((v) => (
          <Badge key={v} variant="secondary" className="gap-1">
            <span className="font-mono text-[11px]">{v}</span>
            <button
              type="button"
              onClick={() => onChange(values.filter((x) => x !== v))}
              aria-label={`Remove ${v}`}
              className="ml-0.5 inline-flex h-3.5 w-3.5 items-center justify-center rounded-sm hover:bg-muted-foreground/20"
            >
              <X className="h-3 w-3" aria-hidden="true" />
            </button>
          </Badge>
        ))}
      </div>
      {remaining.length > 0 && (
        <div className="flex gap-1.5">
          <select
            value={selectValue}
            onChange={(e) => setSelectValue(e.target.value)}
            className="h-7 flex-1 rounded-md border border-input bg-background px-2 text-xs"
          >
            <option value="">Add a field…</option>
            {remaining.map((n) => (
              <option key={n} value={n}>
                {n}
              </option>
            ))}
          </select>
          <Button
            type="button"
            size="sm"
            variant="outline"
            className="h-7 px-2"
            disabled={!selectValue}
            onClick={() => addField(selectValue)}
          >
            <Plus className="h-3.5 w-3.5" aria-hidden="true" />
          </Button>
        </div>
      )}
    </div>
  )
}

interface MetaFieldsEditorProps {
  values: NonNullable<RecordHeaderConfigDto['metaFields']>
  availableFieldNames: string[]
  onChange: (next: NonNullable<RecordHeaderConfigDto['metaFields']>) => void
}

function MetaFieldsEditor({
  values,
  availableFieldNames,
  onChange,
}: MetaFieldsEditorProps): React.ReactElement {
  const addRow = (): void => {
    onChange([...values, { key: availableFieldNames[0] ?? '' }])
  }
  const removeRow = (idx: number): void => {
    onChange(values.filter((_, i) => i !== idx))
  }
  const updateRow = (idx: number, patch: Partial<{ key: string; prefix?: string }>): void => {
    onChange(values.map((row, i) => (i === idx ? { ...row, ...patch } : row)))
  }

  return (
    <div className="space-y-2">
      <Label className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
        Meta row
      </Label>
      {values.length === 0 && (
        <p className="text-xs text-muted-foreground">
          No meta items configured — ObjectDetailPage will auto-derive a row from common
          contact-ish fields.
        </p>
      )}
      <div className="space-y-1.5">
        {values.map((row, idx) => (
          <div key={idx} className="flex items-center gap-1.5">
            <select
              value={row.key}
              onChange={(e) => updateRow(idx, { key: e.target.value })}
              className="h-7 flex-1 rounded-md border border-input bg-background px-2 text-xs"
              aria-label={`Meta field ${idx + 1} key`}
            >
              {availableFieldNames.map((n) => (
                <option key={n} value={n}>
                  {n}
                </option>
              ))}
              {!availableFieldNames.includes(row.key) && row.key && (
                <option value={row.key}>{row.key} (missing)</option>
              )}
            </select>
            <Input
              value={row.prefix ?? ''}
              onChange={(e) => updateRow(idx, { prefix: e.target.value || undefined })}
              placeholder="Prefix (e.g. Joined )"
              className="h-7 w-32 text-xs"
              aria-label={`Meta field ${idx + 1} prefix`}
            />
            <Button
              type="button"
              size="icon"
              variant="ghost"
              className="h-7 w-7"
              onClick={() => removeRow(idx)}
              aria-label={`Remove meta field ${idx + 1}`}
            >
              <X className="h-3.5 w-3.5" aria-hidden="true" />
            </Button>
          </div>
        ))}
      </div>
      <Button
        type="button"
        size="sm"
        variant="outline"
        className="h-7"
        onClick={addRow}
        disabled={availableFieldNames.length === 0}
      >
        <Plus className="mr-1 h-3.5 w-3.5" aria-hidden="true" />
        Add meta field
      </Button>
    </div>
  )
}

const RAIL_BLOCKS_SCHEMA_HINT = `Array of { kind, config }. Supported kinds:
  metadataCard  { title, rows: [{ label, value, mono? }] }
  statStrip     { tiles: [{ label, value, kind?, icon?, trend?, sub? }] }
  scoreCard     { title, score, statusLabel?, tone?, delta?, metrics? }
  tagsCard      { title, tags: [{ label, tone? }] }
  aiCard        { title, summary, actions? }
  timeline      { title, events: [{ at, title, body?, tone? }] }`

export function DetailExtrasPanel(): React.ReactElement | null {
  const { state, setHeaderConfig, setRailBlocks } = useLayoutEditor()

  const availableFieldNames = useMemo(
    () => state.availableFields.map((f) => f.name).sort(),
    [state.availableFields]
  )

  const headerConfig: RecordHeaderConfigDto = useMemo(
    () => state.headerConfig ?? {},
    [state.headerConfig]
  )
  const titleFields = headerConfig.titleFields ?? []
  const avatarFrom = headerConfig.avatarFrom ?? []
  const metaFields = headerConfig.metaFields ?? []

  const updateHeader = useCallback(
    (patch: Partial<RecordHeaderConfigDto>): void => {
      const next: RecordHeaderConfigDto = { ...headerConfig, ...patch }
      // Strip empties so we don't persist meaningless `[]`/`undefined` values
      const cleaned: RecordHeaderConfigDto = {}
      if (next.titleFields && next.titleFields.length > 0) cleaned.titleFields = next.titleFields
      if (next.avatarFrom && next.avatarFrom.length > 0) cleaned.avatarFrom = next.avatarFrom
      if (next.metaFields && next.metaFields.length > 0) cleaned.metaFields = next.metaFields
      setHeaderConfig(Object.keys(cleaned).length > 0 ? cleaned : null)
    },
    [headerConfig, setHeaderConfig]
  )

  // Initial textarea content reflects the loaded rail-blocks snapshot. We don't
  // re-sync mid-edit: railBlocks isn't part of the undo stack, and the editor
  // remounts (along with this panel) whenever a different layout is loaded —
  // so the only way state.railBlocks changes is through this textarea's own
  // commit-on-blur, at which point the textarea already holds the new value.
  const [railJson, setRailJson] = useState<string>(() =>
    state.railBlocks ? JSON.stringify(state.railBlocks, null, 2) : ''
  )
  const [railError, setRailError] = useState<string | null>(null)

  const commitRailJson = (): void => {
    const trimmed = railJson.trim()
    if (trimmed === '') {
      setRailBlocks(null)
      setRailError(null)
      return
    }
    try {
      const parsed = JSON.parse(trimmed) as unknown
      if (!Array.isArray(parsed)) {
        setRailError('Must be a JSON array of { kind, config } blocks.')
        return
      }
      setRailBlocks(parsed as RailBlockDto[])
      setRailError(null)
    } catch (err) {
      setRailError(err instanceof Error ? err.message : 'Invalid JSON')
    }
  }

  return (
    <div className="space-y-4 border-t border-border p-4">
      <div>
        <h3 className="text-sm font-semibold text-foreground">Detail page extras</h3>
        <p className="mt-0.5 text-xs text-muted-foreground">
          Optional overrides for the record-detail header and side rail.
        </p>
      </div>

      <section className="space-y-3 rounded-md border border-border bg-card p-3">
        <h4 className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
          Record header
        </h4>

        <MultiSelectFieldPicker
          label="Title fields"
          values={titleFields}
          availableFieldNames={availableFieldNames}
          onChange={(next) => updateHeader({ titleFields: next })}
        />

        <MultiSelectFieldPicker
          label="Avatar from"
          values={avatarFrom}
          availableFieldNames={availableFieldNames}
          onChange={(next) => updateHeader({ avatarFrom: next })}
        />

        <MetaFieldsEditor
          values={metaFields}
          availableFieldNames={availableFieldNames}
          onChange={(next) => updateHeader({ metaFields: next })}
        />
      </section>

      <section className="space-y-2 rounded-md border border-border bg-card p-3">
        <h4 className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
          Right rail blocks
        </h4>
        <p className="text-xs text-muted-foreground whitespace-pre-line">
          {RAIL_BLOCKS_SCHEMA_HINT}
        </p>
        <Textarea
          value={railJson}
          onChange={(e) => setRailJson(e.target.value)}
          onBlur={commitRailJson}
          rows={10}
          className="font-mono text-[11px] leading-relaxed"
          placeholder='[ { "kind": "metadataCard", "config": { "title": "System", "rows": [] } } ]'
          aria-label="Rail blocks JSON"
        />
        {railError && (
          <p className="text-xs text-destructive" role="alert">
            {railError}
          </p>
        )}
        <p className="text-[11px] text-muted-foreground">
          Tip: leave empty to auto-derive a System-information card. Changes commit on blur.
        </p>
      </section>
    </div>
  )
}
