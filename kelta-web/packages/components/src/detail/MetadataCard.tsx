/**
 * MetadataCard — label/value list separated by 1px dashed dividers.
 */

import React from 'react';
import { cn } from './_utils';

export interface MetadataRow {
  label: string;
  value: React.ReactNode;
  mono?: boolean;
}

export interface MetadataCardConfig {
  title: string;
  rows: MetadataRow[];
}

export function MetadataCard({
  config,
  className,
}: {
  config: MetadataCardConfig;
  className?: string;
}): React.ReactElement | null {
  const { title, rows } = config;
  if (rows.length === 0) return null;

  return (
    <div
      data-component="MetadataCard"
      className={cn('overflow-hidden rounded-xl border border-border bg-card', className)}
    >
      <div className="px-5 py-4">
        <span className="text-sm font-semibold text-foreground">{title}</span>
      </div>
      <div className="border-t border-border px-5 py-2">
        <dl className="divide-y divide-dashed divide-border">
          {rows.map((row, idx) => (
            <div
              key={`${row.label}-${idx}`}
              className="flex items-baseline justify-between gap-3 py-2.5"
            >
              <dt className="text-[12px] text-muted-foreground">{row.label}</dt>
              <dd
                className={cn(
                  'truncate text-[13px] text-foreground',
                  row.mono && 'font-mono text-[12px]'
                )}
              >
                {row.value}
              </dd>
            </div>
          ))}
        </dl>
      </div>
    </div>
  );
}
