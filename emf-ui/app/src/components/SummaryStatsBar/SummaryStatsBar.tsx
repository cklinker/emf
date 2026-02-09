/**
 * SummaryStatsBar Component
 *
 * Displays a summary bar above data tables showing record count,
 * filter description, and optional field aggregates (sums for numeric fields).
 *
 * Features:
 * - Total and filtered record counts
 * - Human-readable filter description with operator labels
 * - Client-side sum aggregates for numeric fields
 * - Clear filters action
 * - Locale-aware number formatting
 * - Internationalization via I18n context
 */

import React, { useMemo } from 'react'
import { useI18n } from '../../context/I18nContext'
import styles from './SummaryStatsBar.module.css'

/**
 * A single filter condition applied to the data
 */
export interface FilterCondition {
  id: string
  field: string
  operator: string
  value: string
}

/**
 * Definition of a field in the collection
 */
export interface FieldDefinition {
  name: string
  displayName?: string
  type: string
}

/**
 * Props for the SummaryStatsBar component
 */
export interface SummaryStatsBarProps {
  /** Total number of records in the collection */
  totalCount: number
  /** Number of records after filters are applied */
  filteredCount: number
  /** Active filter conditions */
  filters: FilterCondition[]
  /** Fields currently visible in the table */
  visibleFields: FieldDefinition[]
  /** Currently visible page of records */
  records: Array<Record<string, unknown>>
  /** Callback when the clear filters link is clicked */
  onClearFilters: () => void
}

/**
 * Map of filter operators to human-readable labels
 */
const OPERATOR_LABELS: Record<string, string> = {
  equals: '=',
  not_equals: '\u2260',
  contains: 'contains',
  starts_with: 'starts with',
  ends_with: 'ends with',
  greater_than: '>',
  less_than: '<',
  greater_than_or_equal: '\u2265',
  less_than_or_equal: '\u2264',
}

/**
 * Numeric field types that support aggregation
 */
const NUMERIC_FIELD_TYPES = new Set([
  'number',
  'integer',
  'long',
  'double',
  'float',
  'decimal',
  'currency',
  'percent',
])

/**
 * Build a human-readable filter description from filter conditions.
 *
 * Converts filters like:
 *   [{ field: 'Status', operator: 'equals', value: 'Active' }]
 * into:
 *   "Status = Active"
 */
function buildFilterDescription(filters: FilterCondition[]): string {
  if (filters.length === 0) {
    return ''
  }

  return filters
    .map((filter) => {
      const operatorLabel = OPERATOR_LABELS[filter.operator] || filter.operator
      return `${filter.field} ${operatorLabel} ${filter.value}`
    })
    .join(' AND ')
}

/**
 * Compute sum aggregates for numeric fields from the visible records.
 *
 * Returns an array of { label, value } for each numeric field that has
 * at least one numeric value in the records.
 */
function computeAggregates(
  visibleFields: FieldDefinition[],
  records: Array<Record<string, unknown>>
): Array<{ label: string; sum: number }> {
  if (records.length === 0) {
    return []
  }

  const numericFields = visibleFields.filter((field) =>
    NUMERIC_FIELD_TYPES.has(field.type.toLowerCase())
  )

  const aggregates: Array<{ label: string; sum: number }> = []

  for (const field of numericFields) {
    let sum = 0
    let hasNumericValue = false

    for (const record of records) {
      const rawValue = record[field.name]
      if (rawValue === null || rawValue === undefined || rawValue === '') {
        continue
      }
      const numValue = typeof rawValue === 'number' ? rawValue : Number(rawValue)
      if (!isNaN(numValue)) {
        sum += numValue
        hasNumericValue = true
      }
    }

    if (hasNumericValue) {
      aggregates.push({
        label: field.displayName || field.name,
        sum,
      })
    }
  }

  return aggregates
}

/**
 * SummaryStatsBar Component
 *
 * Renders a compact bar with record counts, filter descriptions,
 * and optional numeric field aggregates.
 *
 * @example
 * ```tsx
 * <SummaryStatsBar
 *   totalCount={150}
 *   filteredCount={42}
 *   filters={[{ id: '1', field: 'Status', operator: 'equals', value: 'Active' }]}
 *   visibleFields={[{ name: 'amount', displayName: 'Amount', type: 'number' }]}
 *   records={records}
 *   onClearFilters={() => clearFilters()}
 * />
 * ```
 */
export function SummaryStatsBar({
  totalCount,
  filteredCount,
  filters,
  visibleFields,
  records,
  onClearFilters,
}: SummaryStatsBarProps): React.ReactElement {
  const { t } = useI18n()

  const hasActiveFilters = filters.length > 0

  const filterDescription = useMemo(() => buildFilterDescription(filters), [filters])

  const aggregates = useMemo(
    () => computeAggregates(visibleFields, records),
    [visibleFields, records]
  )

  return (
    <div className={styles.bar} data-testid="summary-stats-bar">
      {/* Left side: record count and filter info */}
      <div className={styles.leftSide}>
        <span className={styles.recordCount} data-testid="summary-record-count">
          {t('summaryStats.recordCount', { count: totalCount })}
        </span>

        {hasActiveFilters ? (
          <>
            <span className={styles.separator} aria-hidden="true">
              &middot;
            </span>
            <span data-testid="summary-filtered-count">
              {t('summaryStats.filteredCount', { count: filteredCount })}
            </span>
            <span className={styles.separator} aria-hidden="true">
              &middot;
            </span>
            <span
              className={styles.filterDescription}
              title={filterDescription}
              data-testid="summary-filter-description"
            >
              {filterDescription}
            </span>
            <button
              type="button"
              className={styles.clearFilters}
              onClick={onClearFilters}
              data-testid="summary-clear-filters"
            >
              {t('summaryStats.clearFilters')}
            </button>
          </>
        ) : (
          <>
            <span className={styles.separator} aria-hidden="true">
              &middot;
            </span>
            <span data-testid="summary-showing-all">{t('summaryStats.showingAll')}</span>
          </>
        )}
      </div>

      {/* Right side: aggregates for numeric fields */}
      {aggregates.length > 0 && (
        <div className={styles.rightSide} data-testid="summary-aggregates">
          {aggregates.map((agg, index) => (
            <React.Fragment key={agg.label}>
              {index > 0 && (
                <span className={styles.separator} aria-hidden="true">
                  &middot;
                </span>
              )}
              <span className={styles.aggregate} data-testid={`summary-aggregate-${agg.label}`}>
                {t('summaryStats.sumOf', {
                  field: agg.label,
                  value: agg.sum.toLocaleString(),
                })}
              </span>
            </React.Fragment>
          ))}
        </div>
      )}
    </div>
  )
}

export default SummaryStatsBar
