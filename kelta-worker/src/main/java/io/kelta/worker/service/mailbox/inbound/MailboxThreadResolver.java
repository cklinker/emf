package io.kelta.worker.service.mailbox.inbound;

import io.kelta.worker.repository.MailboxMessageRepository;
import io.kelta.worker.repository.MailboxThreadRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Decides which conversation an inbound message belongs to.
 *
 * <p>Tried in descending order of confidence. Each step is a weaker signal than the one above,
 * and the two anti-rules at the bottom exist because the weakest step is strong enough to cause
 * real harm if left unbounded.
 *
 * @since 1.0.0
 */
@Component
public class MailboxThreadResolver {

    private static final Logger log = LoggerFactory.getLogger(MailboxThreadResolver.class);

    private final MailboxThreadRepository threadRepository;
    private final MailboxMessageRepository messageRepository;
    private final io.kelta.worker.service.mailbox.MailboxVerpService verpService;

    public MailboxThreadResolver(MailboxThreadRepository threadRepository,
                                 MailboxMessageRepository messageRepository,
                                 io.kelta.worker.service.mailbox.MailboxVerpService verpService) {
        this.threadRepository = threadRepository;
        this.messageRepository = messageRepository;
        this.verpService = verpService;
    }

    /**
     * @param threadId       the conversation this message joins
     * @param created        true when a new thread was opened
     * @param parentThreadId set when a closed thread was reopened as a new one
     */
    public record Resolution(String threadId, boolean created, String parentThreadId) {
    }

    public Resolution resolve(String tenantId, Map<String, Object> mailbox,
                              NormalizedInboundMail mail, boolean requesterVerified,
                              String verificationMethod) {
        String mailboxId = (String) mailbox.get("id");

        // 0. Our own signed thread token in the recipient address. The strongest signal available:
        //    it is deterministic and survives clients that strip References. The HMAC is what makes
        //    it safe — a bare +t<threadId> address would let anyone who learns a thread id post
        //    into that conversation.
        Optional<String> byToken = verpService.threadIdFrom(mail.toAddresses());
        if (byToken.isPresent() && threadRepository.findById(byToken.get(), tenantId).isPresent()) {
            return join(tenantId, mailbox, mail, byToken.get(), requesterVerified, verificationMethod);
        }

        // 1. In-Reply-To, an exact reference to a message we sent or stored.
        Optional<String> byInReplyTo = messageRepository
                .findThreadIdByMessageId(tenantId, mailboxId, mail.inReplyTo());
        if (byInReplyTo.isPresent()) {
            return join(tenantId, mailbox, mail, byInReplyTo.get(), requesterVerified, verificationMethod);
        }

        // 2. Any id in the References chain, newest first. Some clients drop In-Reply-To but
        //    keep References, and the chain is ordered oldest to newest.
        if (mail.references() != null) {
            String[] ids = mail.references().trim().split("\\s+");
            for (int i = ids.length - 1; i >= 0; i--) {
                Optional<String> match = messageRepository
                        .findThreadIdByMessageId(tenantId, mailboxId, ids[i]);
                if (match.isPresent()) {
                    return join(tenantId, mailbox, mail, match.get(), requesterVerified, verificationMethod);
                }
            }
        }

        // 3. Subject, but only from the same requester and only within the configured window.
        String normalized = MailboxThreadRepository.normalizeSubject(mail.subject());
        int days = intOf(mailbox.get("subject_threading_days"), 7);
        if (mail.fromAddress() != null) {
            Optional<String> bySubject = threadRepository
                    .findBySubject(tenantId, mailboxId, normalized, mail.fromAddress(), days);
            if (bySubject.isPresent()) {
                return join(tenantId, mailbox, mail, bySubject.get(), requesterVerified, verificationMethod);
            }
        }

        return new Resolution(
                createThread(tenantId, mailboxId, mail, normalized, requesterVerified,
                        verificationMethod, null),
                true, null);
    }

    /**
     * Joins an existing thread, unless it is closed.
     *
     * <p>A reply arriving after a thread was resolved opens a <b>new</b> thread linked by
     * {@code parent_thread_id} rather than reviving the old one. Reviving would resurrect a
     * thread whose SLA clock has long since been settled, so the reopened conversation would be
     * born already breached and would page someone immediately.
     */
    private Resolution join(String tenantId, Map<String, Object> mailbox, NormalizedInboundMail mail,
                            String threadId, boolean requesterVerified, String verificationMethod) {
        if (threadRepository.isClosed(tenantId, threadId)) {
            String normalized = MailboxThreadRepository.normalizeSubject(mail.subject());
            String newId = createThread(tenantId, (String) mailbox.get("id"), mail, normalized,
                    requesterVerified, verificationMethod, threadId);
            log.info("Reply arrived on closed thread {} — opened {} instead", threadId, newId);
            return new Resolution(newId, true, threadId);
        }
        return new Resolution(threadId, false, null);
    }

    private String createThread(String tenantId, String mailboxId, NormalizedInboundMail mail,
                                String normalizedSubject, boolean requesterVerified,
                                String verificationMethod, String parentThreadId) {
        return threadRepository.create(tenantId, mailboxId,
                mail.subject(), normalizedSubject,
                mail.fromAddress() == null ? "unknown@invalid" : mail.fromAddress(),
                mail.fromName(),
                requesterVerified, verificationMethod,
                "OPEN", parentThreadId);
    }

    private static int intOf(Object value, int fallback) {
        return value instanceof Number n ? n.intValue() : fallback;
    }
}
