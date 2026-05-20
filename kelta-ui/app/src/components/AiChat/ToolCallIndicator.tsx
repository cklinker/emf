import { Loader2, Check, AlertCircle } from 'lucide-react'
import type { ToolCallState } from './types'

interface ToolCallIndicatorProps {
  call: ToolCallState
}

const TOOL_LABELS: Record<string, string> = {
  get_collection_schema: 'Inspecting collection',
  query_records: 'Sampling records',
  list_picklists: 'Listing picklists',
  get_picklist: 'Loading picklist',
  list_validation_rules: 'Loading validation rules',
  list_page_layouts: 'Loading page layouts',
}

export function ToolCallIndicator({ call }: ToolCallIndicatorProps) {
  const label = TOOL_LABELS[call.name] ?? call.name
  const colorClass =
    call.status === 'error'
      ? 'border-destructive/40 text-destructive'
      : call.status === 'done'
        ? 'border-green-500/30 text-green-700 dark:text-green-400'
        : 'border-muted-foreground/30 text-muted-foreground'

  return (
    <div
      className={`mx-4 my-1 inline-flex items-center gap-2 rounded-full border px-2.5 py-1 text-xs ${colorClass}`}
    >
      {call.status === 'pending' && <Loader2 className="h-3 w-3 animate-spin" />}
      {call.status === 'done' && <Check className="h-3 w-3" />}
      {call.status === 'error' && <AlertCircle className="h-3 w-3" />}
      <span>{label}</span>
      {call.summary && <span className="opacity-70">— {call.summary}</span>}
    </div>
  )
}
