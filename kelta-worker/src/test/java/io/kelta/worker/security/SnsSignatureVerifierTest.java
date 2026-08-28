package io.kelta.worker.security;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/**
 * Uses a throwaway RSA keypair and self-signed cert (CN=sns.us-east-1.amazonaws.com, generated
 * once with {@code keytool}, valid to 2036) fixed as test data below — never the real SNS signing
 * key. The point is exercising the same RSA/X.509 verification path the real one uses, keyed to
 * a cert whose host passes the verifier's SNS-hostname check.
 */
@DisplayName("SnsSignatureVerifier")
class SnsSignatureVerifierTest {

    private static final String CERT_URL = "https://sns.us-east-1.amazonaws.com/test-cert.pem";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TEST_CERT_PEM = """
            -----BEGIN CERTIFICATE-----
            MIIC7zCCAdegAwIBAgIIUPcicivovN8wDQYJKoZIhvcNAQEMBQAwJjEkMCIGA1UE
            AxMbc25zLnVzLWVhc3QtMS5hbWF6b25hd3MuY29tMB4XDTI2MDgyODAzMTYyOVoX
            DTM2MDgyNTAzMTYyOVowJjEkMCIGA1UEAxMbc25zLnVzLWVhc3QtMS5hbWF6b25h
            d3MuY29tMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEApo5JfyVOSd8m
            y+YOIyA2iFs+xDtyxbF9LuyQ5GYmQfoWXYYxUcnodbcJ5T9n3dqbCLTh7H+RtIuS
            RrQWADHMfEjxy1teK0Thu5yo/Vz4gViH9dr/FRmSThbdtMOcGDm5txL+dNNir5PM
            ATlKrlFwhAbposrHtaRz8r8mgK4lvzSpMVuwTex5XBQ7KqYEPKq7hLX/EiJqTRSK
            cc9a9iTqvMqqYHjfL5FdP3w2VWXAlYpuxgoPFUa0OpY0BS5irTH35AH9OH/EDfiK
            YRi1OR4UDlUCoKFD0enaJ/u7qZmlhkfGdBi+r7SiKmTNELpv7M2iFFMa5X6V8gZH
            YxL7KIdSHQIDAQABoyEwHzAdBgNVHQ4EFgQUMbItCR56tpfJAOS8/WfA6H+9sLMw
            DQYJKoZIhvcNAQEMBQADggEBAAIw3VOywR1vCYVWtyl3jfumXvWlzInFnBvf+q07
            a8kBoc1k/KaInkvLhr3y1xth4Ok9/5PevoaU+W35FmIwGdf0NbYPl6zCGIUd5a6o
            /zql58wLbmHM148RJ0Wq4nQ8uXIiSd1gf50MmU5mvgmZIXmc0lTPIlDPBhGf27TS
            lGX4IoHimL55uOYPaPfHyZOuW0D5pAf0Wl9y/sWTdem3o3HqItrIUVS88E0dAMRv
            O8CvOeS/kIUser4rpVbhtzSbw/HTyQOaFNTIxlL3c/y0fdX1hXxLjhFxtSS//3pl
            WAZPaB6QrOfVvMB0bVR9cdfwh9JuegCbpHpLS6WysK3N/OA=
            -----END CERTIFICATE-----
            """;

