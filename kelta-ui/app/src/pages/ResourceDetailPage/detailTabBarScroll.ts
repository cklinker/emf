/**
 * Scrolls the detail tab bar into view. The activity timeline's version links
 * switch to the History tab via a `?tab=` search param, but the tab bar sits
 * below the (often long) record body — without this the switch happens
 * off-screen and the click looks like a no-op. Deferred a frame so the newly
 * selected tab panel has mounted before the scroll.
 */
export function scrollDetailTabBarIntoView(): void {
  requestAnimationFrame(() => {
    document
      .querySelector('[data-testid="detail-tab-bar"]')
      ?.scrollIntoView?.({ behavior: 'smooth', block: 'start' })
  })
}
