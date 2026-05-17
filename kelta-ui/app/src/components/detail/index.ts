/**
 * Detail-page components.
 *
 * - StatStrip / ScoreCard / TagsCard / MetadataCard / AICard / Timeline /
 *   Crumb / RailRenderer now live in `@kelta/components` so admin + runtime
 *   shells share them. They're re-exported here so existing imports through
 *   `@/components/detail` continue to work.
 * - RecordHeader / FieldSection / AddressMap remain local because they
 *   depend on this app's FieldRenderer and Radix-backed DropdownMenu /
 *   Collapsible primitives; they migrate in a follow-up once the consumer
 *   contract is stabilized.
 */

export { RecordHeader } from './RecordHeader'
export type {
  RecordHeaderConfig,
  RecordHeaderMetaField,
  RecordHeaderAction,
  RecordHeaderProps,
} from './RecordHeader'

export { FieldSection } from './FieldSection'
export type { FieldSectionProps } from './FieldSection'

export { AddressMap } from './AddressMap'
export type { AddressMapProps } from './AddressMap'

export {
  StatStrip,
  ScoreCard,
  TagsCard,
  MetadataCard,
  AICard,
  Timeline,
  Crumb,
  RailRenderer,
} from '@kelta/components'
export type {
  StatStripProps,
  StatTileConfig,
  StatTileKind,
  StatTileTrend,
  ScoreCardConfig,
  ScoreMetric,
  ScoreTone,
  TagsCardConfig,
  TagItem,
  TagTone,
  MetadataCardConfig,
  MetadataRow,
  AICardConfig,
  AIActionConfig,
  TimelineConfig,
  TimelineEvent,
  TimelineTone,
  CrumbItem,
  CrumbPosition,
  CrumbProps,
  RailRendererProps,
} from '@kelta/components'
