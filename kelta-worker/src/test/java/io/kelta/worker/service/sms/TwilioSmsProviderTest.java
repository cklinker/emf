package io.kelta.worker.service.sms;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

@DisplayName("TwilioSmsProvider")
class TwilioSmsProviderTest {

    private static final String SID = "AC00000000000000000000000000000000";
    private static final String TOKEN = "test-token";
    private static final String FROM = "+15550000000";

    private record Harness(TwilioSmsProvider provider, MockRestServiceServer server) {
    }

    private Harness twilio(String sid, String token, String from) {
        return twilioWithKey(sid, "", token, from);
    }

    private Harness twilioWithKey(String sid, String keySid, String token, String from) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Harness(new TwilioSmsProvider(builder.build(), sid, keySid, token, from), server);
    }

    @Test
    @DisplayName("posts a form-encoded message to the Twilio Messages API and succeeds on 2xx")
    void sendsMessage() {
        Harness h = twilio(SID, TOKEN, FROM);
        h.server().expect(requestTo("https://api.twilio.com/2010-04-01/Accounts/" + SID + "/Messages.json"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("Authorization", org.hamcrest.Matchers.startsWith("Basic ")))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("From=%2B15550000000"),
                        org.hamcrest.Matchers.containsString("To=%2B14155551234"),
                        org.hamcrest.Matchers.containsString("Body="))))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .body("{\"sid\":\"SM1\"}").contentType(MediaType.APPLICATION_JSON));

        h.provider().send(new SmsMessage("+14155551234", "A spot just opened. Book now."));

        h.server().verify();
    }

    @Test
    @DisplayName("uses the API Key SID as the Basic-auth user when key-sid is configured")
    void usesApiKeyForBasicAuth() {
        Harness h = twilioWithKey(SID, "SKtestkey", "keysecret", FROM);
        String expected = "Basic " + java.util.Base64.getEncoder()
                .encodeToString("SKtestkey:keysecret".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        // The URL still carries the Account SID; only the Basic-auth user switches to the key.
        h.server().expect(requestTo(
                        "https://api.twilio.com/2010-04-01/Accounts/" + SID + "/Messages.json"))
                .andExpect(header("Authorization", expected))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .body("{\"sid\":\"SM1\"}").contentType(MediaType.APPLICATION_JSON));

        h.provider().send(new SmsMessage("+14155551234", "hi"));

        h.server().verify();
    }

    @Test
    @DisplayName("maps a Twilio error response to SmsDeliveryException")
    void mapsErrorResponse() {
        Harness h = twilio(SID, TOKEN, FROM);
        h.server().expect(requestTo(org.hamcrest.Matchers.containsString("/Messages.json")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .body("{\"code\":20003,\"message\":\"Authentication Error\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> h.provider().send(new SmsMessage("+14155551234", "hi")))
                .isInstanceOf(SmsDeliveryException.class)
                .hasMessageContaining("401");
    }

    @Test
    @DisplayName("fails (without any HTTP call) when Twilio is selected but not configured")
    void failsWhenUnconfigured() {
        // Blank credentials — the bean still constructs (so the worker boots), but a send is a
        // clear failure rather than a silent success.
        TwilioSmsProvider provider = twilio("", "", "").provider();

        assertThatThrownBy(() -> provider.send(new SmsMessage("+14155551234", "hi")))
                .isInstanceOf(SmsDeliveryException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    @DisplayName("rejects a message with no recipient or body")
    void rejectsEmptyMessage() {
        TwilioSmsProvider provider = twilio(SID, TOKEN, FROM).provider();

        assertThatThrownBy(() -> provider.send(new SmsMessage("", "hi")))
                .isInstanceOf(SmsDeliveryException.class);
        assertThatThrownBy(() -> provider.send(new SmsMessage("+14155551234", "  ")))
                .isInstanceOf(SmsDeliveryException.class);
    }

    @Test
    @DisplayName("constructs even with blank config so a mis-set provider cannot stop worker boot")
    void constructsWhenUnconfigured() {
        // Regression guard for the multi-bean/boot trap: selecting provider=twilio without
        // secrets must yield a live (if inert) SmsProvider bean, not a construction failure.
        assertThat(twilio("", "", "").provider()).isNotNull();
    }
}
