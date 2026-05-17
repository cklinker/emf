/**
 * Detail-page components.
 *
 * - RecordHeader / StatStrip / ScoreCard / TagsCard / MetadataCard / AICard /
 *   Timeline / Crumb / RailRenderer live in `@kelta/components` so admin
 *   and runtime shells share them. Re-exported here so existing imports
 *   through `@/components/detail` continue to work.
 * - FieldSection / AddressMap remain local because they depend on this
 *   app's FieldRenderer; they migrate next with a `renderField` slot prop.
 */

export { FieldSection } from './FieldSection'
export type { FieldSectionProps } from './FieldSection'

export { AddressMap } from './AddressMap'
export type { AddressMapProps } from './AddressMap'

export {
  RecordHeader,
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
  RecordHeaderConfig,
  RecordHeaderMetaField,
  RecordHeaderAction,
  RecordHeaderProps,
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
