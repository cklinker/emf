/**
 * Detail-page components. Mirror the design handoff at
 * `design_handoff_kelta_detail_layout/` and are consumed by Kelta's
 * record-detail page (kelta-ui/app/src/pages/app/ObjectDetailPage).
 *
 * RecordHeader now lives here too; it depends on the package-local UI
 * primitives in `../ui/` (Button, Badge, DropdownMenu). FieldSection +
 * AddressMap still live in kelta-ui/app because they depend on the
 * consumer's FieldRenderer; they migrate in a separate pass that adds a
 * `renderField` slot prop.
 */

export { RecordHeader } from './RecordHeader';
export type {
  RecordHeaderConfig,
  RecordHeaderMetaField,
  RecordHeaderAction,
  RecordHeaderProps,
} from './RecordHeader';

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
