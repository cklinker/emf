package io.kelta.worker.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Verifies the signature Amazon SNS attaches to every delivered message, so a forged POST to a
 * public SNS-backed webhook cannot pass itself off as a real notification. Implemented on raw JDK
 * crypto rather than an AWS SNS SDK module — same call as {@code WebPushCrypto}: pulling in an SDK
 * just for this one check would add reflective surface with no native-image reachability metadata.
 *
 * @since 1.0.0
 */
public final class SnsSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(SnsSignatureVerifier.class);

    // AWS's signing cert always comes from a real SNS regional endpoint — reject anything else so
    // a forged "SigningCertURL" can't make us verify against an attacker-supplied key.
    private static final Pattern CERT_HOST = Pattern.compile("^sns\\.[a-zA-Z0-9-]{1,20}\\.amazonaws\\.com$");

    private final RestClient restClient;
    private final ConcurrentHashMap<String, X509Certificate> certCache = new ConcurrentHashMap<>();

    public SnsSignatureVerifier(RestClient restClient) {
        this.restClient = restClient;
    }

    /** True only when the message's signature verifies against a genuine SNS signing cert. */
    public boolean verify(JsonNode message) {
        return verify(message, null);
    }

    /**
     * Verifies the signature and, when {@code expectedTopicArn} is given, that the message came
     * from that specific topic.
     *
     * <p>The pin matters more than it looks. A valid signature only proves that <i>some</i> SNS
     * topic in <i>some</i> AWS account signed this message — and anyone can create a topic and
     * publish a properly signed message to a public endpoint. Without the pin, "the signature
     * checks out" means nothing stronger than "this came from AWS".
     *
     * @param expectedTopicArn the ARN this endpoint accepts, or {@code null} to accept any topic
     *                         (only appropriate where the caller has another binding to the
     *                         sender)
     */
    public boolean verify(JsonNode message, String expectedTopicArn) {
        if (expectedTopicArn != null && !expectedTopicArn.isBlank()) {
            String actual = message.path("TopicArn").asText(null);
            if (!expectedTopicArn.equals(actual)) {
                log.warn("Rejecting SNS message: TopicArn '{}' is not the expected topic", actual);
                return false;
            }
        }
        try {
            String type = message.path("Type").asText(null);
            String signingCertUrl = message.path("SigningCertURL").asText(null);
            String signature = message.path("Signature").asText(null);
            String signatureVersion = message.path("SignatureVersion").asText(null);
            if (type == null || signingCertUrl == null || signature == null) {
                return false;
            }

            if (!isRealSnsCertUrl(signingCertUrl)) {
                log.warn("Rejecting SNS message: SigningCertURL '{}' is not a real SNS endpoint", signingCertUrl);
                return false;
            }

            X509Certificate cert = certCache.computeIfAbsent(signingCertUrl, this::fetchCert);
            if (cert == null) {
                return false;
            }

            String stringToSign = canonicalize(message, type);
            String algorithm = "2".equals(signatureVersion) ? "SHA256withRSA" : "SHA1withRSA";
            Signature sig = Signature.getInstance(algorithm);
            sig.initVerify(cert.getPublicKey());
            sig.update(stringToSign.getBytes(StandardCharsets.UTF_8));
            return sig.verify(Base64.getDecoder().decode(signature));
        } catch (Exception e) {
            log.warn("SNS signature verification failed: {}", e.getMessage());
            return false;
        }
    }

    /** Also used by callers that need to validate a {@code SubscribeURL} before fetching it. */
    public static boolean isRealSnsCertUrl(String url) {
        try {
            URI uri = URI.create(url);
            return "https".equals(uri.getScheme()) && uri.getHost() != null
                    && CERT_HOST.matcher(uri.getHost()).matches();
        } catch (Exception e) {
            return false;
        }
    }

    private X509Certificate fetchCert(String url) {
        try {
            byte[] pem = restClient.get().uri(url).retrieve().body(byte[].class);
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(pem));
        } catch (Exception e) {
            log.warn("Failed to fetch SNS signing certificate from {}: {}", url, e.getMessage());
            return null;
        }
    }

    /** Field order and inclusion is fixed by AWS's spec and differs by message type. */
    private String canonicalize(JsonNode message, String type) {
        StringBuilder sb = new StringBuilder();
        if ("Notification".equals(type)) {
            append(sb, message, "Message");
            append(sb, message, "MessageId");
            if (!message.path("Subject").isMissingNode() && !message.path("Subject").isNull()) {
                append(sb, message, "Subject");
            }
            append(sb, message, "Timestamp");
            append(sb, message, "TopicArn");
            append(sb, message, "Type");
        } else {
            // SubscriptionConfirmation / UnsubscribeConfirmation
            append(sb, message, "Message");
            append(sb, message, "MessageId");
            append(sb, message, "SubscribeURL");
            append(sb, message, "Timestamp");
            append(sb, message, "Token");
            append(sb, message, "TopicArn");
            append(sb, message, "Type");
        }
        return sb.toString();
    }

    private void append(StringBuilder sb, JsonNode message, String field) {
        sb.append(field).append('\n').append(message.path(field).asText("")).append('\n');
    }
}
