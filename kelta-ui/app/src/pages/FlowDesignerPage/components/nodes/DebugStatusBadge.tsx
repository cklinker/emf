import React from 'react'
import { CheckCircle2, XCircle, Loader2, SkipForward } from 'lucide-react'

export type DebugStatus = 'SUCCEEDED' | 'FAILED' | 'RUNNING' | 'SKIPPED' | null

interface DebugStatusBadgeProps {
  status: DebugStatus
  duration: string | null
  error: string | null
}

const statusIcon: Record<Exclude<DebugStatus, null>, React.ReactNode> = {
  SUCCEEDED: <CheckCircle2 className="h-3.5 w-3.5 text-emerald-600" />,
  FAILED: <XCircle className="h-3.5 w-3.5 text-red-600" />,
  RUNNING: <Loader2 className="h-3.5 w-3.5 animate-spin text-blue-600" />,
  SKIPPED: <SkipForward className="h-3.5 w-3.5 text-gray-400" />,
}

export function DebugStatusBadge({ status, duration, error }: DebugStatusBadgeProps) {
  if (!status) return null
  return (
    <>
      <div
        className="absolute -right-1.5 -top-1.5 flex h-5 w-5 items-center justify-center rounded-full bg-card shadow-sm ring-1 ring-border"
        title={error ?? status}
      >
        {statusIcon[status]}
      </div>
      {duration && (
        <div className="absolute -bottom-2 right-1 rounded-full bg-card px-1.5 py-0.5 text-[10px] font-mono leading-none text-muted-foreground shadow-sm ring-1 ring-border">
          {duration}
        </div>
      )}
    </>
  )
}
