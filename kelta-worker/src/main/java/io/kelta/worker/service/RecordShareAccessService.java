package io.kelta.worker.service;

import io.kelta.worker.repository.RecordShareRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runtime enforcement of manual record shares ({@code record_share}).
 *
 * <p>Shares only ever <em>widen</em> access: after the profile-based Cerbos
 * decision, records the user was denied are re-checked against shares granted
 * to them (directly or via a group). A {@code READ} share widens reads; an
 * {@code EDIT} share widens reads and edits. Shares never widen {@code create}
 * or {@code delete}, and never narrow anything — no share rows means the
 * Cerbos decision stands untouched.
 *
 * <p>Lookups are uncached so revoking a share takes effect on the next request.
 * Any lookup failure fails closed (no widening).
 */
@Service
public class RecordShareAccessService {

    private static final Logger log = LoggerFactory.getLogger(RecordShareAccessService.class);

    static final String ACCESS_READ = "READ";
    static final String ACCESS_EDIT = "EDIT";

    private final RecordShareRepository repository;

    public RecordShareAccessService(RecordShareRepository repository) {
        this.repository = repository;
    }

    /**
     * Returns the subset of {@code deniedRecordIds} that a record share grants
     * the user for {@code action}. Empty for actions shares can't widen.
     *
     * @param email           the invoking user's email (resolved to a user id)
     * @param collectionName  the collection name from the request path
     * @param deniedRecordIds record ids the profile-based check denied
     * @param action          the Cerbos action ({@code read}, {@code edit}, …)
     */
    public Set<String> widen(String email, String collectionName,
                             Set<String> deniedRecordIds, String action) {
        if (deniedRecordIds.isEmpty() || email == null || email.isBlank()) {
            return Set.of();
        }
        boolean isRead = "read".equals(action);
        boolean isEdit = "edit".equals(action);
        if (!isRead && !isEdit) {
            return Set.of();
        }

        try {
            String userId = repository.findUserIdByEmail(email);
            if (userId == null) {
                return Set.of();
            }
            List<String> groupIds = repository.findGroupIdsForUser(userId);
            List<Map<String, Object>> shares =
                    repository.findSharesForPrincipal(collectionName, deniedRecordIds, userId, groupIds);

            Set<String> widened = new HashSet<>();
            for (Map<String, Object> share : shares) {
                String level = (String) share.get("accessLevel");
                boolean grants = isRead
                        ? (ACCESS_READ.equals(level) || ACCESS_EDIT.equals(level))
                        : ACCESS_EDIT.equals(level);
                if (grants) {
                    widened.add((String) share.get("recordId"));
                }
            }
            if (!widened.isEmpty()) {
                log.debug("Record shares widened {} access to {} record(s) in '{}' for {}",
                        action, widened.size(), collectionName, email);
            }
            return widened;
        } catch (Exception e) {
            log.error("Record-share widening failed (fail-closed, no widening): collection={} error={}",
                    collectionName, e.getMessage(), e);
            return Set.of();
        }
    }
}
