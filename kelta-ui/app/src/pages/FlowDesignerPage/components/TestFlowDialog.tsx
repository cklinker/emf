import React, { useState } from 'react'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { Label } from '@/components/ui/label'
import { Play } from 'lucide-react'

interface TestFlowDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  flowType: string
  onSubmit: (state: Record<string, unknown>) => void
  isLoading?: boolean
}

const RECORD_TRIGGERED_TEMPLATE = {
  record: {
    id: '',
    collection: '',
    event: 'UPDATED',
    data: {},
  },
}

const DEFAULT_TEMPLATE = {
  input: {},
}

const flowTypeLabels: Record<string, string> = {
  RECORD_TRIGGERED: 'Record-Triggered',
  SCHEDULED: 'Scheduled',
  AUTOLAUNCHED: 'API / Manual',
  KAFKA_TRIGGERED: 'Kafka',
  SCREEN: 'Screen',
}

function getTemplateForFlowType(flowType: string): object {
  if (flowType === 'RECORD_TRIGGERED') {
    return RECORD_TRIGGERED_TEMPLATE
  }
  return DEFAULT_TEMPLATE
}

export function TestFlowDialog({
  open,
  onOpenChange,
  flowType,
  onSubmit,
  isLoading = false,
}: TestFlowDialogProps) {
  const [jsonValue, setJsonValue] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [initialized, setInitialized] = useState(false)

  // Reset the editor content when the dialog opens (synchronous pattern to avoid lint error)
  if (open && !initialized) {
    const template = getTemplateForFlowType(flowType)
    setJsonValue(JSON.stringify(template, null, 2))
    setError(null)
    setInitialized(true)
  }
  if (!open && initialized) {
    setInitialized(false)
  }

  const handleJsonChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setJsonValue(e.target.value)
    // Clear error on edit so the user gets fresh feedback on submit
    if (error) {
      setError(null)
    }
  }

  const handleSubmit = () => {
    try {
      const parsed = JSON.parse(jsonValue)
      if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
        setError('State must be a JSON object (not an array or primitive).')
        return
      }
      setError(null)
      onSubmit(parsed as Record<string, unknown>)
    } catch (e) {
      const message = e instanceof SyntaxError ? e.message : 'Invalid JSON'
      setError(message)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[540px]">
        <DialogHeader>
          <DialogTitle>Test Flow Execution</DialogTitle>
          <DialogDescription>
            Provide the initial state data for this flow execution. The state will be available to
            all steps during the test run.
          </DialogDescription>
        </DialogHeader>

        <div className="flex flex-col gap-4">
          <div className="flex items-center gap-2">
            <span className="text-xs text-muted-foreground">Flow type:</span>
            <span className="rounded-full bg-muted px-2 py-0.5 text-[10px] font-medium uppercase tracking-wider text-muted-foreground">
              {flowTypeLabels[flowType] || flowType}
            </span>
          </div>

          <div className="flex flex-col gap-1.5">
            <Label className="text-xs">Initial State (JSON)</Label>
            <Textarea
              value={jsonValue}
              onChange={handleJsonChange}
              className="min-h-[200px] font-mono text-xs leading-relaxed"
              placeholder='{ "input": {} }'
              spellCheck={false}
            />
            {error && <p className="text-xs text-destructive">{error}</p>}
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={isLoading}>
            Cancel
          </Button>
          <Button onClick={handleSubmit} disabled={isLoading}>
            <Play className="mr-1 h-3.5 w-3.5" />
            {isLoading ? 'Running...' : 'Run Test'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
