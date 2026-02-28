import React, { useState, useMemo } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetFooter } from '@/components/ui/sheet'
import { Button } from '@/components/ui/button'
import {
  RecordTriggerForm,
  ScheduledTriggerForm,
  AutolaunchedTriggerForm,
  KafkaTriggerForm,
} from '@/components/flows'
import { useApi } from '@/context/ApiContext'
import { useToast } from '@/components'
import type { Flow, TriggerConfig } from '../types'

interface TriggerEditSheetProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  flow: Flow
}

function parseTriggerConfig(flow: Flow): Partial<TriggerConfig> {
  if (!flow.triggerConfig) return {}
  try {
    return JSON.parse(flow.triggerConfig) as Partial<TriggerConfig>
  } catch {
    return {}
  }
}

export function TriggerEditSheet({ open, onOpenChange, flow }: TriggerEditSheetProps) {
  const { apiClient } = useApi()
  const { showToast } = useToast()
  const queryClient = useQueryClient()
  const initialConfig = useMemo(() => parseTriggerConfig(flow), [flow])
  const [config, setConfig] = useState<Partial<TriggerConfig>>({})
  const [initialized, setInitialized] = useState(false)

  // Reset config when sheet opens
  if (open && !initialized) {
    setConfig(initialConfig)
    setInitialized(true)
  }
  if (!open && initialized) {
    setInitialized(false)
  }

  const saveMutation = useMutation({
    mutationFn: async () => {
      await apiClient.putResource(`/api/flows/${flow.id}`, {
        ...flow,
        triggerConfig: JSON.stringify(config),
      })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['flow', flow.id] })
      showToast('Trigger configuration saved', 'success')
      onOpenChange(false)
    },
    onError: (err: Error) => {
      showToast(err.message || 'Failed to save trigger config', 'error')
    },
  })

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="w-[400px] sm:max-w-[400px]">
        <SheetHeader>
          <SheetTitle>Edit Trigger</SheetTitle>
        </SheetHeader>

        <div className="flex-1 overflow-y-auto px-4 py-4">
          <TriggerFormRouter flowType={flow.flowType} config={config} onChange={setConfig} />
        </div>

        <SheetFooter className="px-4 pb-4">
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={() => saveMutation.mutate()} disabled={saveMutation.isPending}>
            {saveMutation.isPending ? 'Saving...' : 'Save'}
          </Button>
        </SheetFooter>
      </SheetContent>
    </Sheet>
  )
}

function TriggerFormRouter({
  flowType,
  config,
  onChange,
}: {
  flowType: string
  config: Partial<TriggerConfig>
  onChange: (config: Partial<TriggerConfig>) => void
}) {
  switch (flowType) {
    case 'RECORD_TRIGGERED':
      return <RecordTriggerForm config={config} onChange={onChange} />
    case 'SCHEDULED':
      return <ScheduledTriggerForm config={config} onChange={onChange} />
    case 'AUTOLAUNCHED':
      return <AutolaunchedTriggerForm config={config} onChange={onChange} />
    case 'KAFKA_TRIGGERED':
      return <KafkaTriggerForm config={config} onChange={onChange} />
    default:
      return <div className="text-sm text-muted-foreground">Unknown flow type: {flowType}</div>
  }
}
