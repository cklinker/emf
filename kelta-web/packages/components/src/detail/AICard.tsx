/**
 * AICard — cyan-tinted card with brand-gradient sparkles icon + summary.
 */

import React from 'react';
import { Sparkles } from 'lucide-react';
import { cn } from './_utils';

export interface AIActionConfig {
  label: string;
  icon?: React.ReactNode;
  onClick: () => void;
}

export interface AICardConfig {
  title: string;
  summary: string;
  actions?: AIActionConfig[];
}

export function AICard({
  config,
  className,
}: {
  config: AICardConfig;
  className?: string;
}): React.ReactElement {
  const { title, summary, actions = [] } = config;
  return (
    <div
      data-component="AICard"
      className={cn(
        'relative overflow-hidden rounded-xl border border-border bg-card',
        'before:pointer-events-none before:absolute before:inset-0 before:bg-[radial-gradient(circle_at_top_right,rgba(96,165,250,0.18),transparent_55%)]',
        'after:pointer-events-none after:absolute after:inset-x-0 after:bottom-0 after:h-12 after:bg-gradient-to-t after:from-cyan-500/10 after:to-transparent',
        className
      )}
    >
      <div className="relative space-y-3 px-5 py-4">
        <div className="flex items-center gap-2">
          <span
            className="inline-flex h-6 w-6 items-center justify-center rounded-md kelta-brand-gradient text-white shadow-[0_6px_14px_-4px_rgba(59,130,246,0.5)]"
            aria-hidden="true"
          >
            <Sparkles className="h-3.5 w-3.5" />
          </span>
          <span className="text-sm font-semibold text-foreground">{title}</span>
        </div>
        <p className="text-[13px] leading-[1.55] text-muted-foreground">{summary}</p>
        {actions.length > 0 && (
          <div className="flex flex-wrap gap-1.5 pt-1">
            {actions.map((action, idx) => (
              <button
                key={`${action.label}-${idx}`}
                type="button"
                onClick={action.onClick}
                className="inline-flex h-7 items-center rounded-md px-2 text-[12px] text-foreground hover:bg-accent"
              >
                {action.icon && <span className="mr-1 inline-flex">{action.icon}</span>}
                {action.label}
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
