package io.kelta.worker.service;

import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Semaphore;

/**
 * On-the-fly image transformation: resize, crop, and format conversion.
 *
 * <p>Uses Thumbnailator (pure Java, no native dependencies) for image processing.
 * Includes protection against image decompression bombs and concurrent memory exhaustion.
 *
 * @since 1.0.0
 */
@Service
public class ImageTransformService {

    private static final Logger log = LoggerFactory.getLogger(ImageTransformService.class);

    private static final int MAX_DIMENSION = 4096;
    private static final int MAX_MEGAPIXELS = 20;
    private static final int DEFAULT_QUALITY = 85;
    private static final int MAX_CONCURRENT_TRANSFORMS = 4;

    private static final Map<String, String> FORMAT_TO_CONTENT_TYPE = Map.of(
            "jpeg", "image/jpeg",
            "jpg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp",
            "gif", "image/gif"
    );

    private final Semaphore transformSemaphore = new Semaphore(MAX_CONCURRENT_TRANSFORMS);

    /**
     * Transform parameters parsed from URL query string.
     */
    public record TransformParams(
            Integer width,
            Integer height,
            String fit,     // "cover" or "contain" (default: contain)
            String format,  // "jpeg", "png", "webp"
            Integer quality // 1-100 (default: 85)
    ) {
        public boolean hasTransform() {
            return width != null || height != null || format != null;
        }

        public int effectiveQuality() {
            return quality != null ? Math.max(1, Math.min(100, quality)) : DEFAULT_QUALITY;
        }

        public int clampedWidth() {
            return width != null ? Math.max(1, Math.min(width, MAX_DIMENSION)) : 0;
        }

        public int clampedHeight() {
            return height != null ? Math.max(1, Math.min(height, MAX_DIMENSION)) : 0;
        }
    }

    /**
     * Result of an image transformation.
     */
    public record TransformResult(byte[] data, String contentType) {}

    /**
     * Transforms an image according to the given parameters.
     *
     * @param source      the source image stream
     * @param contentType the source content type (e.g., "image/png")
     * @param params      the transform parameters
     * @return the transformed image, or null if no transform needed
     * @throws IOException if image processing fails
     */
    public TransformResult transform(InputStream source, String contentType, TransformParams params)
            throws IOException {
        if (params == null || !params.hasTransform()) {
            return null; // Signal passthrough
        }

        if (!isImageType(contentType)) {
            return null; // Non-image passthrough
        }

        // Acquire semaphore to limit concurrent transforms
        try {
            transformSemaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Image transform interrupted", e);
        }

        try {
            return doTransform(source, params);
        } finally {
            transformSemaphore.release();
        }
    }

    private TransformResult doTransform(InputStream source, TransformParams params) throws IOException {
        // Check dimensions before full decompression (image bomb protection)
        BufferedImage image = readWithBombProtection(source);

        int w = params.clampedWidth();
        int h = params.clampedHeight();
        String outputFormat = params.format() != null ? params.format() : guessFormat(image);
        boolean isCover = "cover".equalsIgnoreCase(params.fit());

        var builder = Thumbnails.of(image);

        if (w > 0 && h > 0) {
            if (isCover) {
                // Cover: resize to fill, then crop center
                builder.size(w, h).crop(Positions.CENTER);
            } else {
                // Contain: fit within bounds
                builder.size(w, h).keepAspectRatio(true);
            }
        } else if (w > 0) {
            builder.width(w).keepAspectRatio(true);
        } else if (h > 0) {
            builder.height(h).keepAspectRatio(true);
        } else {
            // Format conversion only
            builder.scale(1.0);
        }

        builder.outputQuality(params.effectiveQuality() / 100.0);
        builder.outputFormat(normalizeFormat(outputFormat));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        builder.toOutputStream(out);

        String resultContentType = FORMAT_TO_CONTENT_TYPE.getOrDefault(
                normalizeFormat(outputFormat), "image/png");

        log.debug("Transformed image: {}x{} fit={} format={} quality={} → {} bytes",
                w, h, params.fit(), outputFormat, params.effectiveQuality(), out.size());

        return new TransformResult(out.toByteArray(), resultContentType);
    }

    /**
     * Reads image with decompression bomb protection.
     * Checks pixel dimensions via ImageReader metadata before full decode.
     */
    private BufferedImage readWithBombProtection(InputStream source) throws IOException {
        ImageInputStream iis = ImageIO.createImageInputStream(source);
        if (iis == null) {
            throw new IOException("Cannot read image: unsupported format");
        }

        Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
        if (!readers.hasNext()) {
            iis.close();
            throw new IOException("Cannot read image: no suitable reader found");
        }

        ImageReader reader = readers.next();
        try {
            reader.setInput(iis);
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            long megapixels = (long) width * height / 1_000_000;

            if (megapixels > MAX_MEGAPIXELS) {
                throw new IOException("Image too large: " + width + "x" + height +
                        " (" + megapixels + "MP exceeds " + MAX_MEGAPIXELS + "MP limit)");
            }

            return reader.read(0);
        } finally {
            reader.dispose();
            iis.close();
        }
    }

    private String normalizeFormat(String format) {
        if (format == null) return "png";
        return switch (format.toLowerCase()) {
            case "jpg" -> "jpeg";
            case "jpeg", "png", "gif", "webp" -> format.toLowerCase();
            default -> "png";
        };
    }

    private String guessFormat(BufferedImage image) {
        // If image has alpha channel, default to PNG; otherwise JPEG
        return image.getColorModel().hasAlpha() ? "png" : "jpeg";
    }

    public static boolean isImageType(String contentType) {
        return contentType != null && contentType.startsWith("image/");
    }
}
