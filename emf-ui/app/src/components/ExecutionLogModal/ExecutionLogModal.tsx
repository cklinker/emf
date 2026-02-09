import React, { useCallback } from 'react'
import { LoadingSpinner, ErrorMessage } from '..'
import styles from './ExecutionLogModal.module.css'

export interface LogColumn<T> {
  key: string
  header: string
  render?: (value: unknown, row: T) => React.ReactNode
}

export interface ExecutionLogModalProps<T> {
  title: string
  subtitle?: string
  columns: LogColumn<T>[]
  data: T[]
  isLoading: boolean
  error: Error | null
  onClose: () => void
  emptyMessage?: string
}

export function ExecutionLogModal<T extends Record<string, unknown>>({
  title,
  subtitle,
  columns,
  data,
  isLoading,
  error,
  onClose,
  emptyMessage = 'No logs found.',
}: ExecutionLogModalProps<T>): React.ReactElement {
  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose()
      }
    },
    [onClose]
  )

  return (
    <div
      className={styles.overlay}
      onClick={(e) => e.target === e.currentTarget && onClose()}
      onKeyDown={handleKeyDown}
      role="presentation"
      data-testid="execution-log-overlay"
    >
      <div
        className={styles.modal}
        role="dialog"
        aria-modal="true"
        aria-labelledby="execution-log-title"
        data-testid="execution-log-modal"
      >
        <div className={styles.header}>
          <div>
            <h2 id="execution-log-title" className={styles.title}>
              {title}
            </h2>
            {subtitle && <p className={styles.subtitle}>{subtitle}</p>}
          </div>
          <button
            type="button"
            className={styles.closeButton}
            onClick={onClose}
            aria-label="Close"
            data-testid="execution-log-close"
          >
            &times;
          </button>
        </div>
        <div className={styles.body}>
          {isLoading ? (
            <div className={styles.centered}>
              <LoadingSpinner size="medium" label="Loading logs..." />
            </div>
          ) : error ? (
            <ErrorMessage error={error} />
          ) : data.length === 0 ? (
            <div className={styles.centered}>
              <p className={styles.emptyMessage}>{emptyMessage}</p>
            </div>
          ) : (
            <div className={styles.tableContainer}>
              <table className={styles.table} role="grid" aria-label={title}>
                <thead>
                  <tr>
                    {columns.map((col) => (
                      <th key={col.key}>{col.header}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {data.map((row, rowIndex) => (
                    <tr key={((row as Record<string, unknown>).id as string) ?? rowIndex}>
                      {columns.map((col) => (
                        <td key={col.key}>
                          {col.render
                            ? col.render(row[col.key], row)
                            : row[col.key] != null
                              ? String(row[col.key])
                              : '-'}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
