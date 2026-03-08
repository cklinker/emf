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

interface FilterConfig {
  field: string
  operator: string
  value: string
}

interface AggregationConfig {
  function: string
  field?: string
  alias: string
}

interface QueryRecordsParamsProps {
  parameters?: Record<string, unknown>
  onUpdate: (params: Record<string, unknown>) => void
}

const OPERATORS = [
  { value: 'eq', label: 'Equals' },
  { value: 'neq', label: 'Not Equals' },
  { value: 'gt', label: 'Greater Than' },
  { value: 'lt', label: 'Less Than' },
  { value: 'gte', label: 'Greater or Equal' },
  { value: 'lte', label: 'Less or Equal' },
  { value: 'contains', label: 'Contains' },
  { value: 'icontains', label: 'Contains (case-insensitive)' },
]

const AGG_FUNCTIONS = ['COUNT', 'SUM', 'MIN', 'MAX', 'AVG']

const DEFAULT_FILTER: FilterConfig = { field: '', operator: 'eq', value: '' }
const DEFAULT_AGG: AggregationConfig = { function: 'COUNT', alias: '' }

export function QueryRecordsParams({ parameters, onUpdate }: QueryRecordsParamsProps) {
  const params = parameters || {}
  const targetCollectionName = (params.targetCollectionName as string) || ''
  const filters = (params.filters as FilterConfig[]) || []
  const sort = (params.sort as string) || ''
  const pageSize = (params.pageSize as number) ?? 200
  const aggregations = (params.aggregations as AggregationConfig[]) || []

  const update = (field: string, value: unknown) => {
    onUpdate({ ...params, [field]: value })
  }

  const updateFilter = (index: number, field: keyof FilterConfig, value: string) => {
    const updated = [...filters]
    updated[index] = { ...updated[index], [field]: value }
    update('filters', updated)
  }

  const addFilter = () => update('filters', [...filters, { ...DEFAULT_FILTER }])
  const removeFilter = (index: number) =>
    update(
      'filters',
      filters.filter((_, i) => i !== index)
    )

  const updateAgg = (index: number, field: string, value: string) => {
    const updated = [...aggregations]
    updated[index] = { ...updated[index], [field]: value }
    update('aggregations', updated)
  }

  const addAgg = () => update('aggregations', [...aggregations, { ...DEFAULT_AGG }])
  const removeAgg = (index: number) =>
    update(
      'aggregations',
      aggregations.filter((_, i) => i !== index)
    )

  return (
    <div className="flex flex-col gap-3 rounded-md border border-border bg-muted/30 p-2">
      <span className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
        Query Records Config
      </span>

      <div>
        <Label className="text-[10px]">Collection Name</Label>
        <Input
          value={targetCollectionName}
          onChange={(e) => update('targetCollectionName', e.target.value)}
          className="mt-0.5 h-7 text-xs"
          placeholder="e.g. orders"
        />
      </div>

      {/* Filters */}
      <Accordion type="single" collapsible defaultValue="filters">
        <AccordionItem value="filters" className="border-none">
          <AccordionTrigger className="py-1.5 text-[10px] font-medium text-muted-foreground hover:no-underline">
            Filters ({filters.length})
          </AccordionTrigger>
          <AccordionContent className="flex flex-col gap-2 pb-0">
            {filters.map((filter, i) => (
              <div
                key={i}
                className="flex flex-col gap-1.5 rounded border border-border bg-background p-1.5"
              >
                <div className="flex items-center justify-between">
                  <span className="text-[10px] text-muted-foreground">Filter #{i + 1}</span>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="h-5 w-5"
                    onClick={() => removeFilter(i)}
                  >
                    <Trash2 className="h-3 w-3" />
                  </Button>
                </div>
                <div className="grid grid-cols-3 gap-1">
                  <div>
                    <Label className="text-[9px]">Field</Label>
                    <Input
                      value={filter.field}
                      onChange={(e) => updateFilter(i, 'field', e.target.value)}
                      className="mt-0.5 h-6 text-[11px]"
                      placeholder="field_name"
                    />
                  </div>
                  <div>
                    <Label className="text-[9px]">Operator</Label>
                    <select
                      value={filter.operator}
                      onChange={(e) => updateFilter(i, 'operator', e.target.value)}
                      className="mt-0.5 h-6 w-full rounded border border-border bg-background px-1 text-[11px]"
                    >
                      {OPERATORS.map((op) => (
                        <option key={op.value} value={op.value}>
                          {op.label}
                        </option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <Label className="text-[9px]">Value</Label>
                    <Input
                      value={filter.value}
                      onChange={(e) => updateFilter(i, 'value', e.target.value)}
                      className="mt-0.5 h-6 text-[11px]"
                      placeholder="${$.path} or literal"
                    />
                  </div>
                </div>
              </div>
            ))}
            <Button variant="outline" size="sm" className="h-6 text-[10px]" onClick={addFilter}>
              <Plus className="mr-1 h-3 w-3" />
              Add Filter
            </Button>
          </AccordionContent>
        </AccordionItem>
      </Accordion>

      {/* Sort & Page Size */}
      <div className="grid grid-cols-2 gap-2">
        <div>
          <Label className="text-[10px]">Sort</Label>
          <Input
            value={sort}
            onChange={(e) => update('sort', e.target.value)}
            className="mt-0.5 h-7 text-xs"
            placeholder="-created_at"
          />
        </div>
        <div>
          <Label className="text-[10px]">Page Size</Label>
          <Input
            type="number"
            value={pageSize}
            onChange={(e) => update('pageSize', parseInt(e.target.value) || 200)}
            className="mt-0.5 h-7 text-xs"
            min={1}
            max={1000}
          />
        </div>
      </div>

      {/* Aggregations */}
      <Accordion type="single" collapsible defaultValue="aggregations">
        <AccordionItem value="aggregations" className="border-none">
          <AccordionTrigger className="py-1.5 text-[10px] font-medium text-muted-foreground hover:no-underline">
            Aggregations ({aggregations.length})
          </AccordionTrigger>
          <AccordionContent className="flex flex-col gap-2 pb-0">
            {aggregations.map((agg, i) => (
              <div
                key={i}
                className="flex flex-col gap-1.5 rounded border border-border bg-background p-1.5"
              >
                <div className="flex items-center justify-between">
                  <span className="text-[10px] text-muted-foreground">Aggregation #{i + 1}</span>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="h-5 w-5"
                    onClick={() => removeAgg(i)}
                  >
                    <Trash2 className="h-3 w-3" />
                  </Button>
                </div>
                <div className="grid grid-cols-3 gap-1">
                  <div>
                    <Label className="text-[9px]">Function</Label>
                    <select
                      value={agg.function}
                      onChange={(e) => updateAgg(i, 'function', e.target.value)}
                      className="mt-0.5 h-6 w-full rounded border border-border bg-background px-1 text-[11px]"
                    >
                      {AGG_FUNCTIONS.map((fn) => (
                        <option key={fn} value={fn}>
                          {fn}
                        </option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <Label className="text-[9px]">Field</Label>
                    <Input
                      value={agg.field || ''}
                      onChange={(e) => updateAgg(i, 'field', e.target.value)}
                      className="mt-0.5 h-6 text-[11px]"
                      placeholder={agg.function === 'COUNT' ? '(optional)' : 'field_name'}
                    />
                  </div>
                  <div>
                    <Label className="text-[9px]">Alias</Label>
                    <Input
                      value={agg.alias}
                      onChange={(e) => updateAgg(i, 'alias', e.target.value)}
                      className="mt-0.5 h-6 text-[11px]"
                      placeholder="output_key"
                    />
                  </div>
                </div>
              </div>
            ))}
            <Button variant="outline" size="sm" className="h-6 text-[10px]" onClick={addAgg}>
              <Plus className="mr-1 h-3 w-3" />
              Add Aggregation
            </Button>
          </AccordionContent>
        </AccordionItem>
      </Accordion>
    </div>
  )
}
