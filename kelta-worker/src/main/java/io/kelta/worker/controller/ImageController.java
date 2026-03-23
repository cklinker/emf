package io.kelta.worker.controller;

import io.kelta.worker.service.ImageTransformService;
import io.kelta.worker.service.ImageTransformService.TransformParams;
import io.kelta.worker.service.ImageTransformService.TransformResult;
import io.kelta.worker.service.S3StorageService;
import io.kelta.worker.service.S3StorageService.StorageObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Serves images with on-the-fly transformations (resize, crop, format conversion).
 *
 * <p>Query parameters:
 * <ul>
 *   <li>{@code w} — target width in pixels</li>
 *   <li>{@code h} — target height in pixels</li>
 *   <li>{@code fit} — "cover" (crop to exact) or "contain" (fit within, default)</li>
 *   <li>{@code format} — output format: "jpeg", "png", "webp"</li>
 *   <li>{@code quality} — JPEG quality 1-100 (default 85)</li>
 * </ul>
 *
 * <p>Non-image files and requests without transform params are served unchanged.
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/images")
@ConditionalOnBean(S3StorageService.class)
public class ImageController {

    private static final Logger log = LoggerFactory.getLogger(ImageController.class);

    private final S3StorageService storageService;
    private final ImageTransformService transformService;

    public ImageController(S3StorageService storageService, ImageTransformService transformService) {
        this.storageService = storageService;
        this.transformService = transformService;
    }

    @GetMapping("/**")
    public void serveImage(HttpServletRequest request, HttpServletResponse response,
                           @RequestHeader(value = "X-Cerbos-Scope", required = false) String tenantId,
                           @RequestHeader(value = "X-User-Email", required = false) String userEmail,
                           @RequestParam(value = "w", required = false) Integer width,
                           @RequestParam(value = "h", required = false) Integer height,
                           @RequestParam(value = "fit", required = false) String fit,
                           @RequestParam(value = "format", required = false) String format,
                           @RequestParam(value = "quality", required = false) Integer quality) throws IOException {

        String storageKey = request.getRequestURI().substring("/api/images/".length());

        // Auth check
        if (userEmail == null || userEmail.isBlank() || tenantId == null || tenantId.isBlank()) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return;
        }

        // Path traversal prevention (reuse FileController logic)
        if (FileController.containsPathTraversal(storageKey)) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return;
        }

        // Tenant scoping
        String keyTenant = storageKey.contains("/") ? storageKey.substring(0, storageKey.indexOf('/')) : storageKey;
        if (!tenantId.equals(keyTenant)) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            return;
        }

        TransformParams params = new TransformParams(width, height, fit, format, quality);

        try (StorageObject obj = storageService.streamObject(storageKey)) {
            if (!params.hasTransform() || !ImageTransformService.isImageType(obj.contentType())) {
                // Passthrough: serve original
                serveOriginal(obj, response);
                return;
            }

            // Transform
            TransformResult result = transformService.transform(obj.content(), obj.contentType(), params);
            if (result == null) {
                // Fallback passthrough
                serveOriginal(obj, response);
                return;
            }

            response.setContentType(result.contentType());
            response.setContentLength(result.data().length);
            response.setHeader("Content-Disposition", "inline; filename=\"" +
                    FileController.sanitizeFileName(obj.fileName()) + "\"");
            response.setHeader("Cache-Control", "private, max-age=86400");

            try (OutputStream out = response.getOutputStream()) {
                out.write(result.data());
            }

        } catch (NoSuchKeyException e) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("too large")) {
                log.warn("Image bomb rejected: storageKey={} error={}", storageKey, e.getMessage());
                response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
            } else {
                log.error("Failed to serve image: {}", storageKey, e);
                response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
            }
        }
    }

    private void serveOriginal(StorageObject obj, HttpServletResponse response) throws IOException {
        response.setContentType(obj.contentType());
        if (obj.contentLength() > 0) {
            response.setContentLengthLong(obj.contentLength());
        }
        response.setHeader("Content-Disposition", "inline; filename=\"" +
                FileController.sanitizeFileName(obj.fileName()) + "\"");
        response.setHeader("Cache-Control", "private, max-age=86400");

        try (OutputStream out = response.getOutputStream()) {
            obj.content().transferTo(out);
        }
    }
}
