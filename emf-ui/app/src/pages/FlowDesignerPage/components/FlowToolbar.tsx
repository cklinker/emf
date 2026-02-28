import React from 'react'
import { ArrowLeft, Save, Code, CheckCircle } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { Button } from '@/components/ui/button'

interface FlowToolbarProps {
  flowName: string
  flowType: string
  isActive: boolean
  isDirty: boolean
  isSaving: boolean
  showJson: boolean
  onSave: () => void
  onToggleJson: () => void
  onActivate?: () => void
}

const flowTypeLabels: Record<string, string> = {
  RECORD_TRIGGERED: 'Record-Triggered',
  SCHEDULED: 'Scheduled',
  AUTOLAUNCHED: 'API / Manual',
  KAFKA_TRIGGERED: 'Kafka',
  SCREEN: 'Screen',
}

export function FlowToolbar({
  flowName,
  flowType,
  isActive,
  isDirty,
  isSaving,
  showJson,
  onSave,
  onToggleJson,
  onActivate,
}: FlowToolbarProps) {
  const navigate = useNavigate()

  return (
    <div className="flex h-12 shrink-0 items-center justify-between border-b border-border bg-card px-4">
      <div className="flex items-center gap-3">
        <button
          onClick={() => navigate(-1)}
          className="rounded p-1 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
          aria-label="Back to flows"
        >
          <ArrowLeft className="h-4 w-4" />
        </button>
        <div className="flex items-center gap-2">
          <h1 className="text-sm font-semibold text-foreground">{flowName || 'Untitled Flow'}</h1>
          <span className="rounded-full bg-muted px-2 py-0.5 text-[10px] font-medium uppercase tracking-wider text-muted-foreground">
            {flowTypeLabels[flowType] || flowType}
          </span>
          {isActive && (
            <span className="rounded-full bg-emerald-100 px-2 py-0.5 text-[10px] font-semibold text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300">
              Active
            </span>
          )}
          {isDirty && (
            <span className="h-2 w-2 rounded-full bg-amber-500" title="Unsaved changes" />
          )}
        </div>
      </div>

      <div className="flex items-center gap-2">
        <Button
          variant="outline"
          size="sm"
          onClick={onToggleJson}
          className={showJson ? 'bg-muted' : ''}
        >
          <Code className="mr-1 h-3.5 w-3.5" />
          JSON
        </Button>
        <Button variant="outline" size="sm" onClick={onSave} disabled={isSaving || !isDirty}>
          <Save className="mr-1 h-3.5 w-3.5" />
          {isSaving ? 'Saving...' : 'Save'}
        </Button>
        {onActivate && !isActive && (
          <Button size="sm" onClick={onActivate}>
            <CheckCircle className="mr-1 h-3.5 w-3.5" />
            Activate
          </Button>
        )}
      </div>
    </div>
  )
}
