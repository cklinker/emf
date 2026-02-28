import React from 'react'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from '@/components/ui/accordion'
import { Plus, Trash2 } from 'lucide-react'

interface HeaderEntry {
  key: string
  value: string
}

const HTTP_METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE']

interface HttpCalloutParamsProps {
  parameters?: Record<string, unknown>
  onUpdate: (params: Record<string, unknown>) => void
}

function headersToEntries(headers: Record<string, string> | undefined): HeaderEntry[] {
  if (!headers || typeof headers !== 'object') return []
  return Object.entries(headers).map(([key, value]) => ({ key, value: String(value) }))
}

function entriesToHeaders(entries: HeaderEntry[]): Record<string, string> {
  const result: Record<string, string> = {}
  for (const entry of entries) {
    if (entry.key) {
      result[entry.key] = entry.value
    }
  }
  return result
}

export function HttpCalloutParams({ parameters, onUpdate }: HttpCalloutParamsProps) {
  const params = parameters || {}
  const url = (params.url as string) || ''
  const method = (params.method as string) || 'GET'
  const headers = headersToEntries(params.headers as Record<string, string> | undefined)
  const body = (params.body as string) || ''

  const update = (field: string, value: unknown) => {
    onUpdate({ ...params, [field]: value })
  }

  const updateHeader = (index: number, field: 'key' | 'value', value: string) => {
    const updated = [...headers]
    updated[index] = { ...updated[index], [field]: value }
    update('headers', entriesToHeaders(updated))
  }

  const addHeader = () => {
    const updated = [...headers, { key: '', value: '' }]
    update('headers', entriesToHeaders(updated))
  }

  const removeHeader = (index: number) => {
    const updated = headers.filter((_, i) => i !== index)
    update('headers', entriesToHeaders(updated))
  }

  return (
    <div className="flex flex-col gap-3 rounded-md border border-border bg-muted/30 p-2">
      <span className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
        HTTP Callout Config
      </span>

      <div>
        <Label className="text-[10px]">URL</Label>
        <Input
          value={url}
          onChange={(e) => update('url', e.target.value)}
          className="mt-0.5 h-7 text-xs"
          placeholder="https://api.example.com/endpoint"
        />
      </div>

      <div>
        <Label className="text-[10px]">Method</Label>
        <select
          value={method}
          onChange={(e) => update('method', e.target.value)}
          className="mt-0.5 h-7 w-full rounded border border-border bg-background px-2 text-xs"
        >
          {HTTP_METHODS.map((m) => (
            <option key={m} value={m}>
              {m}
            </option>
          ))}
        </select>
      </div>

      {/* Headers */}
      <Accordion type="single" collapsible defaultValue="headers">
        <AccordionItem value="headers" className="border-none">
          <AccordionTrigger className="py-1.5 text-[10px] font-medium text-muted-foreground hover:no-underline">
            Headers ({headers.length})
          </AccordionTrigger>
          <AccordionContent className="flex flex-col gap-2 pb-0">
            {headers.map((header, i) => (
              <div
                key={i}
                className="flex flex-col gap-1.5 rounded border border-border bg-background p-1.5"
              >
                <div className="flex items-center justify-between">
                  <span className="text-[10px] text-muted-foreground">Header #{i + 1}</span>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="h-5 w-5"
                    onClick={() => removeHeader(i)}
                  >
                    <Trash2 className="h-3 w-3" />
                  </Button>
                </div>
                <div className="grid grid-cols-2 gap-1">
                  <div>
                    <Label className="text-[9px]">Key</Label>
                    <Input
                      value={header.key}
                      onChange={(e) => updateHeader(i, 'key', e.target.value)}
                      className="mt-0.5 h-6 text-[11px]"
                      placeholder="Content-Type"
                    />
                  </div>
                  <div>
                    <Label className="text-[9px]">Value</Label>
                    <Input
                      value={header.value}
                      onChange={(e) => updateHeader(i, 'value', e.target.value)}
                      className="mt-0.5 h-6 text-[11px]"
                      placeholder="application/json"
                    />
                  </div>
                </div>
              </div>
            ))}
            <Button variant="outline" size="sm" className="h-6 text-[10px]" onClick={addHeader}>
              <Plus className="mr-1 h-3 w-3" />
              Add Header
            </Button>
          </AccordionContent>
        </AccordionItem>
      </Accordion>

      <div>
        <Label className="text-[10px]">Body</Label>
        <Textarea
          value={body}
          onChange={(e) => update('body', e.target.value)}
          className="mt-0.5 min-h-[56px] font-mono text-xs"
          placeholder={'{"orderId": "${$.record.data.id}"}'}
          rows={4}
        />
      </div>
    </div>
  )
}
