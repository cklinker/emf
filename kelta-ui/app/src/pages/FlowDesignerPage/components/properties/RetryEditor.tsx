import React from 'react'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Button } from '@/components/ui/button'
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from '@/components/ui/accordion'
import { Plus, Trash2 } from 'lucide-react'
import type { RetryRule } from '../../types'

interface RetryEditorProps {
  retry: RetryRule[]
  onUpdate: (retry: RetryRule[]) => void
}

const DEFAULT_RETRY: RetryRule = {
  errorEquals: ['States.ALL'],
  intervalSeconds: 1,
  maxAttempts: 3,
  backoffRate: 2.0,
}

export function RetryEditor({ retry, onUpdate }: RetryEditorProps) {
  const addRetry = () => onUpdate([...retry, { ...DEFAULT_RETRY }])

  const removeRetry = (index: number) => onUpdate(retry.filter((_, i) => i !== index))

  const updateRetry = (index: number, field: keyof RetryRule, value: unknown) => {
    const updated = [...retry]
    updated[index] = { ...updated[index], [field]: value }
    onUpdate(updated)
  }

  return (
    <Accordion type="single" collapsible>
      <AccordionItem value="retry" className="border-none">
        <AccordionTrigger className="py-2 text-xs font-medium text-muted-foreground hover:no-underline">
          Retry ({retry.length})
        </AccordionTrigger>
        <AccordionContent className="flex flex-col gap-3 pb-0">
          {retry.map((rule, i) => (
            <div key={i} className="flex flex-col gap-2 rounded-md border border-border p-2">
              <div className="flex items-center justify-between">
                <span className="text-[10px] font-medium text-muted-foreground">
                  Retry #{i + 1}
                </span>
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-5 w-5"
                  onClick={() => removeRetry(i)}
                >
                  <Trash2 className="h-3 w-3" />
                </Button>
              </div>
              <div>
                <Label className="text-[10px]">Error Equals (comma-separated)</Label>
                <Input
                  value={rule.errorEquals.join(', ')}
                  onChange={(e) =>
                    updateRetry(
                      i,
                      'errorEquals',
                      e.target.value.split(',').map((s) => s.trim())
                    )
                  }
                  className="mt-0.5 h-7 text-xs"
                  placeholder="States.ALL"
                />
              </div>
              <div className="grid grid-cols-3 gap-1.5">
                <div>
                  <Label className="text-[10px]">Interval (s)</Label>
                  <Input
                    type="number"
                    value={rule.intervalSeconds}
                    onChange={(e) =>
                      updateRetry(i, 'intervalSeconds', parseInt(e.target.value) || 1)
                    }
                    className="mt-0.5 h-7 text-xs"
                    min={1}
                  />
                </div>
                <div>
                  <Label className="text-[10px]">Max Attempts</Label>
                  <Input
                    type="number"
                    value={rule.maxAttempts}
                    onChange={(e) => updateRetry(i, 'maxAttempts', parseInt(e.target.value) || 1)}
                    className="mt-0.5 h-7 text-xs"
                    min={1}
                  />
                </div>
                <div>
                  <Label className="text-[10px]">Backoff</Label>
                  <Input
                    type="number"
                    value={rule.backoffRate}
                    onChange={(e) =>
                      updateRetry(i, 'backoffRate', parseFloat(e.target.value) || 1.0)
                    }
                    className="mt-0.5 h-7 text-xs"
                    min={1}
                    step={0.5}
                  />
                </div>
              </div>
            </div>
          ))}
          <Button variant="outline" size="sm" className="h-7 text-xs" onClick={addRetry}>
            <Plus className="mr-1 h-3 w-3" />
            Add Retry
          </Button>
        </AccordionContent>
      </AccordionItem>
    </Accordion>
  )
}
