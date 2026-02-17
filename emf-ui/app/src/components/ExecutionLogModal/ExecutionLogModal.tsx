import React, { useCallback } from 'react'
import { LoadingSpinner, ErrorMessage } from '..'

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
      className="fixed inset-0 bg-black/50 flex items-center justify-center z-50"
      onClick={(e) => e.target === e.currentTarget && onClose()}
      onKeyDown={handleKeyDown}
      role="presentation"
      data-testid="execution-log-overlay"
    >
      <div
        className="bg-card rounded-lg w-full max-w-[900px] max-h-[80vh] flex flex-col shadow-xl"
        role="dialog"
        aria-modal="true"
        aria-labelledby="execution-log-title"
        data-testid="execution-log-modal"
      >
        <div className="flex justify-between items-start px-6 py-5 border-b border-border">
          <div>
            <h2 id="execution-log-title" className="m-0 text-lg font-semibold">
              {title}
            </h2>
            {subtitle && <p className="mt-1 mb-0 text-sm text-muted-foreground">{subtitle}</p>}
          </div>
          <button
            type="button"
            className="bg-transparent border-none text-2xl cursor-pointer px-1 leading-none text-muted-foreground hover:text-foreground"
            onClick={onClose}
            aria-label="Close"
            data-testid="execution-log-close"
          >
            &times;
          </button>
        </div>
        <div className="px-6 py-5 overflow-y-auto flex-1">
          {isLoading ? (
            <div className="flex items-center justify-center p-8">
              <LoadingSpinner size="medium" label="Loading logs..." />
            </div>
          ) : error ? (
            <ErrorMessage error={error} />
          ) : data.length === 0 ? (
            <div className="flex items-center justify-center p-8">
              <p className="text-muted-foreground text-sm">{emptyMessage}</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table
                className="w-full border-collapse text-[0.8125rem]"
                role="grid"
                aria-label={title}
              >
                <thead>
                  <tr>
                    {columns.map((col) => (
                      <th
                        key={col.key}
                        className="px-3 py-2 text-left border-b border-border whitespace-nowrap font-semibold text-xs uppercase text-muted-foreground sticky top-0 bg-card"
                      >
                        {col.header}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {data.map((row, rowIndex) => (
                    <tr
                      key={((row as Record<string, unknown>).id as string) ?? rowIndex}
                      className="hover:bg-accent"
                    >
                      {columns.map((col) => (
                        <td
                          key={col.key}
                          className="px-3 py-2 text-left border-b border-border whitespace-nowrap"
                        >
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
