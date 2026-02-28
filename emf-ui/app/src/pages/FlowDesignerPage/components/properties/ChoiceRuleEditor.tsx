import React from 'react'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Button } from '@/components/ui/button'
import { Trash2 } from 'lucide-react'
import type { ChoiceRuleUI, ChoiceOperator } from '../../types'
import { CHOICE_OPERATORS } from '../../types'

interface ChoiceRuleEditorProps {
  rule: ChoiceRuleUI
  index: number
  allNodeIds: string[]
  onUpdate: (rule: ChoiceRuleUI) => void
  onRemove: () => void
}

const OPERATOR_LABELS: Record<ChoiceOperator, string> = {
  StringEquals: 'String =',
  StringNotEquals: 'String !=',
  StringGreaterThan: 'String >',
  StringLessThan: 'String <',
  StringGreaterThanEquals: 'String >=',
  StringLessThanEquals: 'String <=',
  StringMatches: 'String matches',
  NumericEquals: 'Number =',
  NumericNotEquals: 'Number !=',
  NumericGreaterThan: 'Number >',
  NumericLessThan: 'Number <',
  NumericGreaterThanEquals: 'Number >=',
  NumericLessThanEquals: 'Number <=',
  BooleanEquals: 'Boolean =',
  IsPresent: 'Is present',
  IsNull: 'Is null',
}

export function ChoiceRuleEditor({
  rule,
  index,
  allNodeIds,
  onUpdate,
  onRemove,
}: ChoiceRuleEditorProps) {
  return (
    <div className="flex flex-col gap-2 rounded-md border border-border p-2">
      <div className="flex items-center justify-between">
        <span className="text-[10px] font-medium text-muted-foreground">Rule #{index + 1}</span>
        <Button variant="ghost" size="icon" className="h-5 w-5" onClick={onRemove}>
          <Trash2 className="h-3 w-3" />
        </Button>
      </div>

      <div>
        <Label className="text-[10px]">Variable</Label>
        <Input
          value={rule.variable}
          onChange={(e) => onUpdate({ ...rule, variable: e.target.value })}
          className="mt-0.5 h-7 font-mono text-xs"
          placeholder="$.status"
        />
      </div>

      <div>
        <Label className="text-[10px]">Operator</Label>
        <select
          value={rule.operator}
          onChange={(e) => onUpdate({ ...rule, operator: e.target.value as ChoiceOperator })}
          className="mt-0.5 h-7 w-full rounded-md border border-border bg-background px-2 text-xs text-foreground focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
        >
          {CHOICE_OPERATORS.map((op) => (
            <option key={op} value={op}>
              {OPERATOR_LABELS[op]}
            </option>
          ))}
        </select>
      </div>

      <div>
        <Label className="text-[10px]">Value</Label>
        <Input
          value={rule.value}
          onChange={(e) => onUpdate({ ...rule, value: e.target.value })}
          className="mt-0.5 h-7 text-xs"
          placeholder="expected value"
        />
      </div>

      <div>
        <Label className="text-[10px]">Next State</Label>
        <select
          value={rule.next}
          onChange={(e) => onUpdate({ ...rule, next: e.target.value })}
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
  )
}
