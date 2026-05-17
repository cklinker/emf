/**
 * TagsCard — header with optional `+` action + flex row of tone-tinted pills.
 */

import React from 'react';
import { Plus } from 'lucide-react';
import { cn } from './_utils';

export type TagTone = 'default' | 'brand' | 'success' | 'warning' | 'danger';

export interface TagItem {
  label: string;
  tone?: TagTone;
}

export interface TagsCardConfig {
  title: string;
  tags: TagItem[];
  onAdd?: () => void;
}

const TONE_CLASS: Record<TagTone, string> = {
  default: 'bg-muted text-foreground',
  brand: 'bg-blue-400/15 text-blue-300',
  success: 'bg-emerald-500/10 text-emerald-400',
  warning: 'bg-amber-500/10 text-amber-400',
  danger: 'bg-red-500/10 text-red-400',
};

export function TagsCard({
  config,
  className,
}: {
  config: TagsCardConfig;
  className?: string;
}): React.ReactElement | null {
  const { title, tags, onAdd } = config;
  if (tags.length === 0 && !onAdd) return null;

  return (
    <div
      data-component="TagsCard"
      className={cn('overflow-hidden rounded-xl border border-border bg-card', className)}
    >
      <div className="flex flex-row items-center justify-between gap-2 px-5 py-4">
        <span className="text-sm font-semibold text-foreground">{title}</span>
        {onAdd && (
          <button
            type="button"
            onClick={onAdd}
            aria-label={`Add ${title.toLowerCase()}`}
            className="inline-flex h-6 w-6 items-center justify-center rounded-md text-muted-foreground hover:bg-accent hover:text-foreground"
          >
            <Plus className="h-3.5 w-3.5" aria-hidden="true" />
          </button>
        )}
      </div>
      <div className="border-t border-border px-5 py-4">
        {tags.length === 0 ? (
          <span className="text-[13px] text-muted-foreground">—</span>
        ) : (
          <div className="flex flex-wrap gap-1.5">
            {tags.map((tag, idx) => (
              <span
                key={`${tag.label}-${idx}`}
                className={cn(
                  'inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-medium',
                  TONE_CLASS[tag.tone || 'default']
                )}
              >
                {tag.label}
              </span>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
