package io.kelta.worker.service.mailbox;

import io.kelta.worker.repository.EmailSuppressionRepository;
import io.kelta.worker.repository.MailboxMessageRepository;
import io.kelta.worker.repository.MailboxRepository;
import io.kelta.worker.repository.MailboxThreadRepository;
import io.kelta.worker.service.email.DefaultEmailService;
import io.kelta.worker.service.email.EmailHeaders;
import io.kelta.worker.service.email.SendResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("MailboxReplyService")
class MailboxReplyServiceTest {

    private static final String TENANT = "t1";
    private static final String THREAD = "th1";
    private static final String MAILBOX = "mb1";

    private MailboxRepository mailboxRepository;
    private MailboxThreadRepository threadRepository;
    private MailboxMessageRepository messageRepository;
    private EmailSuppressionRepository suppressionRepository;
    private DefaultEmailService emailService;
    private JdbcTemplate jdbcTemplate;
    private MailboxReplyService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mailboxRepository = mock(MailboxRepository.class);
        threadRepository = mock(MailboxThreadRepository.class);
        messageRepository = mock(MailboxMessageRepository.class);
        suppressionRepository = mock(EmailSuppressionRepository.class);
        emailService = mock(DefaultEmailService.class);
        jdbcTemplate = mock(JdbcTemplate.class);

        ObjectProvider<DefaultEmailService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(emailService);

        service = new MailboxReplyService(mailboxRepository, threadRepository, messageRepository,
                suppressionRepository, new MailboxVerpService("secret"), provider, jdbcTemplate);

        Map<String, Object> thread = new HashMap<>(Map.of(
                "id", THREAD, "tenant_id", TENANT, "mailbox_id", MAILBOX,
                "subject", "Booking question", "requester_email", "alex@example.com"));
        Map<String, Object> mailbox = new HashMap<>(Map.of(
                "id", MAILBOX, "tenant_id", TENANT, "name", "Support",
                "address", "support@spotopened.com", "verp_domain", "spotopened.com",
                "reply_from_address", "support@spotopened.com"));

