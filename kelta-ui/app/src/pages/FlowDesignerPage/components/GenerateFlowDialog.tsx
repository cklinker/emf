import React, { useState } from 'react'
import { Sparkles, X } from 'lucide-react'
import { Button } from '@/components/ui/button'

interface GenerateFlowDialogProps {
  open: boolean
  isLoading: boolean
  onOpenChange: (open: boolean) => void
  onGenerate: (description: string) => void
}

export function GenerateFlowDialog({
  open,
  isLoading,
  onOpenChange,
  onGenerate,
}: GenerateFlowDialogProps) {
  const [description, setDescription] = useState('')

  if (!open) return null

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (description.trim()) {
      onGenerate(description.trim())
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="w-full max-w-lg rounded-xl border border-border bg-card shadow-xl">
        <div className="flex items-center justify-between border-b border-border px-5 py-4">
          <div className="flex items-center gap-2">
            <Sparkles className="h-4 w-4 text-primary" />
            <h2 className="text-sm font-semibold text-foreground">Generate Flow with AI</h2>
          </div>
          <button
            onClick={() => onOpenChange(false)}
            className="rounded p-1 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
            aria-label="Close"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="flex flex-col gap-4 p-5">
          <div className="flex flex-col gap-1.5">
            <label className="text-xs font-medium text-foreground" htmlFor="flow-description">
              Describe what this flow should do
            </label>
            <textarea
              id="flow-description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="e.g. When a Contact is created, send them a welcome email and log the event"
              rows={4}
              className="w-full resize-none rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground placeholder:text-muted-foreground focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
              autoFocus
              disabled={isLoading}
            />
          </div>

          <p className="text-[11px] text-muted-foreground">
            The AI will generate a starting point — you can edit any step afterwards.
          </p>

          <div className="flex justify-end gap-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => onOpenChange(false)}
              disabled={isLoading}
            >
              Cancel
            </Button>
            <Button type="submit" size="sm" disabled={!description.trim() || isLoading}>
              <Sparkles className="mr-1.5 h-3.5 w-3.5" />
              {isLoading ? 'Generating...' : 'Generate'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  )
}
