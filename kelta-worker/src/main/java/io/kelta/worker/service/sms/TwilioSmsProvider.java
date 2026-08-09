package io.kelta.worker.service.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Twilio SMS provider (consumer-alerting slice 10) — the production sender behind the {@code sms}
 * alert channel. Selected with {@code kelta.sms.provider=twilio}, which switches off the default
 * {@link LogOnlySmsProvider} (mutually exclusive {@code @ConditionalOnProperty} — exactly one
 * {@link SmsProvider} bean is ever active, so single-typed injection never sees two candidates).
 *
 * <p>Deliberately <b>not</b> the {@code twilio-java} SDK: like the payment client, its large
 * reflective model surface has no maintained native-image reachability metadata and would need
 * extensive {@code reflect-config.json} entries to survive the GraalVM image. One call — a
 * form-encoded POST to the Messages API on Spring's native-proven {@link RestClient} — is cheaper
 * and safer.
 *
 * <p><b>The bean constructs even when unconfigured</b> (blank credentials): validation happens on
 * {@link #send}, not at construction. If it threw in the constructor, selecting
 * {@code provider=twilio} without secrets would leave no {@code SmsProvider} bean and the whole
 * worker would fail to boot — a missing credential must degrade to a failed SMS delivery, not a
 * dead service. To go live, set {@code kelta.sms.twilio.account-sid} ({@code AC…}),
 * {@code .from-number} (E.164), and either an API key ({@code .key-sid} = {@code SK…} +
 * {@code .auth-token} = its secret, recommended) or the account Auth Token ({@code .auth-token},
 * leaving {@code .key-sid} blank) — from the deployment secret.
 */
@Component
@ConditionalOnProperty(name = "kelta.sms.provider", havingValue = "twilio")
public class TwilioSmsProvider implements SmsProvider {

    private static final Logger log = LoggerFactory.getLogger(TwilioSmsProvider.class);

    private static final String BASE_URL = "https://api.twilio.com";
    private static final String MESSAGES_PATH = "/2010-04-01/Accounts/{sid}/Messages.json";

    private final RestClient restClient;
    private final String accountSid;
    private final String keySid;
    private final String authToken;
    private final String fromNumber;

    /**
     * The constructor Spring uses. {@code @Autowired} is required, not decorative: this class has
     * a second (test-seam) constructor, and with more than one candidate Spring will not guess —
     * it falls back to a no-arg constructor and fails bean creation outright (the same trap the
     * payment client documents).
     *
     * <p><b>Two auth modes, both supported.</b> The URL always carries the {@code account-sid}
     * ({@code AC…}). Basic auth uses {@code key-sid} ({@code SK…}) + {@code auth-token} = the API
     * Key Secret when a {@code key-sid} is set (the recommended, revocable path), else the
     * {@code account-sid} + {@code auth-token} = the account Auth Token.
     */
    @Autowired
    public TwilioSmsProvider(
            @Value("${kelta.sms.twilio.account-sid:}") String accountSid,
            @Value("${kelta.sms.twilio.key-sid:}") String keySid,
            @Value("${kelta.sms.twilio.auth-token:}") String authToken,
            @Value("${kelta.sms.twilio.from-number:}") String fromNumber) {
        this(RestClient.create(), accountSid, keySid, authToken, fromNumber);
    }

    /** Test seam: lets a unit test bind a mock-server-backed client. */
    TwilioSmsProvider(RestClient restClient, String accountSid, String keySid, String authToken,
                      String fromNumber) {
        this.restClient = restClient;
        this.accountSid = accountSid;
        this.keySid = keySid;
        this.authToken = authToken;
        this.fromNumber = fromNumber;
    }

    @Override
    public void send(SmsMessage message) throws SmsDeliveryException {
        if (isBlank(accountSid) || isBlank(authToken) || isBlank(fromNumber)) {
            throw new SmsDeliveryException(
                    "Twilio is selected (kelta.sms.provider=twilio) but not configured — set "
                            + "kelta.sms.twilio.account-sid, .auth-token and .from-number");
        }
        if (message == null || isBlank(message.to()) || isBlank(message.body())) {
            throw new SmsDeliveryException("SMS message requires a recipient and a body");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("From", fromNumber);
        form.add("To", message.to());
        form.add("Body", message.body());

        try {
            restClient.post()
                    .uri(BASE_URL + MESSAGES_PATH, accountSid)
                    .headers(h -> h.setBasicAuth(basicAuthUser(), authToken))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            // Twilio returns a JSON error body ({code, message, more_info}); surface it, but
            // NEVER log the auth token or the full request.
            throw new SmsDeliveryException(
                    "Twilio send failed (" + e.getStatusCode() + "): " + e.getResponseBodyAsString(), e);
        } catch (RuntimeException e) {
            throw new SmsDeliveryException("Twilio send failed: " + e.getMessage(), e);
        }
    }

    /** Basic-auth username: the API Key SID when configured (recommended), else the Account SID. */
    private String basicAuthUser() {
        return isBlank(keySid) ? accountSid : keySid;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