        when(threadRepository.findById(THREAD, TENANT)).thenReturn(Optional.of(thread));
        when(mailboxRepository.findById(MAILBOX, TENANT)).thenReturn(Optional.of(mailbox));
        when(suppressionRepository.isSuppressed(anyString(), anyString())).thenReturn(false);
        when(messageRepository.insertInbound(any())).thenReturn("msg-1");
        when(emailService.queueReply(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyList(),
                any(EmailHeaders.class)))
                .thenReturn(CompletableFuture.completedFuture(SendResult.sent("<out@kelta>")));
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
    }

    private void lastInbound(Map<String, Object> row) {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(row == null ? List.of() : List.of(row));
    }

    @Test
    @DisplayName("Sends to the thread's requester and stops the first-response clock")
    void sendsAndStopsTheClock() {
        MailboxReplyService.Result result =
                service.reply(TENANT, THREAD, "Here is your answer.", "user-1", false);

        assertThat(result.sent()).isTrue();
        verify(emailService).queueReply(eq(TENANT), eq("alex@example.com"), eq("Re: Booking question"),
                eq("Here is your answer."), eq("support-mailbox"), eq(THREAD),
                eq("support@spotopened.com"), eq("Support"), anyList(),
                any(EmailHeaders.class));
        verify(threadRepository).recordFirstResponse(TENANT, THREAD);
    }

    @Test
    @DisplayName("Reply-To carries a signed thread token so the customer's reply threads")
    void setsVerpReplyTo() {
        service.reply(TENANT, THREAD, "body", "user-1", false);

        assertThat(capturedHeaders().replyTo())
                .startsWith("support+t" + THREAD + ".")
                .endsWith("@spotopened.com");
    }

    @Test
    @DisplayName("A human reply carries no Auto-Submitted; a generated one does")
    void marksAutomatedRepliesOnly() {
        service.reply(TENANT, THREAD, "typed by a person", "user-1", false);
        assertThat(capturedHeaders().autoSubmitted()).isNull();

        clearInvocations(emailService);
        service.reply(TENANT, THREAD, "generated", "user-1", true);
        // Without this the far side's autoresponder answers us and the two loop.
        assertThat(capturedHeaders().autoSubmitted()).isEqualTo("auto-replied");
    }

    @Test
    @DisplayName("Threads the reply onto the message it answers")
    void setsThreadingHeaders() {
        lastInbound(new HashMap<>(Map.of(
                "message_id", "<in-2@mail.example.com>",
                "references_header", "<root@mail.example.com>")));

        service.reply(TENANT, THREAD, "body", "user-1", false);

        EmailHeaders headers = capturedHeaders();
        assertThat(headers.inReplyTo()).isEqualTo("<in-2@mail.example.com>");
        assertThat(headers.references())
                .contains("<root@mail.example.com>")
                .contains("<in-2@mail.example.com>");
    }

    @Test
    @DisplayName("A header-injection attempt costs the threading hints, not the reply")
    void survivesHeaderInjectionInTheInboundMessage() {
        // In-Reply-To is copied from the customer's message, so it is attacker-controlled.
        lastInbound(new HashMap<>(Map.of(
                "message_id", "<x@y>\r\nBcc: victim@example.com",
                "references_header", "<root@mail.example.com>")));

        MailboxReplyService.Result result = service.reply(TENANT, THREAD, "body", "user-1", false);

        assertThat(result.sent()).isTrue();
        EmailHeaders headers = capturedHeaders();
        assertThat(headers.inReplyTo()).isNull();
        assertThat(headers.references()).isNull();
    }

    @Test
    @DisplayName("Refuses a suppressed address — queueEmail does not check, so this must")
    void refusesSuppressedRecipient() {
        when(suppressionRepository.isSuppressed(TENANT, "alex@example.com")).thenReturn(true);

        MailboxReplyService.Result result = service.reply(TENANT, THREAD, "body", "user-1", false);

        assertThat(result.sent()).isFalse();
        assertThat(result.refusal()).isEqualTo(MailboxReplyService.Refusal.SUPPRESSED);
        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("Refuses to answer a bounce — that is backscatter")
    void refusesBounce() {
        lastInbound(new HashMap<>(Map.of("is_bounce", true)));

        MailboxReplyService.Result result = service.reply(TENANT, THREAD, "body", "user-1", false);

        assertThat(result.refusal()).isEqualTo(MailboxReplyService.Refusal.BOUNCE_OR_AUTOMATED);
        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("Refuses to answer an auto-submitted message — two autoresponders is a loop")
    void refusesAutoSubmitted() {
        lastInbound(new HashMap<>(Map.of("auto_submitted", "auto-replied", "is_bounce", false)));

        MailboxReplyService.Result result = service.reply(TENANT, THREAD, "body", "user-1", false);

        assertThat(result.refusal()).isEqualTo(MailboxReplyService.Refusal.BOUNCE_OR_AUTOMATED);
    }

    @Test
    @DisplayName("Auto-Submitted: no is not a reason to refuse")
    void allowsAutoSubmittedNo() {
        // RFC 3834 uses "no" to mean an ordinary human message.
        lastInbound(new HashMap<>(Map.of("auto_submitted", "no", "is_bounce", false)));

        assertThat(service.reply(TENANT, THREAD, "body", "user-1", false).sent()).isTrue();
    }

    @Test
    @DisplayName("Refuses unattended addresses")
    void refusesUnattendedAddresses() {
        assertThat(MailboxReplyService.isUnattended("no-reply@example.com")).isTrue();
        assertThat(MailboxReplyService.isUnattended("MAILER-DAEMON@example.com")).isTrue();
        assertThat(MailboxReplyService.isUnattended("postmaster@example.com")).isTrue();
        assertThat(MailboxReplyService.isUnattended("alex@example.com")).isFalse();
        // Substring matches would wrongly catch a real person.
        assertThat(MailboxReplyService.isUnattended("noreplyton@example.com")).isFalse();
    }

    @Test
    @DisplayName("Adds one Re: and never a second")
    void buildsReplySubject() {
        assertThat(MailboxReplyService.replySubject("Booking")).isEqualTo("Re: Booking");
        assertThat(MailboxReplyService.replySubject("Re: Booking")).isEqualTo("Re: Booking");
        assertThat(MailboxReplyService.replySubject("RE: Booking")).isEqualTo("RE: Booking");
        assertThat(MailboxReplyService.replySubject(null)).isEqualTo("Re: (no subject)");
    }

    private EmailHeaders capturedHeaders() {
        ArgumentCaptor<EmailHeaders> captor = ArgumentCaptor.forClass(EmailHeaders.class);
        verify(emailService).queueReply(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyList(), captor.capture());
        return captor.getValue();
    }
}
