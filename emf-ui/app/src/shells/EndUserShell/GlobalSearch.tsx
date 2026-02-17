/**
 * GlobalSearch Component
 *
 * A command palette (Cmd+K) for searching across all collections.
 * Uses shadcn's Command dialog with grouped search results.
 */

import React, { useCallback } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Search, FileText, ArrowRight } from 'lucide-react'
import {
  CommandDialog,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from '@/components/ui/command'

interface GlobalSearchProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function GlobalSearch({ open, onOpenChange }: GlobalSearchProps): React.ReactElement {
  const { tenantSlug } = useParams<{ tenantSlug: string }>()
  const navigate = useNavigate()

  const handleSelect = useCallback(
    (value: string) => {
      onOpenChange(false)
      // Navigate to the selected item
      if (value.startsWith('/')) {
        navigate(`/${tenantSlug}${value}`)
      }
    },
    [navigate, tenantSlug, onOpenChange]
  )

  return (
    <CommandDialog open={open} onOpenChange={onOpenChange}>
      <CommandInput placeholder="Search records, collections, pages..." />
      <CommandList>
        <CommandEmpty>
          <div className="flex flex-col items-center gap-2 py-6 text-center">
            <Search className="h-8 w-8 text-muted-foreground" />
            <p className="text-sm text-muted-foreground">
              No results found. Try a different query.
            </p>
          </div>
        </CommandEmpty>
        <CommandGroup heading="Quick Actions">
          <CommandItem onSelect={() => handleSelect('/app/home')}>
            <FileText className="mr-2 h-4 w-4" />
            <span>Go to Home</span>
            <ArrowRight className="ml-auto h-4 w-4 text-muted-foreground" />
          </CommandItem>
          <CommandItem onSelect={() => handleSelect('/setup')}>
            <FileText className="mr-2 h-4 w-4" />
            <span>Switch to Setup</span>
            <ArrowRight className="ml-auto h-4 w-4 text-muted-foreground" />
          </CommandItem>
        </CommandGroup>
      </CommandList>
    </CommandDialog>
  )
}
