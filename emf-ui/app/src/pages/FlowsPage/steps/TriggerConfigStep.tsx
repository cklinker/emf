import React from 'react'
import {
  RecordTriggerForm,
  ScheduledTriggerForm,
  AutolaunchedTriggerForm,
  KafkaTriggerForm,
} from '@/components/flows'
import type { FlowType, TriggerConfig } from '@/pages/FlowDesignerPage/types'

interface TriggerConfigStepProps {
  flowType: FlowType
  config: Partial<TriggerConfig>
  onChange: (config: Partial<TriggerConfig>) => void
}

export function TriggerConfigStep({ flowType, config, onChange }: TriggerConfigStepProps) {
  return (
    <div className="flex flex-col gap-4">
      <div>
        <h3 className="text-base font-semibold text-foreground">Configure Trigger</h3>
        <p className="mt-1 text-sm text-muted-foreground">
          Set up the conditions that will start this flow.
        </p>
      </div>

      <div>
        {flowType === 'RECORD_TRIGGERED' && (
          <RecordTriggerForm config={config} onChange={onChange} />
        )}
        {flowType === 'SCHEDULED' && <ScheduledTriggerForm config={config} onChange={onChange} />}
        {flowType === 'AUTOLAUNCHED' && (
          <AutolaunchedTriggerForm config={config} onChange={onChange} />
        )}
        {flowType === 'KAFKA_TRIGGERED' && <KafkaTriggerForm config={config} onChange={onChange} />}
      </div>
    </div>
  )
}
