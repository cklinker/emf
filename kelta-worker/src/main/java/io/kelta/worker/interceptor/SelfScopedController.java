package io.kelta.worker.interceptor;

/**
 * Marks a controller that scopes its own response data to the calling member,
 * exempting it from {@link CerbosRecordAuthorizationAdvice}'s record-level filter.
 *
 * <p>The advice removes any record the caller's <em>profile</em> is not granted at
 * the record level. A portal member holds no record grants at all, so applying it
 * to a member-facing endpoint empties the response — the member's own watches, wins,
 * devices, and billing state vanish while every write still succeeds, which reads as
 * data loss rather than a permission error. The same failure hit the telehealth
 * portal in July 2026 and was patched there with a path exclusion.
 *
 * <p>A marker <b>interface</b>, not an annotation: the check is then a plain type
 * test with no reflection, which the native image cannot silently drop.
 *
 * <p>Implementing this is a promise: the controller resolves the acting member from
 * the request itself and returns only that member's rows (a foreign id must 404).
 * It exempts only this controller's own handlers — the generic collection route on
 * the same path keeps full record-level filtering.
 */
public interface SelfScopedController {
}
