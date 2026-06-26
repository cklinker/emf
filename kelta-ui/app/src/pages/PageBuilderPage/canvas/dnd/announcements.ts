/**
 * Kelta-worded screen-reader announcements + instructions for the canvas `DndContext` (slice 2c, a11y).
 * Strings flow through `t()` (`builder.pages.dnd.*`) so they are localized like the rest of the builder.
 */
import type { Announcements, ScreenReaderInstructions } from '@dnd-kit/core'

type TFn = (key: string, params?: Record<string, string | number>) => string

/** Build the `accessibility.announcements` object for the canvas `DndContext` from the i18n `t`. */
export function buildAnnouncements(t: TFn): Announcements {
  const labelOf = (id: string | number | null | undefined): string => String(id ?? '')
  return {
    onDragStart({ active }) {
      return t('builder.pages.dnd.picked', { type: labelOf(active.id) })
    },
    onDragOver({ active, over }) {
      if (over) {
        return t('builder.pages.dnd.over', {
          type: labelOf(active.id),
          container: labelOf(over.id),
        })
      }
      return t('builder.pages.dnd.picked', { type: labelOf(active.id) })
    },
    onDragEnd({ active, over }) {
      if (over) {
        return t('builder.pages.dnd.dropped', {
          type: labelOf(active.id),
          container: labelOf(over.id),
        })
      }
      return t('builder.pages.dnd.cancelled', { type: labelOf(active.id) })
    },
    onDragCancel({ active }) {
      return t('builder.pages.dnd.cancelled', { type: labelOf(active.id) })
    },
  }
}

/** Build the `accessibility.screenReaderInstructions` for the canvas `DndContext`. */
export function buildScreenReaderInstructions(t: TFn): ScreenReaderInstructions {
  return {
    draggable: t('builder.pages.dnd.instructions'),
  }
}
