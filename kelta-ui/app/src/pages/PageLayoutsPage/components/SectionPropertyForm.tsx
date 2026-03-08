/**
 * SectionPropertyForm Component
 *
 * Form for editing section properties within the property panel.
 * Allows changing heading, columns, style, section type, tab group,
 * collapsed state, and visibility rules.
 */

import React, { useMemo, useCallback } from 'react'
import { useLayoutEditor, type EditorSection } from './LayoutEditorContext'
import { VisibilityRuleEditor } from './VisibilityRuleEditor'
import { cn } from '@/lib/utils'

export interface SectionPropertyFormProps {
  sectionId: string
}

const SECTION_STYLES = ['default', 'collapsible', 'card'] as const
const SECTION_TYPES = ['fields', 'HIGHLIGHTS_PANEL'] as const

export function SectionPropertyForm({
  sectionId,
}: SectionPropertyFormProps): React.ReactElement | null {
  const { state, updateSection } = useLayoutEditor()

  const section = useMemo(
    () => state.sections.find((s) => s.id === sectionId),
    [state.sections, sectionId]
  )

  const hasExistingHighlightsPanel = useMemo(
    () => state.sections.some((s) => s.sectionType === 'HIGHLIGHTS_PANEL' && s.id !== sectionId),
    [state.sections, sectionId]
  )

  const handleUpdate = useCallback(
    (updates: Partial<EditorSection>) => {
      updateSection(sectionId, updates)
    },
    [updateSection, sectionId]
  )

  const handleHeadingChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      handleUpdate({ heading: e.target.value })
    },
    [handleUpdate]
  )

  const handleColumnsChange = useCallback(
    (columns: number) => {
      handleUpdate({ columns })
    },
    [handleUpdate]
  )

  const handleStyleChange = useCallback(
    (e: React.ChangeEvent<HTMLSelectElement>) => {
      handleUpdate({ style: e.target.value })
    },
    [handleUpdate]
  )

  const handleSectionTypeChange = useCallback(
    (e: React.ChangeEvent<HTMLSelectElement>) => {
      handleUpdate({ sectionType: e.target.value })
    },
    [handleUpdate]
  )

  const handleTabGroupChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      handleUpdate({ tabGroup: e.target.value || undefined })
    },
    [handleUpdate]
  )

  const handleTabLabelChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      handleUpdate({ tabLabel: e.target.value || undefined })
    },
    [handleUpdate]
  )

  const handleCollapsedToggle = useCallback(() => {
    if (section) {
      handleUpdate({ collapsed: !section.collapsed })
    }
  }, [handleUpdate, section])

  const handleVisibilityRuleChange = useCallback(
    (value: string | undefined) => {
      handleUpdate({ visibilityRule: value })
    },
    [handleUpdate]
  )

  if (!section) return null

  return (
    <div data-testid={`section-property-form-${sectionId}`}>
      {/* Heading */}
      <div className="flex flex-col gap-1">
        <label
          className="text-xs font-medium uppercase tracking-wider text-muted-foreground"
          htmlFor={`section-heading-${sectionId}`}
        >
          Heading
        </label>
        <input
          id={`section-heading-${sectionId}`}
          type="text"
          className="rounded-md border border-input bg-background px-2.5 py-1.5 text-[13px] text-foreground placeholder:text-muted-foreground transition-colors duration-150 focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/15 disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground motion-reduce:transition-none"
          value={section.heading}
          onChange={handleHeadingChange}
          placeholder="Section heading"
          data-testid="section-prop-heading"
        />
      </div>

      {/* Columns */}
      <div className="flex flex-col gap-1">
        <span className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
          Columns
        </span>
        <div className="flex overflow-hidden rounded-md border border-input">
          {[1, 2, 3].map((n) => (
            <button
              key={n}
              type="button"
              className={cn(
                'flex-1 border-none border-r border-input bg-background py-1.5 text-center text-xs text-muted-foreground cursor-pointer transition-colors duration-150 last:border-r-0 motion-reduce:transition-none',
                'focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-[-2px]',
                section.columns === n ? 'bg-primary text-primary-foreground' : 'hover:bg-muted'
              )}
              onClick={() => handleColumnsChange(n)}
              data-testid={`section-prop-col-${n}`}
            >
              {n}
            </button>
          ))}
        </div>
      </div>

      {/* Style */}
      <div className="flex flex-col gap-1">
        <label
          className="text-xs font-medium uppercase tracking-wider text-muted-foreground"
          htmlFor={`section-style-${sectionId}`}
        >
          Style
        </label>
        <select
          id={`section-style-${sectionId}`}
          className="cursor-pointer appearance-none rounded-md border border-input bg-background bg-[url('data:image/svg+xml,%3csvg%20xmlns=%27http://www.w3.org/2000/svg%27%20fill=%27none%27%20viewBox=%270%200%2020%2020%27%3e%3cpath%20stroke=%27%236b7280%27%20stroke-linecap=%27round%27%20stroke-linejoin=%27round%27%20stroke-width=%271.5%27%20d=%27M6%208l4%204%204-4%27/%3e%3c/svg%3e')] bg-[length:1.25em_1.25em] bg-[right_6px_center] bg-no-repeat px-2.5 py-1.5 pr-7 text-[13px] text-foreground transition-colors duration-150 focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/15 disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground motion-reduce:transition-none"
          value={section.style}
          onChange={handleStyleChange}
          data-testid="section-prop-style"
        >
          {SECTION_STYLES.map((s) => (
            <option key={s} value={s}>
              {s.charAt(0).toUpperCase() + s.slice(1)}
            </option>
          ))}
        </select>
      </div>

      {/* Section Type */}
      <div className="flex flex-col gap-1">
        <label
          className="text-xs font-medium uppercase tracking-wider text-muted-foreground"
          htmlFor={`section-type-${sectionId}`}
        >
          Section Type
        </label>
        <select
          id={`section-type-${sectionId}`}
          className="cursor-pointer appearance-none rounded-md border border-input bg-background bg-[url('data:image/svg+xml,%3csvg%20xmlns=%27http://www.w3.org/2000/svg%27%20fill=%27none%27%20viewBox=%270%200%2020%2020%27%3e%3cpath%20stroke=%27%236b7280%27%20stroke-linecap=%27round%27%20stroke-linejoin=%27round%27%20stroke-width=%271.5%27%20d=%27M6%208l4%204%204-4%27/%3e%3c/svg%3e')] bg-[length:1.25em_1.25em] bg-[right_6px_center] bg-no-repeat px-2.5 py-1.5 pr-7 text-[13px] text-foreground transition-colors duration-150 focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/15 disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground motion-reduce:transition-none"
          value={section.sectionType}
          onChange={handleSectionTypeChange}
          data-testid="section-prop-type"
        >
          {SECTION_TYPES.map((t) => (
            <option
              key={t}
              value={t}
              disabled={t === 'HIGHLIGHTS_PANEL' && hasExistingHighlightsPanel}
            >
              {t === 'fields' ? 'Standard' : 'Highlights Panel'}
            </option>
          ))}
        </select>
      </div>

      <hr className="my-1 border-t border-border" />

      {/* Tab Group */}
      <div className="flex flex-col gap-1">
        <label
          className="text-xs font-medium uppercase tracking-wider text-muted-foreground"
          htmlFor={`section-tab-group-${sectionId}`}
        >
          Tab Group
        </label>
        <input
          id={`section-tab-group-${sectionId}`}
          type="text"
          className="rounded-md border border-input bg-background px-2.5 py-1.5 text-[13px] text-foreground placeholder:text-muted-foreground transition-colors duration-150 focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/15 disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground motion-reduce:transition-none"
          value={section.tabGroup ?? ''}
          onChange={handleTabGroupChange}
          placeholder="Tab group name"
          data-testid="section-prop-tab-group"
        />
      </div>

      {/* Tab Label (shown only when tabGroup is set) */}
      {section.tabGroup && (
        <div className="flex flex-col gap-1">
          <label
            className="text-xs font-medium uppercase tracking-wider text-muted-foreground"
            htmlFor={`section-tab-label-${sectionId}`}
          >
            Tab Label
          </label>
          <input
            id={`section-tab-label-${sectionId}`}
            type="text"
            className="rounded-md border border-input bg-background px-2.5 py-1.5 text-[13px] text-foreground placeholder:text-muted-foreground transition-colors duration-150 focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/15 disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground motion-reduce:transition-none"
            value={section.tabLabel ?? ''}
            onChange={handleTabLabelChange}
            placeholder="Tab label"
            data-testid="section-prop-tab-label"
          />
        </div>
      )}

      <hr className="my-1 border-t border-border" />

      {/* Collapsed by Default */}
      <div className="flex items-center justify-between py-2">
        <span className="text-[13px] text-foreground">Collapsed by Default</span>
        <button
          type="button"
          className={cn(
            'relative h-5 w-9 cursor-pointer rounded-full border-none p-0 transition-colors duration-200 motion-reduce:transition-none',
            'focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-2',
            section.collapsed ? 'bg-primary' : 'bg-input'
          )}
          onClick={handleCollapsedToggle}
          role="switch"
          aria-checked={section.collapsed}
          data-testid="section-prop-collapsed"
        >
          <span
            className={cn(
              'absolute top-0.5 h-4 w-4 rounded-full bg-background transition-[left] duration-200 motion-reduce:transition-none',
              section.collapsed ? 'left-[18px]' : 'left-0.5'
            )}
          />
        </button>
      </div>

      <hr className="my-1 border-t border-border" />

      {/* Visibility Rule */}
      <div className="flex flex-col gap-1">
        <span className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
          Visibility Rule
        </span>
        <VisibilityRuleEditor
          value={section.visibilityRule}
          onChange={handleVisibilityRuleChange}
          fields={state.availableFields}
        />
      </div>
    </div>
  )
}
