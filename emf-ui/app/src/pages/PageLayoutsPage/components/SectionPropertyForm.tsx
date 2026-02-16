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
import styles from './PropertyPanel.module.css'

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
      <div className={styles.formGroup}>
        <label className={styles.formLabel} htmlFor={`section-heading-${sectionId}`}>
          Heading
        </label>
        <input
          id={`section-heading-${sectionId}`}
          type="text"
          className={styles.formInput}
          value={section.heading}
          onChange={handleHeadingChange}
          placeholder="Section heading"
          data-testid="section-prop-heading"
        />
      </div>

      {/* Columns */}
      <div className={styles.formGroup}>
        <span className={styles.formLabel}>Columns</span>
        <div className={styles.segmentedControl}>
          {[1, 2, 3].map((n) => (
            <button
              key={n}
              type="button"
              className={`${styles.segmentButton} ${section.columns === n ? styles.segmentButtonActive : ''}`}
              onClick={() => handleColumnsChange(n)}
              data-testid={`section-prop-col-${n}`}
            >
              {n}
            </button>
          ))}
        </div>
      </div>

      {/* Style */}
      <div className={styles.formGroup}>
        <label className={styles.formLabel} htmlFor={`section-style-${sectionId}`}>
          Style
        </label>
        <select
          id={`section-style-${sectionId}`}
          className={styles.formSelect}
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
      <div className={styles.formGroup}>
        <label className={styles.formLabel} htmlFor={`section-type-${sectionId}`}>
          Section Type
        </label>
        <select
          id={`section-type-${sectionId}`}
          className={styles.formSelect}
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

      <hr className={styles.sectionDivider} />

      {/* Tab Group */}
      <div className={styles.formGroup}>
        <label className={styles.formLabel} htmlFor={`section-tab-group-${sectionId}`}>
          Tab Group
        </label>
        <input
          id={`section-tab-group-${sectionId}`}
          type="text"
          className={styles.formInput}
          value={section.tabGroup ?? ''}
          onChange={handleTabGroupChange}
          placeholder="Tab group name"
          data-testid="section-prop-tab-group"
        />
      </div>

      {/* Tab Label (shown only when tabGroup is set) */}
      {section.tabGroup && (
        <div className={styles.formGroup}>
          <label className={styles.formLabel} htmlFor={`section-tab-label-${sectionId}`}>
            Tab Label
          </label>
          <input
            id={`section-tab-label-${sectionId}`}
            type="text"
            className={styles.formInput}
            value={section.tabLabel ?? ''}
            onChange={handleTabLabelChange}
            placeholder="Tab label"
            data-testid="section-prop-tab-label"
          />
        </div>
      )}

      <hr className={styles.sectionDivider} />

      {/* Collapsed by Default */}
      <div className={styles.toggleGroup}>
        <span className={styles.toggleLabel}>Collapsed by Default</span>
        <button
          type="button"
          className={`${styles.toggle} ${section.collapsed ? styles.toggleActive : ''}`}
          onClick={handleCollapsedToggle}
          role="switch"
          aria-checked={section.collapsed}
          data-testid="section-prop-collapsed"
        >
          <span className={styles.toggleDot} />
        </button>
      </div>

      <hr className={styles.sectionDivider} />

      {/* Visibility Rule */}
      <div className={styles.formGroup}>
        <span className={styles.formLabel}>Visibility Rule</span>
        <VisibilityRuleEditor
          value={section.visibilityRule}
          onChange={handleVisibilityRuleChange}
          fields={state.availableFields}
        />
      </div>
    </div>
  )
}
