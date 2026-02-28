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
import type { CatchRule } from '../../types'

interface CatchEditorProps {
  catches: CatchRule[]
  allNodeIds: string[]
  onUpdate: (catches: CatchRule[]) => void
}

const DEFAULT_CATCH: CatchRule = {
  errorEquals: ['States.ALL'],
  resultPath: '$.error',
  next: '',
}

export function CatchEditor({ catches, allNodeIds, onUpdate }: CatchEditorProps) {
  const addCatch = () => onUpdate([...catches, { ...DEFAULT_CATCH }])

  const removeCatch = (index: number) => onUpdate(catches.filter((_, i) => i !== index))

  const updateCatch = (index: number, field: keyof CatchRule, value: unknown) => {
    const updated = [...catches]
    updated[index] = { ...updated[index], [field]: value }
    onUpdate(updated)
  }

  return (
    <Accordion type="single" collapsible>
      <AccordionItem value="catch" className="border-none">
        <AccordionTrigger className="py-2 text-xs font-medium text-muted-foreground hover:no-underline">
          Catch ({catches.length})
        </AccordionTrigger>
        <AccordionContent className="flex flex-col gap-3 pb-0">
          {catches.map((rule, i) => (
            <div key={i} className="flex flex-col gap-2 rounded-md border border-border p-2">
              <div className="flex items-center justify-between">
                <span className="text-[10px] font-medium text-muted-foreground">
                  Catch #{i + 1}
                </span>
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-5 w-5"
                  onClick={() => removeCatch(i)}
                >
                  <Trash2 className="h-3 w-3" />
                </Button>
              </div>
              <div>
                <Label className="text-[10px]">Error Equals (comma-separated)</Label>
                <Input
                  value={rule.errorEquals.join(', ')}
                  onChange={(e) =>
                    updateCatch(
                      i,
                      'errorEquals',
                      e.target.value.split(',').map((s) => s.trim())
                    )
                  }
                  className="mt-0.5 h-7 text-xs"
                  placeholder="States.ALL"
                />
              </div>
              <div>
                <Label className="text-[10px]">Result Path</Label>
                <Input
                  value={rule.resultPath}
                  onChange={(e) => updateCatch(i, 'resultPath', e.target.value)}
                  className="mt-0.5 h-7 font-mono text-xs"
                  placeholder="$.error"
                />
              </div>
              <div>
                <Label className="text-[10px]">Next State</Label>
                <select
                  value={rule.next}
                  onChange={(e) => updateCatch(i, 'next', e.target.value)}
                  className="mt-0.5 h-7 w-full rounded-md border border-border bg-background px-2 text-xs text-foreground focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
                >
                  <option value="">Select state...</option>
                  {allNodeIds.map((id) => (
                    <option key={id} value={id}>
                      {id}
                    </option>
                  ))}
                </select>
              </div>
            </div>
          ))}
          <Button variant="outline" size="sm" className="h-7 text-xs" onClick={addCatch}>
            <Plus className="mr-1 h-3 w-3" />
            Add Catch
          </Button>
        </AccordionContent>
      </AccordionItem>
    </Accordion>
  )
}
