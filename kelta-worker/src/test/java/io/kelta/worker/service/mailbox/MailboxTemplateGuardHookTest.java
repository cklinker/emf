package io.kelta.worker.service.mailbox;

import io.kelta.runtime.workflow.BeforeSaveResult;
import io.kelta.worker.repository.EmailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The author-time gate on auto-sendable copy.
 *
 * <p>It exists because {@code DefaultEmailService.substitute()} does no HTML escaping, so a
 * variable whose value comes from an inbound message is an HTML injection into mail sent from our
 * own authenticated domain — and an auto-sent reply has no reviewer to notice.
 */
@DisplayName("MailboxTemplateGuardHook")
class MailboxTemplateGuardHookTest {

    private static final String TENANT = "t1";

    private EmailRepository emailRepository;
    private MailboxTemplateGuardHook hook;

    @BeforeEach
    void setUp() {
        emailRepository = mock(EmailRepository.class);
        hook = new MailboxTemplateGuardHook(emailRepository);
        template("safe.key", "Hi from ${mailboxName}", "<p>Reach us at ${supportAddress}.</p>");
    }

    private void template(String key, String subject, String bodyHtml) {
        when(emailRepository.findTemplateByKey(eq(TENANT), eq(key))).thenReturn(Optional.of(
                Map.of("subject", subject, "body_html", bodyHtml)));
    }

    private Map<String, Object> record(String key, boolean autoSend) {
        Map<String, Object> r = new HashMap<>();
        r.put("templateKey", key);
        r.put("autoSendEligible", autoSend);
        return r;
    }

    @Test
    @DisplayName("Accepts an auto-sendable template that uses only platform constants")
    void acceptsConstantsOnly() {
        assertThat(hook.beforeCreate(record("safe.key", true), TENANT).isSuccess()).isTrue();
    }

    @Test
    @DisplayName("Refuses auto-send when the copy references anything derived from the message")
    void refusesInboundDerivedVariables() {
        template("unsafe.key", "Re: ${subject}", "<p>Hello ${requesterName},</p>");

        BeforeSaveResult result = hook.beforeCreate(record("unsafe.key", true), TENANT);

        assertThat(result.isSuccess()).isFalse();
        // The message names the offending variables so the author can fix it rather than guess.
        assertThat(result.getErrors().toString())
                .contains("${requesterName}")
                .contains("${subject}")
                .contains("does not escape HTML");
    }

    @Test
    @DisplayName("The same template is fine when it is NOT auto-sendable — a human reviews it")
    void allowsVariablesWhenNotAutoSent() {
        template("unsafe.key", "Re: ${subject}", "<p>Hello ${requesterName},</p>");

        assertThat(hook.beforeCreate(record("unsafe.key", false), TENANT).isSuccess()).isTrue();
    }

    @Test
    @DisplayName("Flipping an existing template to auto-send is validated against the merged record")
    void validatesPartialUpdateAgainstPrevious() {
        template("unsafe.key", "Re: ${subject}", "<p>Hi ${requesterName}</p>");

        // The dangerous edit: a PATCH carrying only the boolean. Validating the submitted map
        // alone would find no templateKey and wave through exactly this change.
        Map<String, Object> patch = new HashMap<>();
        patch.put("autoSendEligible", true);
        Map<String, Object> previous = Map.of("templateKey", "unsafe.key", "autoSendEligible", false);

        BeforeSaveResult result = hook.beforeUpdate("tpl-1", patch, previous, TENANT);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrors().toString()).contains("${requesterName}");
    }

    @Test
    @DisplayName("Refuses a template whose copy does not exist")
    void refusesMissingTemplate() {
        when(emailRepository.findTemplateByKey(eq(TENANT), anyString())).thenReturn(Optional.empty());

        BeforeSaveResult result = hook.beforeCreate(record("nope.key", false), TENANT);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrors().toString()).contains("No active email template");
    }

    @Test
    @DisplayName("Account-disclosing copy must also demand a verified sender")
    void refusesDisclosureWithoutVerification() {
        Map<String, Object> r = record("safe.key", true);
        r.put("disclosesAccountData", true);
        r.put("requiresVerifiedSender", false);

        BeforeSaveResult result = hook.beforeCreate(r, TENANT);

        // From: is trivially spoofable, so account-specific copy without a verification demand is
        // a disclosure waiting for someone to ask for it.
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrors().toString()).contains("verified");
    }

    @Test
    @DisplayName("Account-disclosing copy is fine once it demands verification")
    void allowsDisclosureWithVerification() {
        Map<String, Object> r = record("safe.key", true);
        r.put("disclosesAccountData", true);
        r.put("requiresVerifiedSender", true);

        assertThat(hook.beforeCreate(r, TENANT).isSuccess()).isTrue();
    }

    @Test
    @DisplayName("templateKey is required")
    void requiresTemplateKey() {
        assertThat(hook.beforeCreate(new HashMap<>(), TENANT).isSuccess()).isFalse();
    }

    @Test
    @DisplayName("Only fires for its own collection")
    void scopedToItsCollection() {
        assertThat(hook.getCollectionName()).isEqualTo("mailbox-templates");
    }
}
