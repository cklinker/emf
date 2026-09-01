package io.kelta.worker.service.mailbox;

import io.kelta.worker.repository.MailboxAccessRepository;
import io.kelta.worker.repository.MailboxRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decides what a given user may do with a given mailbox.
 *
 * <p>Central because this is the whole authorization story for the console. {@code /api/support/**}
 * is a {@code static-} gateway route, so the gateway performs only the blanket {@code API_ACCESS}
 * check and every finer decision is made here.
 *
 * <p><b>Absence of access is reported as 404, never 403.</b> A 403 confirms the mailbox or thread
 * exists, which turns any id-taking endpoint into an enumeration oracle. The same reasoning is
 * already written down on {@code WatchController}.
 *
 * @since 1.0.0
 */
@Service
public class MailboxAccessGuard {

    /** Roles that may send a reply or change a thread's state. */
    private static final Set<String> CAN_ACT = Set.of("AGENT", "MANAGER");
    /** Roles that may approve a drafted reply, or act across the whole mailbox. */
    private static final Set<String> CAN_MANAGE = Set.of("MANAGER");

    private final MailboxRepository mailboxRepository;
    private final MailboxAccessRepository accessRepository;

    public MailboxAccessGuard(MailboxRepository mailboxRepository,
                              MailboxAccessRepository accessRepository) {
        this.mailboxRepository = mailboxRepository;
        this.accessRepository = accessRepository;
    }

    /** What one user may do with one mailbox. */
    public record Access(String mailboxId, List<String> roles) {

        public boolean canRead() {
            return !roles.isEmpty();
        }

        public boolean canAct() {
            return roles.stream().anyMatch(CAN_ACT::contains);
        }

        public boolean canManage() {
            return roles.stream().anyMatch(CAN_MANAGE::contains);
        }
    }

    /**
     * Resolves access, or 404s.
     *
     * <p>Roles are returned as a list rather than collapsed to a winner: a user can hold
     * {@code AGENT} directly and {@code MANAGER} through a group, and most-permissive-wins is the
     * platform's convention elsewhere. Collapsing here would silently pick one.
     */
    public Access require(String tenantId, String mailboxId, String userId) {
        if (userId == null || userId.isBlank()) {
            throw notFound();
        }
        // Confirm the mailbox exists in this tenant first, so a foreign id and a
        // no-access id are indistinguishable from outside.
        if (mailboxRepository.findById(mailboxId, tenantId).isEmpty()) {
            throw notFound();
        }
        List<String> roles = accessRepository.rolesForUser(tenantId, mailboxId, userId);
        if (roles.isEmpty()) {
            throw notFound();
        }
        return new Access(mailboxId, roles);
    }

    /** Resolves access for the mailbox owning a thread, or 404s. */
    public Access requireForThread(String tenantId, Map<String, Object> thread, String userId) {
        return require(tenantId, (String) thread.get("mailbox_id"), userId);
    }

    public void requireAct(Access access) {
        if (!access.canAct()) {
            // Safe to be specific here: the caller has already proven they can see the mailbox,
            // so naming the missing role leaks nothing they did not already know.
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "AGENT or MANAGER role required on this mailbox");
        }
    }

    public void requireManage(Access access) {
        if (!access.canManage()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "MANAGER role required on this mailbox");
        }
    }

    /** Every mailbox this user can see, for the console's selector. */
    public List<String> visibleMailboxIds(String tenantId, String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        return accessRepository.accessibleMailboxIds(tenantId, userId);
    }

    public static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
    }
}
