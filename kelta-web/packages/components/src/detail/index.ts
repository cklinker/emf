/**
 * Detail-page components. Mirror the design handoff at
 * `design_handoff_kelta_detail_layout/` and are consumed by Kelta's
 * record-detail page (kelta-ui/app/src/pages/app/ObjectDetailPage).
 *
 * RecordHeader, FieldSection, and AddressMap also live here. They depend on
 * the package-local UI primitives in `../ui/` (Button, Badge, DropdownMenu,
 * Card, Collapsible). FieldSection + AddressMap take a `renderField` slot
 * prop so the package never reaches back into the consumer's FieldRenderer.
 */

export { RecordHeader } from './RecordHeader';
export type {
  RecordHeaderConfig,
  RecordHeaderMetaField,
  RecordHeaderAction,
  RecordHeaderProps,
} from './RecordHeader';

export { FieldSection } from './FieldSection';
export type { FieldSectionProps, FieldSectionRenderContext, DetailField } from './FieldSection';

export { AddressMap } from './AddressMap';
export type { AddressMapProps } from './AddressMap';

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
