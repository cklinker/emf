package io.kelta.worker.service.billing;

import java.util.List;
import java.util.Map;

/**
 * What a portal member is currently entitled to: the plan that supplied the
 * baseline, the subscription status behind it, and the merged entitlement values.
 *
 * <p>The value map is <b>opaque</b> — the platform never interprets the keys, so
 * a tenant invents limits (a cap, a feature flag, a channel list) without any
 * schema or code change. Typed accessors coerce defensively and fall back to the
 * supplied default rather than throwing, because these values come from
 * tenant-authored JSON.
 *
 * @param planCode           code of the plan providing the base entitlements, or null
 * @param subscriptionStatus processor status behind the base plan, or null when
 *                           the baseline came from the DEFAULT plan
 * @param values             merged entitlement values (base plan + live passes)
 */
public record MemberEntitlements(
        String planCode,
        String subscriptionStatus,
        Map<String, Object> values) {

    public static final MemberEntitlements EMPTY =
            new MemberEntitlements(null, null, Map.of());

    public MemberEntitlements {
        values = values == null ? Map.of() : Map.copyOf(values);
    }

    /**
     * Integer limit for {@code key}. Accepts any {@link Number} or a numeric
     * string; anything else yields {@code deflt}.
     */
    public int intValue(String key, int deflt) {
        Object raw = values.get(key);
        if (raw instanceof Number n) {
            return n.intValue();
        }
        if (raw instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return deflt;
            }
        }
        return deflt;
    }

    /** Boolean flag for {@code key}; accepts a real boolean or "true"/"false". */
    public boolean boolValue(String key, boolean deflt) {
        Object raw = values.get(key);
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw instanceof String s) {
            if ("true".equalsIgnoreCase(s.trim())) {
                return true;
            }
            if ("false".equalsIgnoreCase(s.trim())) {
                return false;
            }
        }
        return deflt;
    }

    /**
     * String list for {@code key} (e.g. permitted channels). A single string is
     * treated as a one-element list; anything unusable yields an empty list.
     */
    public List<String> listValue(String key) {
        Object raw = values.get(key);
        if (raw instanceof List<?> list) {
            return list.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(String::valueOf)
                    .toList();
        }
        if (raw instanceof String s && !s.isBlank()) {
            return List.of(s);
        }
        return List.of();
    }

    /** True when the member has no entitlements at all (no plan resolved). */
    public boolean isEmpty() {
        return values.isEmpty();
    }
}
