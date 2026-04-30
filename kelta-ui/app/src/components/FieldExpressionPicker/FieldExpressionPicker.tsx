import React, { useCallback, useState } from 'react'
import { Braces, FunctionSquare } from 'lucide-react'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { FieldsTab } from './FieldsTab'
import { FunctionsTab } from './FunctionsTab'
import type { FieldExpressionMode, StaticNamespace } from './types'
import type { FieldType } from '../../hooks/useCollectionSchema'

export interface FieldExpressionPickerProps {
  /** Whether the dialog is open. */
  open: boolean
  /** Called when the dialog should close. */
  onOpenChange: (open: boolean) => void
  /** Root collection id, or null when only namespaces are available. */
  rootCollectionId: string | null
  /** Static envelope namespaces (e.g. recipient, currentUser). */
  staticNamespaces?: StaticNamespace[]
  /**
   * `expression` (default) — both Fields and Functions tabs are available; the
   * inserted token includes function syntax when a function is chosen.
   *
   * `path-only` — only Fields tab is shown; only field paths can be inserted.
   */
  mode?: FieldExpressionMode
  /** Restrict the Functions tab to functions with these return types. */
  allowedTypes?: FieldType[]
  /**
   * Called when the user clicks Insert. The argument is the assembled token —
   * for fields, the dot-notation path (e.g. `account_id.name`); for functions,
   * the function call (e.g. `IF(${condition}, ${then}, ${else})`).
   *
   * Callers wrap with `{{…}}` if they need merge-tag syntax.
   */
  onInsert: (token: string) => void
  /** Title shown in the dialog header. */
  title?: string
  testId?: string
}

/**
 * Reusable picker for choosing a field path or function call against a
 * collection's schema.
 *
 * Used as the standard primitive anywhere a user expresses a value derived
 * from a record — email templates, workflow actions, validation rules,
 * approvals, webhooks, etc.
 */
export function FieldExpressionPicker({
  open,
  onOpenChange,
  rootCollectionId,
  staticNamespaces,
  mode = 'expression',
  allowedTypes,
  onInsert,
  title = 'Insert field or function',
  testId = 'field-expression-picker',
}: FieldExpressionPickerProps): React.ReactElement {
  const [tab, setTab] = useState<'fields' | 'functions'>('fields')
  const [path, setPath] = useState('')
  const [pathTypeChip, setPathTypeChip] = useState<string | null>(null)
  const [pendingFunctionStub, setPendingFunctionStub] = useState<string | null>(null)

  const reset = useCallback(() => {
    setPath('')
    setPathTypeChip(null)
    setPendingFunctionStub(null)
    setTab('fields')
  }, [])

  const handleInsert = useCallback(() => {
    if (pendingFunctionStub) {
      onInsert(pendingFunctionStub)
    } else if (path) {
      onInsert(path)
    } else {
      return
    }
    reset()
    onOpenChange(false)
  }, [path, pendingFunctionStub, onInsert, onOpenChange, reset])

  const handleClose = useCallback(
    (next: boolean) => {
      if (!next) reset()
      onOpenChange(next)
    },
    [onOpenChange, reset]
  )

  const showFunctionsTab = mode === 'expression'
  const previewExpression = pendingFunctionStub ?? path
  const canInsert = !!previewExpression

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent
        className="!max-w-[1100px] h-[80vh] gap-0 overflow-hidden p-0 sm:max-w-[1100px]"
        showCloseButton={false}
        data-testid={testId}
      >
        <DialogHeader className="border-b border-border px-6 py-4">
          <div className="flex items-start justify-between gap-4">
            <div className="flex flex-col gap-2">
              <DialogTitle>{title}</DialogTitle>
              <div className="flex items-center gap-2 text-xs">
                <span className="text-muted-foreground">Selected:</span>
                <code
                  className={cn(
                    'rounded bg-muted px-2 py-1 font-mono',
                    !previewExpression && 'text-muted-foreground'
                  )}
                  data-testid={`${testId}-preview`}
                >
                  {previewExpression ? `{{${previewExpression}}}` : 'Pick a field or a function'}
                </code>
                {pathTypeChip && !pendingFunctionStub && (
                  <span className="text-muted-foreground">
                    Type: <span className="font-medium text-foreground">{pathTypeChip}</span>
                  </span>
                )}
              </div>
            </div>
            <div className="flex items-center gap-2">
              <Button
                type="button"
                variant="outline"
                onClick={() => handleClose(false)}
                data-testid={`${testId}-cancel`}
              >
                Cancel
              </Button>
              <Button
                type="button"
                onClick={handleInsert}
                disabled={!canInsert}
                data-testid={`${testId}-insert`}
              >
                Insert
              </Button>
            </div>
          </div>
        </DialogHeader>

        <div className="flex flex-1 overflow-hidden">
          {showFunctionsTab && (
            <div className="flex w-44 shrink-0 flex-col border-r border-border bg-muted/20">
              <button
                type="button"
                onClick={() => setTab('fields')}
                className={cn(
                  'flex items-center gap-2 px-4 py-3 text-left text-sm transition-colors',
                  tab === 'fields'
                    ? 'border-r-2 border-primary bg-background font-medium text-foreground'
                    : 'text-muted-foreground hover:bg-muted/50'
                )}
                data-testid={`${testId}-tab-fields`}
              >
                <Braces className="h-4 w-4" />
                Fields
              </button>
              <button
                type="button"
                onClick={() => setTab('functions')}
                className={cn(
                  'flex items-center gap-2 px-4 py-3 text-left text-sm transition-colors',
                  tab === 'functions'
                    ? 'border-r-2 border-primary bg-background font-medium text-foreground'
                    : 'text-muted-foreground hover:bg-muted/50'
                )}
                data-testid={`${testId}-tab-functions`}
              >
                <FunctionSquare className="h-4 w-4" />
                Functions
              </button>
            </div>
          )}

          <div className="flex flex-1 overflow-hidden">
            {tab === 'fields' || !showFunctionsTab ? (
              <FieldsTab
                rootCollectionId={rootCollectionId}
                staticNamespaces={staticNamespaces}
                value={path}
                onChange={(nextPath, leaf) => {
                  setPendingFunctionStub(null)
                  setPath(nextPath)
                  setPathTypeChip(leaf && !leaf.nextCollectionId ? leaf.type : null)
                }}
                testId={`${testId}-fields`}
              />
            ) : (
              <FunctionsTab
                allowedReturnTypes={allowedTypes}
                onInsert={(stub) => {
                  setPendingFunctionStub(stub)
                  setPath('')
                  setPathTypeChip(null)
                }}
                testId={`${testId}-functions`}
              />
            )}
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}
