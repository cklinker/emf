package com.emf.worker.advice;

import com.emf.worker.service.S3StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.List;
import java.util.Map;

/**
 * Response enricher that injects presigned S3 download URLs into attachment
 * records returned by the DynamicCollectionRouter.
 *
 * <p>Intercepts JSON:API responses and checks for resources of type "attachments".
 * For each attachment resource that has a non-empty {@code storageKey} attribute,
 * generates a presigned download URL and adds it as a {@code downloadUrl} attribute.
 *
 * <p>This advice is only activated when {@link S3StorageService} is available
 * (i.e., when S3 storage is enabled).
 *
 * @since 1.0.0
 */
@ControllerAdvice
@ConditionalOnBean(S3StorageService.class)
public class AttachmentUrlEnricher implements ResponseBodyAdvice<Object> {

    private static final Logger logger = LoggerFactory.getLogger(AttachmentUrlEnricher.class);
    private static final String ATTACHMENTS_TYPE = "attachments";

    private final S3StorageService s3StorageService;

    public AttachmentUrlEnricher(S3StorageService s3StorageService) {
        this.s3StorageService = s3StorageService;
        logger.info("AttachmentUrlEnricher initialized — attachment responses will include presigned download URLs");
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // Apply to all controller responses — we filter by content in beforeBodyWrite
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object beforeBodyWrite(Object body, MethodParameter returnType,
                                   MediaType selectedContentType,
                                   Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                   ServerHttpRequest request, ServerHttpResponse response) {
        if (!(body instanceof Map)) {
            return body;
        }

        Map<String, Object> responseBody = (Map<String, Object>) body;
        Object data = responseBody.get("data");

        if (data instanceof Map) {
            // Single resource response
            enrichIfAttachment((Map<String, Object>) data);
        } else if (data instanceof List) {
            // List response
            for (Object item : (List<?>) data) {
                if (item instanceof Map) {
                    enrichIfAttachment((Map<String, Object>) item);
                }
            }
        }

        // Also enrich any included resources (e.g., from ?include=attachments)
        Object included = responseBody.get("included");
        if (included instanceof List) {
            for (Object item : (List<?>) included) {
                if (item instanceof Map) {
                    enrichIfAttachment((Map<String, Object>) item);
                }
            }
        }

        return responseBody;
    }

    /**
     * Enriches a single JSON:API resource object with a download URL if it is
     * an attachment with a valid storage key.
     *
     * @param resource the JSON:API resource object
     */
    @SuppressWarnings("unchecked")
    private void enrichIfAttachment(Map<String, Object> resource) {
        if (!ATTACHMENTS_TYPE.equals(resource.get("type"))) {
            return;
        }

        Object attributesObj = resource.get("attributes");
        if (!(attributesObj instanceof Map)) {
            return;
        }

        Map<String, Object> attributes = (Map<String, Object>) attributesObj;
        Object storageKey = attributes.get("storageKey");

        if (storageKey instanceof String key && !key.isBlank()) {
            try {
                String downloadUrl = s3StorageService.getPresignedDownloadUrl(
                        key, s3StorageService.getDefaultExpiry());
                attributes.put("downloadUrl", downloadUrl);
            } catch (Exception e) {
                logger.warn("Failed to generate presigned URL for storageKey '{}': {}",
                        storageKey, e.getMessage());
            }
        }
    }
}