    private static final String TEST_KEY_PKCS8_BASE64 = """
            MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCmjkl/JU5J3ybL
            5g4jIDaIWz7EO3LFsX0u7JDkZiZB+hZdhjFRyeh1twnlP2fd2psItOHsf5G0i5JG
            tBYAMcx8SPHLW14rROG7nKj9XPiBWIf12v8VGZJOFt20w5wYObm3Ev5002Kvk8wB
            OUquUXCEBumiyse1pHPyvyaAriW/NKkxW7BN7HlcFDsqpgQ8qruEtf8SImpNFIpx
            z1r2JOq8yqpgeN8vkV0/fDZVZcCVim7GCg8VRrQ6ljQFLmKtMffkAf04f8QN+Iph
            GLU5HhQOVQKgoUPR6don+7upmaWGR8Z0GL6vtKIqZM0Qum/szaIUUxrlfpXyBkdj
            Evsoh1IdAgMBAAECggEABvFF44Ce1YnxcEzX2IN6em8RO/U9aLun++l89aRDYnsl
            7QKPsjjQEX4OccmVjwQSc8e0ZhsALA4oS/2sMBhjTWft+jvF/lg574vNaHg0XJHf
            4vHcsZi2Bj7CQYtoyYK4eMbYJ+76vVZUCdYFRKRc5Af0u6YnsmzXL25j0LfRl3lt
            fIfVAZ9w+xM4SwlgE/hDGKJfYNgB2XbAneZ+RJYT1UtGmfquewn0rHg8tURJPaUo
            11NnnDlJ6nm2f9lZZ2q0LdwOBymj6Cb06N5sAC+R67YCwr1Ydh+9OXV91OMmIXVa
            D82EP8wDtTiHUH1AZYGeh/Qz1EOJgvBVG+0a91tMYQKBgQDGTkP7pkrDlI4Q+6IY
            Zso94eU0YE9le0dyd73blGXz0MEy/Aky4ilyzDkG70gFa4k9j4oo4vJG4YoGwVOC
            rqYb44lHU3jUukS0UH1G70bnvkyExRku3Q5nrKmpGcEa9quxIqasVANdXmRD4xs1
            B0bFf42TcBZdK0nTk9CDpOHvPQKBgQDXA0zKYIIwfqq/oa47yXmWgMb/Y0e+U6d1
            3N3Sl2M6LEpTT/r1HrgvMhHyIj7DoMuE/lez0FzNJPpfrT/NrAWnVEyVEyQ2r2/L
            fd5gnqTc3VvIE/vMYPWndS+DGVMLr+s4zCG7RQM+WwJWJmBiBV0/6O1gnilKj4HO
            ndRXR9UcYQKBgELjv3aw3uMdxtXfqwuEEI/TFJcYLW/Tp4Pq8/WPFtyo+6IQ9aFe
            PBDm5g16xNZfMYm8UyP7eTGDfiVNLLgV5R3fID0Y4NcQhYA07izxJP+iZvkZ6qau
            Fd/Ok+PgNgy8S6mSQJo8NG5YGoXzowe+DpcrsTucQU7n/R0K7Pias0MZAoGBANVl
            PQ2OgyfGT4kp2lcUO0yYOui1jDrlo8pijvW3so/F7W6KbrRg7MRKmW0Ld+eI6vTv
            yfN41OH11VioBi8GkAXmsKsz+DkeHYKFRfP3AAEE4VbOHpZVlPnCYIlo1PdAfeA9
            GS1X7UQx5zvBkt82G7qWXyIJV0nc7CQ4mMqXTHeBAoGAUVza/ZrVDTTMU1ov8xEc
            nWDRTbU6u+nwEYm5dk26hPLmuXrsVMTHw5cNjy/WxUNdN958WNkTnlnOMIcirasE
            gkpc/fB+8xOa+YIRevDmLXV2kwShhu2Bn0qoTlw0LGMkWRfpquls6ajdREWWAsss
            V4grL1vtzfo5tzyhOFMZg98=
            """;

    private static byte[] certPem;
    private static PrivateKey privateKey;

    @BeforeAll
    static void loadKeyMaterial() throws Exception {
        certPem = TEST_CERT_PEM.getBytes(StandardCharsets.UTF_8);
        byte[] keyBytes = Base64.getMimeDecoder().decode(TEST_KEY_PKCS8_BASE64);
        privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
    }

    private RestClient.Builder mockBuilder(MockRestServiceServer[] serverOut) {
        RestClient.Builder builder = RestClient.builder();
        serverOut[0] = MockRestServiceServer.bindTo(builder).build();
        return builder;
    }

