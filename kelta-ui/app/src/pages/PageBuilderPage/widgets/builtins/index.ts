/**
 * Registers all built-in widgets with the global registry. Importing this module (side-effect)
 * populates the registry; the builder and runtime both import it once at startup.
 */
import { widgetRegistry } from '../registry'
import { contentWidgets } from './content'
import { layoutWidgets } from './layout'
import { dataWidgets } from './data'
import { dataBindingWidgets } from './dataBinding'
import { formWidgets } from './forms'
import { inputWidgets } from './inputs'
import { imageWidget } from './image'
import { chartWidget } from './chart'
import { tabsWidget, tabPanelWidget } from './tabs'
import { navWidget } from './nav'
import { iconWidget } from './icon'
import { linkWidget } from './link'
import { metricWidget } from './metric'
import { chatPanelWidget } from './chatPanel'
import { registerFormFieldRenderers } from './registerFormFieldRenderers'

let registered = false

/** Idempotently register the built-in widget set. Safe to call from multiple entry points. */
export function registerBuiltinWidgets(): void {
  if (registered) return
  registered = true
  for (const w of [
    ...contentWidgets,
    ...layoutWidgets,
    ...dataWidgets,
    ...dataBindingWidgets,
    ...formWidgets,
    ...inputWidgets,
    // slice 2g — widget breadth. `imageWidget` overwrites the bare 2a image (register() is
    // overwrite-by-`type`); `tabPanelWidget` is palette-hidden but registered so `get` resolves it.
    imageWidget,
    chartWidget,
    tabsWidget,
    tabPanelWidget,
    navWidget,
    iconWidget,
    linkWidget,
    metricWidget,
    chatPanelWidget,
  ]) {
    widgetRegistry.register(w)
  }
  // Upgrade the `form` widget's ResourceForm picklist/lookup/multi-picklist/rich-text fields to the
  // rich controls via @kelta/components' setComponentRegistry seam (slice 2f).
  registerFormFieldRenderers()
}

registerBuiltinWidgets()
