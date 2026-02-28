import React from 'react'
import {
  Cog,
  GitBranch,
  Columns,
  Repeat,
  Clock,
  ArrowRight,
  CheckCircle,
  XCircle,
} from 'lucide-react'

interface PaletteItem {
  type: string
  label: string
  icon: React.ElementType
  category: string
  color: string
}

const PALETTE_ITEMS: PaletteItem[] = [
  { type: 'Task', label: 'Task', icon: Cog, category: 'Actions', color: 'text-blue-600' },
  { type: 'Choice', label: 'Choice', icon: GitBranch, category: 'Logic', color: 'text-amber-600' },
  {
    type: 'Parallel',
    label: 'Parallel',
    icon: Columns,
    category: 'Logic',
    color: 'text-purple-600',
  },
  { type: 'Map', label: 'Map', icon: Repeat, category: 'Logic', color: 'text-teal-600' },
  { type: 'Wait', label: 'Wait', icon: Clock, category: 'Control', color: 'text-gray-600' },
  { type: 'Pass', label: 'Pass', icon: ArrowRight, category: 'Control', color: 'text-gray-600' },
  {
    type: 'Succeed',
    label: 'Succeed',
    icon: CheckCircle,
    category: 'Terminal',
    color: 'text-green-600',
  },
  { type: 'Fail', label: 'Fail', icon: XCircle, category: 'Terminal', color: 'text-red-600' },
]

const categories = ['Actions', 'Logic', 'Control', 'Terminal']

interface StepsPaletteProps {
  collapsed?: boolean
  onToggle?: () => void
}

export function StepsPalette({ collapsed, onToggle }: StepsPaletteProps) {
  if (collapsed) {
    return (
      <div className="flex w-10 flex-col items-center border-r border-border bg-card pt-2">
        <button
          onClick={onToggle}
          className="rounded p-1 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
          aria-label="Expand palette"
          title="Expand steps palette"
        >
          <ArrowRight className="h-4 w-4" />
        </button>
      </div>
    )
  }

  return (
    <div className="flex w-[180px] shrink-0 flex-col border-r border-border bg-card">
      <div className="flex items-center justify-between border-b border-border px-3 py-2">
        <h3 className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
          Steps
        </h3>
        {onToggle && (
          <button
            onClick={onToggle}
            className="rounded p-1 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
            aria-label="Collapse palette"
          >
            <ArrowRight className="h-3 w-3 rotate-180" />
          </button>
        )}
      </div>
      <div className="flex-1 overflow-y-auto p-2">
        {categories.map((category) => {
          const items = PALETTE_ITEMS.filter((item) => item.category === category)
          return (
            <div key={category} className="mb-3">
              <div className="mb-1 px-1 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                {category}
              </div>
              <div className="flex flex-col gap-1">
                {items.map((item) => (
                  <div
                    key={item.type}
                    draggable
                    onDragStart={(e) => {
                      e.dataTransfer.setData('application/reactflow-type', item.type)
                      e.dataTransfer.effectAllowed = 'move'
                    }}
                    className="flex cursor-grab items-center gap-2 rounded-md border border-border bg-background px-2 py-1.5 text-sm transition-colors hover:bg-muted active:cursor-grabbing"
                  >
                    <item.icon className={`h-3.5 w-3.5 shrink-0 ${item.color}`} />
                    <span className="text-xs font-medium text-foreground">{item.label}</span>
                  </div>
                ))}
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
