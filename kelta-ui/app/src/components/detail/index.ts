/**
 * Detail-page components. All live in `@kelta/components` now — this
 * barrel re-exports them so existing imports through `@/components/detail`
 * keep working.
 */

export {
  RecordHeader,
  FieldSection,
  AddressMap,
  StatStrip,
  ScoreCard,
  TagsCard,
  MetadataCard,
  AICard,
  Timeline,
  Crumb,
  RailRenderer,
  // DropdownMenu primitives re-exported so RecordHeader's `moreMenu`
  // slot is composed from the SAME instance RecordHeader renders
  // (mixing the app's local radix primitives splits the radix context
  // and the menu items never become interactive).
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
} from '@kelta/components'
export type {
  RecordHeaderConfig,
  RecordHeaderMetaField,
  RecordHeaderAction,
  RecordHeaderProps,
  FieldSectionProps,
  FieldSectionRenderContext,
  DetailField,
  AddressMapProps,
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
