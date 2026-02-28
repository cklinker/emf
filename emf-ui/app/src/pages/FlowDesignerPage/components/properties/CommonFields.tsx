import React from 'react'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Badge } from '@/components/ui/badge'

interface CommonFieldsProps {
  nodeId: string
  stateType: string
  label: string
  comment: string
  onUpdate: (data: Record<string, unknown>) => void
}

const STATE_TYPE_COLORS: Record<string, string> = {
  Task: 'bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200',
  Choice: 'bg-amber-100 text-amber-800 dark:bg-amber-900 dark:text-amber-200',
  Wait: 'bg-purple-100 text-purple-800 dark:bg-purple-900 dark:text-purple-200',
  Pass: 'bg-slate-100 text-slate-800 dark:bg-slate-900 dark:text-slate-200',
  Parallel: 'bg-cyan-100 text-cyan-800 dark:bg-cyan-900 dark:text-cyan-200',
  Map: 'bg-teal-100 text-teal-800 dark:bg-teal-900 dark:text-teal-200',
  Fail: 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200',
  Succeed: 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200',
}

export function CommonFields({ nodeId, stateType, label, comment, onUpdate }: CommonFieldsProps) {
  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-center justify-between">
        <Badge variant="outline" className={STATE_TYPE_COLORS[stateType] || ''}>
          {stateType}
        </Badge>
        <span className="font-mono text-[10px] text-muted-foreground">{nodeId}</span>
      </div>

      <div>
        <Label htmlFor={`node-name-${nodeId}`} className="text-xs">
          Name
        </Label>
        <Input
          id={`node-name-${nodeId}`}
          value={label}
          onChange={(e) => onUpdate({ label: e.target.value })}
          className="mt-1 h-8 text-sm"
        />
      </div>

      <div>
        <Label htmlFor={`node-comment-${nodeId}`} className="text-xs">
          Comment
        </Label>
        <Textarea
          id={`node-comment-${nodeId}`}
          value={comment}
          onChange={(e) => onUpdate({ comment: e.target.value })}
          className="mt-1 min-h-[60px] text-sm"
          placeholder="Optional description"
          rows={2}
        />
      </div>
    </div>
  )
}
