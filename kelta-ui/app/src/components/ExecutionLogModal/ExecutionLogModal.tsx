/**
 * ExecutionLogModal Component
 *
 * A modal dialog for displaying execution logs in a tabular format.
 * Built on shadcn Dialog + ScrollArea with Tailwind CSS styling.
 *
 * Features:
 * - Modal overlay with centered dialog
 * - Table display with configurable columns
 * - Loading, error, and empty states
 * - Scrollable content area via ScrollArea
 * - Accessible with ARIA dialog role
 */

import React from 'react'
import { LoadingSpinner, ErrorMessage } from '..'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from '@/components/ui/dialog'
import { ScrollArea } from '@/components/ui/scroll-area'

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
  return (
    <Dialog open={true} onOpenChange={(open) => !open && onClose()}>
      <DialogContent
        className="sm:max-w-[900px] max-h-[80vh] flex flex-col p-0"
        data-testid="execution-log-modal"
      >
        {/* Header */}
        <DialogHeader className="px-6 pt-5 pb-4 border-b">
          <DialogTitle
            id="execution-log-title"
            className="text-lg font-semibold"
            data-testid="execution-log-title"
          >
            {title}
          </DialogTitle>
          {subtitle && (
            <DialogDescription className="text-sm text-muted-foreground mt-1">
              {subtitle}
            </DialogDescription>
          )}
          {!subtitle && <DialogDescription className="sr-only">{title}</DialogDescription>}
        </DialogHeader>

        {/* Body */}
        <ScrollArea className="flex-1 px-6 py-5">
          {isLoading ? (
            <div className="flex items-center justify-center p-8">
              <LoadingSpinner size="medium" label="Loading logs..." />
            </div>
          ) : error ? (
            <ErrorMessage error={error} />
          ) : data.length === 0 ? (
            <div className="flex items-center justify-center p-8">
              <p className="text-sm text-muted-foreground">{emptyMessage}</p>
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
                        className="px-3 py-2 text-left text-xs font-semibold uppercase text-muted-foreground border-b whitespace-nowrap sticky top-0 bg-background"
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
                      className="hover:bg-muted/50 transition-colors"
                    >
                      {columns.map((col) => (
                        <td
                          key={col.key}
                          className="px-3 py-2 text-left border-b whitespace-nowrap"
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
        </ScrollArea>
      </DialogContent>
    </Dialog>
  )
}