    private String sign(String stringToSign, String algorithm) throws Exception {
        Signature sig = Signature.getInstance(algorithm);
        sig.initSign(privateKey);
        sig.update(stringToSign.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(sig.sign());
    }

    private String notificationStringToSign(String message, String messageId, String subject,
                                             String timestamp, String topicArn, String type) {
        StringBuilder sb = new StringBuilder();
        sb.append("Message\n").append(message).append('\n');
        sb.append("MessageId\n").append(messageId).append('\n');
        if (subject != null) {
            sb.append("Subject\n").append(subject).append('\n');
        }
        sb.append("Timestamp\n").append(timestamp).append('\n');
        sb.append("TopicArn\n").append(topicArn).append('\n');
        sb.append("Type\n").append(type).append('\n');
        return sb.toString();
    }

    private ObjectNode notification(String message, String messageId, String subject, String timestamp,
                                     String topicArn, String signature, String signatureVersion) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("Type", "Notification");
        node.put("Message", message);
        node.put("MessageId", messageId);
        if (subject != null) node.put("Subject", subject);
        node.put("Timestamp", timestamp);
        node.put("TopicArn", topicArn);
        node.put("SignatureVersion", signatureVersion);
        node.put("Signature", signature);
        node.put("SigningCertURL", CERT_URL);
        return node;
    }

    @Test
    @DisplayName("verifies a correctly-signed Notification (SignatureVersion 1, no Subject)")
    void verifiesValidNotification() throws Exception {
        MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
        RestClient client = mockBuilder(serverHolder).build();
        serverHolder[0].expect(requestTo(CERT_URL))
                .andRespond(withStatus(HttpStatus.OK).body(certPem).contentType(MediaType.TEXT_PLAIN));

        String toSign = notificationStringToSign("hello", "msg-1", null, "2026-08-28T00:00:00Z",
                "arn:aws:sns:us-east-1:123:topic", "Notification");
        String signature = sign(toSign, "SHA1withRSA");
        JsonNode message = notification("hello", "msg-1", null, "2026-08-28T00:00:00Z",
                "arn:aws:sns:us-east-1:123:topic", signature, "1");

        assertThat(new SnsSignatureVerifier(client).verify(message)).isTrue();
    }

    @Test
    @DisplayName("verifies a correctly-signed Notification with a Subject (SignatureVersion 2)")
    void verifiesValidNotificationWithSubjectAndSha256() throws Exception {
        MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
        RestClient client = mockBuilder(serverHolder).build();
        serverHolder[0].expect(requestTo(CERT_URL))
                .andRespond(withStatus(HttpStatus.OK).body(certPem).contentType(MediaType.TEXT_PLAIN));

        String toSign = notificationStringToSign("hello", "msg-2", "a subject", "2026-08-28T00:00:00Z",
                "arn:aws:sns:us-east-1:123:topic", "Notification");
        String signature = sign(toSign, "SHA256withRSA");
        JsonNode message = notification("hello", "msg-2", "a subject", "2026-08-28T00:00:00Z",
                "arn:aws:sns:us-east-1:123:topic", signature, "2");

        assertThat(new SnsSignatureVerifier(client).verify(message)).isTrue();
    }

    @Test
    @DisplayName("rejects a message whose body was tampered with after signing")
    void rejectsTamperedMessage() throws Exception {
        MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
        RestClient client = mockBuilder(serverHolder).build();
        serverHolder[0].expect(requestTo(CERT_URL))
                .andRespond(withStatus(HttpStatus.OK).body(certPem).contentType(MediaType.TEXT_PLAIN));

        String toSign = notificationStringToSign("hello", "msg-3", null, "2026-08-28T00:00:00Z",
                "arn:aws:sns:us-east-1:123:topic", "Notification");
        String signature = sign(toSign, "SHA1withRSA");
        // Attacker swaps the message content after the signature was computed.
        JsonNode message = notification("forged", "msg-3", null, "2026-08-28T00:00:00Z",
                "arn:aws:sns:us-east-1:123:topic", signature, "1");

        assertThat(new SnsSignatureVerifier(client).verify(message)).isFalse();
    }

    @Test
    @DisplayName("rejects a SigningCertURL that is not a real SNS endpoint, without fetching it")
    void rejectsSpoofedCertHost() {
        MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
        RestClient client = mockBuilder(serverHolder).build();
        // No .expect(...) registered — any HTTP call at all fails server.verify() below.

        ObjectNode message = notification("hello", "msg-4", null, "2026-08-28T00:00:00Z",
                "arn:aws:sns:us-east-1:123:topic", "irrelevant-signature", "1");
        message.put("SigningCertURL", "https://sns.us-east-1.amazonaws.com.evil.test/cert.pem");

        assertThat(new SnsSignatureVerifier(client).verify(message)).isFalse();
        serverHolder[0].verify();
    }
}
