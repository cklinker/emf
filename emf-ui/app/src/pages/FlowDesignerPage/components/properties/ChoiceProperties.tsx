import React from 'react'
import { Label } from '@/components/ui/label'
import { Button } from '@/components/ui/button'
import { Plus } from 'lucide-react'
import type { ChoiceRuleUI } from '../../types'
import { ChoiceRuleEditor } from './ChoiceRuleEditor'

interface ChoicePropertiesProps {
  nodeId: string
  data: Record<string, unknown>
  allNodeIds: string[]
  onUpdate: (data: Record<string, unknown>) => void
}

export function ChoiceProperties({ nodeId, data, allNodeIds, onUpdate }: ChoicePropertiesProps) {
  const rules = (data.rules as ChoiceRuleUI[]) || []
  const defaultState = (data.defaultState as string) || ''

  const addRule = () => {
    const newRule: ChoiceRuleUI = {
      variable: '',
      operator: 'StringEquals',
      value: '',
      next: '',
    }
    onUpdate({ rules: [...rules, newRule], ruleCount: rules.length + 1 })
  }

  const updateRule = (index: number, updated: ChoiceRuleUI) => {
    const newRules = [...rules]
    newRules[index] = updated
    onUpdate({ rules: newRules })
  }

  const removeRule = (index: number) => {
    const newRules = rules.filter((_, i) => i !== index)
    onUpdate({ rules: newRules, ruleCount: newRules.length })
  }

  return (
    <div className="flex flex-col gap-3">
      <div>
        <Label htmlFor={`default-state-${nodeId}`} className="text-xs">
          Default State
        </Label>
        <select
          id={`default-state-${nodeId}`}
          value={defaultState}
          onChange={(e) => onUpdate({ defaultState: e.target.value })}
          className="mt-1 h-8 w-full rounded-md border border-border bg-background px-3 text-sm text-foreground focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
        >
          <option value="">No default</option>
          {allNodeIds.map((id) => (
            <option key={id} value={id}>
              {id}
            </option>
          ))}
        </select>
      </div>

      <div className="flex flex-col gap-2">
        <div className="flex items-center justify-between">
          <span className="text-xs font-medium text-muted-foreground">Rules ({rules.length})</span>
          <Button variant="outline" size="sm" className="h-6 text-[10px]" onClick={addRule}>
            <Plus className="mr-1 h-3 w-3" />
            Add Rule
          </Button>
        </div>

        {rules.map((rule, i) => (
          <ChoiceRuleEditor
            key={i}
            rule={rule}
            index={i}
            allNodeIds={allNodeIds}
            onUpdate={(updated) => updateRule(i, updated)}
            onRemove={() => removeRule(i)}
          />
        ))}

        {rules.length === 0 && (
          <div className="rounded-md border border-dashed border-border p-3 text-center text-xs text-muted-foreground">
            No rules defined. Add a rule to create conditional branches.
          </div>
        )}
      </div>
    </div>
  )
}
