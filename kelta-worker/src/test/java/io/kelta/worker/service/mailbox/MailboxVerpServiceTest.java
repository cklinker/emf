package io.kelta.worker.service.mailbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MailboxVerpService")
class MailboxVerpServiceTest {

    private static final String THREAD = "3f2504e0-4f89-11d3-9a0c-0305e82c3301";

    private final MailboxVerpService service = new MailboxVerpService("test-secret");

    @Test
    @DisplayName("Refuses to start without a secret rather than signing with a default")
    void refusesBlankSecret() {
        // A fallback default would mean every deployment signs with a key published in this
        // repository, and the warning would scroll past on every boot.
        assertThatThrownBy(() -> new MailboxVerpService(""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KELTA_MAILBOX_VERP_SECRET");
        assertThatThrownBy(() -> new MailboxVerpService(null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Mints an address that round-trips back to the thread")
    void roundTrips() {
        String address = service.replyToAddress("support", "spotopened.com", THREAD).orElseThrow();

        assertThat(address).startsWith("support+t" + THREAD + ".").endsWith("@spotopened.com");
        assertThat(service.threadIdFrom(address)).contains(THREAD);
    }

    @Test
    @DisplayName("Finds the token among several recipients")
    void findsTokenInARecipientList() {
        String address = service.replyToAddress("support", "spotopened.com", THREAD).orElseThrow();
        String header = "Someone <someone@example.com>, " + address + ", cc@example.com";

        assertThat(service.threadIdFrom(header)).contains(THREAD);
    }

    @Test
    @DisplayName("A forged tag is rejected — this is what stops anyone posting into a thread by id")
    void rejectsForgedTag() {
        // Without the HMAC, knowing or guessing a thread id would be enough to address a message
        // into that conversation and inherit whatever trust it carries.
        String forged = "support+t" + THREAD + ".deadbeef@spotopened.com";

        assertThat(service.threadIdFrom(forged)).isEmpty();
    }

    @Test
    @DisplayName("A token signed with a different secret does not verify")
    void rejectsForeignSignature() {
        String fromElsewhere = new MailboxVerpService("another-secret")
                .replyToAddress("support", "spotopened.com", THREAD).orElseThrow();

        assertThat(service.threadIdFrom(fromElsewhere)).isEmpty();
    }

    @Test
    @DisplayName("A tag valid for one thread does not verify for another")
    void tagIsBoundToItsThread() {
        String other = "11111111-2222-3333-4444-555555555555";
        String address = service.replyToAddress("support", "spotopened.com", THREAD).orElseThrow();
        String tag = address.substring(address.indexOf('.') + 1, address.indexOf('@'));

        assertThat(service.threadIdFrom("support+t" + other + "." + tag + "@spotopened.com")).isEmpty();
    }

    @Test
    @DisplayName("No VERP domain means no token, so the caller falls back to a plain Reply-To")
    void noDomainMeansNoToken() {
        assertThat(service.replyToAddress("support", "", THREAD)).isEmpty();
        assertThat(service.replyToAddress("support", null, THREAD)).isEmpty();
        assertThat(service.replyToAddress("", "spotopened.com", THREAD)).isEmpty();
    }

    @Test
    @DisplayName("Ordinary addresses carry no token and are simply not matched")
    void plainAddressYieldsNothing() {
        assertThat(service.threadIdFrom("support@spotopened.com")).isEmpty();
        assertThat(service.threadIdFrom(null)).isEmpty();
        assertThat(service.threadIdFrom("")).isEmpty();
    }
}
