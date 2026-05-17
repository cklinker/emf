/**
 * Detail-page rail block components. Mirror the design handoff at
 * `design_handoff_kelta_detail_layout/` and are consumed by Kelta's
 * record-detail page (kelta-ui/app/src/pages/app/ObjectDetailPage).
 *
 * RecordHeader / FieldSection / AddressMap remain in kelta-ui/app for now
 * because they depend on the consumer's FieldRenderer + DropdownMenu /
 * Collapsible primitives; those land in @kelta/components in a separate
 * pass.
 */

export { StatStrip } from './StatStrip';
export type { StatStripProps, StatTileConfig, StatTileKind, StatTileTrend } from './StatStrip';

export { ScoreCard } from './ScoreCard';
export type { ScoreCardConfig, ScoreMetric, ScoreTone } from './ScoreCard';

export { TagsCard } from './TagsCard';
export type { TagsCardConfig, TagItem, TagTone } from './TagsCard';

export { MetadataCard } from './MetadataCard';
export type { MetadataCardConfig, MetadataRow } from './MetadataCard';

export { AICard } from './AICard';
export type { AICardConfig, AIActionConfig } from './AICard';

export { Timeline } from './Timeline';
export type { TimelineConfig, TimelineEvent, TimelineTone } from './Timeline';

export { Crumb } from './Crumb';
export type { CrumbItem, CrumbPosition, CrumbProps } from './Crumb';

export { RailRenderer } from './RailRenderer';
export type { RailRendererProps } from './RailRenderer';

export type { RailBlockDto, RecordHeaderConfigDto } from './types';
