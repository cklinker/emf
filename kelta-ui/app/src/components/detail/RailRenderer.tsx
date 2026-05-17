/**
 * RailRenderer
 *
 * Renders an ordered list of side-rail blocks from a layout config. Each
 * `RailBlockDto` carries a discriminating `kind` and a `config` payload; we
 * dispatch on `kind` and silently skip unknown variants so adding a new
 * server-side kind doesn't crash older clients.
 */

import React from 'react'
import type { RailBlockDto } from '@/hooks/usePageLayout'
import { MetadataCard } from './MetadataCard'
import { StatStrip } from './StatStrip'
import { ScoreCard } from './ScoreCard'
import { TagsCard } from './TagsCard'
import { AICard } from './AICard'
import { Timeline } from './Timeline'
import type { ScoreCardConfig } from './ScoreCard'
import type { StatTileConfig } from './StatStrip'
import type { TagTone } from './TagsCard'
import type { TimelineEvent, TimelineTone } from './Timeline'

export interface RailRendererProps {
  blocks: RailBlockDto[]
}

export function RailRenderer({ blocks }: RailRendererProps): React.ReactElement {
  return (
    <>
      {blocks.map((block, idx) => {
        const key = `${block.kind}-${idx}`
        switch (block.kind) {
          case 'metadataCard':
            return <MetadataCard key={key} config={block.config} />
          case 'statStrip':
            return (
              <StatStrip
                key={key}
                tiles={block.config.tiles as unknown as StatTileConfig[]}
              />
            )
          case 'scoreCard':
            return (
              <ScoreCard
                key={key}
                config={block.config as unknown as ScoreCardConfig}
              />
            )
          case 'tagsCard':
            return (
              <TagsCard
                key={key}
                config={{
                  title: block.config.title,
                  tags: block.config.tags.map((t) => ({
                    label: t.label,
                    tone: (t.tone as TagTone | undefined) ?? 'default',
                  })),
                }}
              />
            )
          case 'aiCard':
            return (
              <AICard
                key={key}
                config={{
                  title: block.config.title,
                  summary: block.config.summary,
                  // Server-defined actions are visual-only at this point;
                  // wire to real handlers when the AI action endpoints land.
                  actions: (block.config.actions ?? []).map((a) => ({
                    label: a.label,
                    onClick: () => undefined,
                  })),
                }}
              />
            )
          case 'timeline':
            return (
              <Timeline
                key={key}
                config={{
                  title: block.config.title,
                  events: (block.config.events as unknown[]).map((e) => {
                    const ev = e as Record<string, unknown>
                    return {
                      at: String(ev.at ?? ''),
                      title: String(ev.title ?? ''),
                      body: ev.body ? String(ev.body) : undefined,
                      tone: (ev.tone as TimelineTone | undefined) ?? 'default',
                    } satisfies TimelineEvent
                  }),
                }}
              />
            )
          default:
            return null
        }
      })}
    </>
  )
}
