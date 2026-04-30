import React, { useMemo, useState } from 'react'
import { ChevronRight, Search } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { CATEGORY_LABELS, FUNCTIONS, buildFunctionStub } from './functions'
import type { FunctionDef } from './types'
import type { FieldType } from '../../hooks/useCollectionSchema'

export interface FunctionsTabProps {
  /** When provided, only functions whose returnType matches one of these (or 'any') are shown. */
  allowedReturnTypes?: FieldType[]
  /** Called with the assembled function stub, e.g. `IF(${condition}, ${then}, ${else})`. */
  onInsert: (stub: string) => void
  testId?: string
}

export function FunctionsTab({
  allowedReturnTypes,
  onInsert,
  testId = 'field-expression-picker-functions',
}: FunctionsTabProps): React.ReactElement {
  const [filter, setFilter] = useState('')
  const [expandedName, setExpandedName] = useState<string | null>(null)

  const visible = useMemo(() => {
    let list: readonly FunctionDef[] = FUNCTIONS
    if (allowedReturnTypes && allowedReturnTypes.length > 0) {
      const allowed = new Set<string>(allowedReturnTypes)
      list = list.filter((fn) => fn.returnType === 'any' || allowed.has(fn.returnType))
    }
    const q = filter.trim().toLowerCase()
    if (q) {
      list = list.filter(
        (fn) => fn.name.toLowerCase().includes(q) || fn.description.toLowerCase().includes(q)
      )
    }
    return list
  }, [filter, allowedReturnTypes])

  const grouped = useMemo(() => {
    const groups: Record<string, FunctionDef[]> = {}
    for (const fn of visible) {
      groups[fn.category] ??= []
      groups[fn.category].push(fn)
    }
    return groups
  }, [visible])

  const orderedCategories = useMemo(
    () => ['logical', 'text', 'math', 'date', 'conversion'].filter((c) => grouped[c]),
    [grouped]
  )

  return (
    <div className="flex h-full flex-col" data-testid={testId}>
      <div className="border-b border-border bg-muted/40 px-3 py-2">
        <div className="relative">
          <Search className="pointer-events-none absolute left-2 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
            placeholder="Search functions…"
            className="h-8 pl-7 text-xs"
          />
        </div>
      </div>
      <div className="flex-1 overflow-y-auto">
        {visible.length === 0 ? (
          <div className="px-3 py-6 text-center text-xs text-muted-foreground">
            No functions match.
          </div>
        ) : (
          orderedCategories.map((cat) => (
            <div key={cat} className="border-b border-border last:border-b-0">
              <div className="bg-muted/30 px-3 py-1 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                {CATEGORY_LABELS[cat] ?? cat}
              </div>
              <ul>
                {grouped[cat].map((fn) => {
                  const expanded = expandedName === fn.name
                  return (
                    <li key={fn.name} className="border-b border-border/60 last:border-b-0">
                      <div className="flex w-full items-stretch hover:bg-muted/60">
                        <button
                          type="button"
                          onClick={() => onInsert(buildFunctionStub(fn))}
                          className="flex flex-1 items-center gap-2 px-3 py-2 text-left text-xs transition-colors"
                          data-testid={`${testId}-fn-${fn.name}`}
                        >
                          <div className="flex flex-1 flex-col gap-0.5">
                            <span className="font-mono text-xs font-semibold text-foreground">
                              {fn.name}({fn.args.join(', ')})
                            </span>
                            <span className="text-[11px] text-muted-foreground">
                              {fn.description}
                            </span>
                          </div>
                          <ChevronRight className="h-3 w-3 text-muted-foreground" />
                        </button>
                        <button
                          type="button"
                          onClick={() => setExpandedName(expanded ? null : fn.name)}
                          aria-label={
                            expanded ? `Hide details for ${fn.name}` : `Show details for ${fn.name}`
                          }
                          aria-expanded={expanded}
                          className="flex w-7 items-center justify-center border-l border-border/60 text-xs text-muted-foreground hover:bg-muted hover:text-foreground"
                        >
                          ?
                        </button>
                      </div>
                      {expanded && fn.example && (
                        <div className="border-t border-border/60 bg-muted/20 px-3 py-2 text-[11px]">
                          <div className="text-muted-foreground">Example:</div>
                          <code className="font-mono text-foreground">{fn.example}</code>
                        </div>
                      )}
                    </li>
                  )
                })}
              </ul>
            </div>
          ))
        )}
      </div>
    </div>
  )
}
