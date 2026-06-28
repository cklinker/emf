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

      {(() => {
        // The TriggerConfig union is narrowed by flowType at runtime; cast here.
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const c = config as any
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const onC = onChange as any
        return (
          <div>
            {flowType === 'RECORD_TRIGGERED' && <RecordTriggerForm config={c} onChange={onC} />}
            {flowType === 'SCHEDULED' && <ScheduledTriggerForm config={c} onChange={onC} />}
            {flowType === 'AUTOLAUNCHED' && <AutolaunchedTriggerForm config={c} onChange={onC} />}
            {flowType === 'KAFKA_TRIGGERED' && <KafkaTriggerForm config={c} onChange={onC} />}
          </div>
        )
      })()}
    </div>
  )
}
