package io.kelta.worker.service.mailbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

/**
 * Fetches raw MIME from the AWS S3 bucket that SES's receipt rule writes to.
 *
 * <p>Separate from {@link io.kelta.worker.service.S3StorageService} because that one is bound to
 * Garage, the self-hosted store the platform uses for everything else, and SES can only write to
 * a real AWS bucket. Modelled on {@code SpotopenedMediaStorageService}, whose javadoc already
 * argues why a second isolated client beats a second {@code S3ConfigProperties} bean: the latter
 * would force a {@code @Qualifier} onto every existing injection site.
 *
 * <p>The AWS bucket is a <b>spool, not an archive</b>. It has a short lifecycle policy; the
 * durable copy of every message is re-uploaded into Garage by the ingest pipeline.
 *
 * <p>Deliberately no {@code endpointOverride} — that is what makes this client reach real AWS
 * rather than Garage, and it is the single line that distinguishes the two.
 *
 * @since 1.0.0
 */
@Service
public class SesInboundMailStore {

    private static final Logger log = LoggerFactory.getLogger(SesInboundMailStore.class);

    /**
     * Refuse to materialise anything larger. SES caps a received message at 40 MB, so a larger
     * object means a misconfigured bucket or a wrong key, not a real email.
     */
    private static final long MAX_OBJECT_BYTES = 45L * 1024 * 1024;

    private final boolean enabled;
    private final S3Client client;

    public SesInboundMailStore(
            @Value("${kelta.mailbox.ses-inbound.enabled:false}") boolean enabled,
            @Value("${kelta.mailbox.ses-inbound.region:us-east-1}") String region,
            @Value("${kelta.mailbox.ses-inbound.access-key:}") String accessKey,
            @Value("${kelta.mailbox.ses-inbound.secret-key:}") String secretKey) {
        this.enabled = enabled;
        if (enabled) {
            var builder = S3Client.builder().region(Region.of(region));
            if (!accessKey.isBlank() && !secretKey.isBlank()) {
                builder.credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)));
            } else {
                // Falls back to the standard chain so the pod can use an IRSA role instead of
                // long-lived keys in config, which is the better posture where it is available.
                builder.credentialsProvider(DefaultCredentialsProvider.create());
            }
            this.client = builder.build();
            log.info("SesInboundMailStore initialised in region {}", region);
        } else {
            this.client = null;
            log.info("SesInboundMailStore disabled (kelta.mailbox.ses-inbound.enabled=false)");
        }
    }

    public boolean isEnabled() {
        return enabled && client != null;
    }

    /**
     * Reads one stored message.
     *
     * <p>Throws rather than returning empty on failure. A message SES accepted but that we cannot
     * fetch is a real customer email we are about to lose; the caller leaves the ledger row in
     * {@code RECEIVED} so it can be re-driven, which only works if the failure is loud.
     */
    public byte[] fetch(String bucket, String key) {
        if (!isEnabled()) {
            throw new IllegalStateException(
                    "SesInboundMailStore is disabled — set kelta.mailbox.ses-inbound.enabled=true");
        }
        ResponseBytes<GetObjectResponse> object = client.getObjectAsBytes(
                GetObjectRequest.builder().bucket(bucket).key(key).build());

        long length = object.response().contentLength() == null ? 0 : object.response().contentLength();
        if (length > MAX_OBJECT_BYTES) {
            throw new IllegalStateException(
                    "Refusing to read " + length + " bytes from s3://" + bucket + "/" + key);
        }
        return object.asByteArray();
    }
}
