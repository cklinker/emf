import React from 'react'
import { FieldLabel } from '@/components/kelta'
import { Input } from '@/components/ui/input'

interface JsonSchemaProperty {
  type?: string
  title?: string
  description?: string
  enum?: string[]
}

interface JsonSchema {
  type?: string
  properties?: Record<string, JsonSchemaProperty>
  required?: string[]
}

interface ModuleActionParamsProps {
  configSchema: string | null
  parameters: Record<string, unknown> | undefined
  onUpdate: (params: Record<string, unknown>) => void
}

export function ModuleActionParams({
  configSchema,
  parameters,
  onUpdate,
}: ModuleActionParamsProps) {
  const schema = React.useMemo<JsonSchema | null>(() => {
    if (!configSchema) return null
    try {
      return JSON.parse(configSchema)
    } catch {
      return null
    }
  }, [configSchema])

  if (!schema || !schema.properties || Object.keys(schema.properties).length === 0) {
    return (
      <p className="text-xs text-muted-foreground">This action has no configurable parameters.</p>
    )
  }

  const params = parameters ?? {}

  function handleChange(key: string, value: unknown) {
    onUpdate({ ...params, [key]: value })
  }

  return (
    <div className="flex flex-col gap-2">
      {Object.entries(schema.properties).map(([key, prop]) => {
        const label = prop.title ?? key
        const value = params[key]
        const fieldId = `module-param-${key}`

        if (prop.enum) {
          return (
            <div key={key}>
              <FieldLabel htmlFor={fieldId} className="text-xs">
                {label}
              </FieldLabel>
              <select
                id={fieldId}
                value={(value as string) ?? ''}
                onChange={(e) => handleChange(key, e.target.value)}
                className="mt-1 h-8 w-full rounded-md border border-border bg-background px-3 text-sm text-foreground focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
              >
                <option value="">Select...</option>
                {prop.enum.map((opt) => (
                  <option key={opt} value={opt}>
                    {opt}
                  </option>
                ))}
              </select>
              {prop.description && (
                <p className="mt-0.5 text-[10px] text-muted-foreground">{prop.description}</p>
              )}
            </div>
          )
        }

        if (prop.type === 'boolean') {
          return (
            <div key={key} className="flex items-center gap-2">
              <input
                id={fieldId}
                type="checkbox"
                checked={(value as boolean) ?? false}
                onChange={(e) => handleChange(key, e.target.checked)}
                className="h-4 w-4 rounded border-border"
              />
              <FieldLabel htmlFor={fieldId} className="text-xs">
                {label}
              </FieldLabel>
            </div>
          )
        }

        return (
          <div key={key}>
            <FieldLabel htmlFor={fieldId} className="text-xs">
              {label}
            </FieldLabel>
            <Input
              id={fieldId}
              type={prop.type === 'number' || prop.type === 'integer' ? 'number' : 'text'}
              value={(value as string | number) ?? ''}
              onChange={(e) =>
                handleChange(
                  key,
                  prop.type === 'number' || prop.type === 'integer'
                    ? e.target.value === ''
                      ? undefined
                      : Number(e.target.value)
                    : e.target.value
                )
              }
              placeholder={prop.description ?? ''}
              className="mt-1 h-8 text-sm"
            />
            {prop.description && (
              <p className="mt-0.5 text-[10px] text-muted-foreground">{prop.description}</p>
            )}
          </div>
        )
      })}
    </div>
  )
}
