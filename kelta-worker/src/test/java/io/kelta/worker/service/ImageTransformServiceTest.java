package io.kelta.worker.service;

import io.kelta.worker.service.ImageTransformService.TransformParams;
import io.kelta.worker.service.ImageTransformService.TransformResult;
import org.junit.jupiter.api.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ImageTransformService Tests")
class ImageTransformServiceTest {

    private ImageTransformService service;

    @BeforeEach
    void setUp() {
        service = new ImageTransformService();
    }

    private InputStream createTestImage(int width, int height, String format) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        // Fill with solid color so it's a valid image
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                img.setRGB(x, y, 0xFF0000); // Red
            }
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, format, baos);
        return new ByteArrayInputStream(baos.toByteArray());
    }

    @Nested
    @DisplayName("Resize Operations")
    class Resize {
        @Test
        void shouldResizeByWidth() throws IOException {
            InputStream source = createTestImage(800, 600, "png");
            var params = new TransformParams(200, null, null, "png", null);

            TransformResult result = service.transform(source, "image/png", params);

            assertThat(result).isNotNull();
            assertThat(result.contentType()).isEqualTo("image/png");
            assertThat(result.data()).isNotEmpty();

            // Verify dimensions
            BufferedImage output = ImageIO.read(new ByteArrayInputStream(result.data()));
            assertThat(output.getWidth()).isEqualTo(200);
            assertThat(output.getHeight()).isEqualTo(150); // Proportional
        }

        @Test
        void shouldResizeByHeight() throws IOException {
            InputStream source = createTestImage(800, 600, "png");
            var params = new TransformParams(null, 150, null, "png", null);

            TransformResult result = service.transform(source, "image/png", params);

            assertThat(result).isNotNull();
            BufferedImage output = ImageIO.read(new ByteArrayInputStream(result.data()));
            assertThat(output.getHeight()).isEqualTo(150);
            assertThat(output.getWidth()).isEqualTo(200); // Proportional
        }

        @Test
        void shouldContainFitWithinBounds() throws IOException {
            InputStream source = createTestImage(800, 400, "png");
            var params = new TransformParams(200, 200, "contain", "png", null);

            TransformResult result = service.transform(source, "image/png", params);

            assertThat(result).isNotNull();
            BufferedImage output = ImageIO.read(new ByteArrayInputStream(result.data()));
            // 800x400 → fit within 200x200 → 200x100
            assertThat(output.getWidth()).isEqualTo(200);
            assertThat(output.getHeight()).isEqualTo(100);
        }

        @Test
        void shouldCoverFitCropToExact() throws IOException {
            InputStream source = createTestImage(800, 400, "png");
            var params = new TransformParams(200, 200, "cover", "png", null);

            TransformResult result = service.transform(source, "image/png", params);

            assertThat(result).isNotNull();
            BufferedImage output = ImageIO.read(new ByteArrayInputStream(result.data()));
            assertThat(output.getWidth()).isEqualTo(200);
            assertThat(output.getHeight()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("Format Conversion")
    class FormatConversion {
        @Test
        void shouldConvertPngToJpeg() throws IOException {
            InputStream source = createTestImage(100, 100, "png");
            var params = new TransformParams(null, null, null, "jpeg", 80);

            TransformResult result = service.transform(source, "image/png", params);

            assertThat(result).isNotNull();
            assertThat(result.contentType()).isEqualTo("image/jpeg");
        }
    }

    @Nested
    @DisplayName("Passthrough")
    class Passthrough {
        @Test
        void shouldReturnNullForNoParams() throws IOException {
            InputStream source = createTestImage(100, 100, "png");
            var params = new TransformParams(null, null, null, null, null);

            TransformResult result = service.transform(source, "image/png", params);
            assertThat(result).isNull();
        }

        @Test
        void shouldReturnNullForNonImage() throws IOException {
            InputStream source = new ByteArrayInputStream("not an image".getBytes());
            var params = new TransformParams(200, null, null, null, null);

            TransformResult result = service.transform(source, "application/pdf", params);
            assertThat(result).isNull();
        }

        @Test
        void shouldReturnNullForNullParams() throws IOException {
            InputStream source = createTestImage(100, 100, "png");

            TransformResult result = service.transform(source, "image/png", null);
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("Dimension Clamping")
    class Clamping {
        @Test
        void shouldClampWidthToMax() {
            var params = new TransformParams(10000, null, null, null, null);
            assertThat(params.clampedWidth()).isEqualTo(4096);
        }

        @Test
        void shouldClampHeightToMax() {
            var params = new TransformParams(null, 10000, null, null, null);
            assertThat(params.clampedHeight()).isEqualTo(4096);
        }

        @Test
        void shouldClampQuality() {
            assertThat(new TransformParams(null, null, null, null, 0).effectiveQuality()).isEqualTo(1);
            assertThat(new TransformParams(null, null, null, null, 200).effectiveQuality()).isEqualTo(100);
            assertThat(new TransformParams(null, null, null, null, 80).effectiveQuality()).isEqualTo(80);
        }
    }

    @Nested
    @DisplayName("Image Type Detection")
    class TypeDetection {
        @Test
        void shouldRecognizeImageTypes() {
            assertThat(ImageTransformService.isImageType("image/png")).isTrue();
            assertThat(ImageTransformService.isImageType("image/jpeg")).isTrue();
            assertThat(ImageTransformService.isImageType("image/webp")).isTrue();
        }

        @Test
        void shouldRejectNonImageTypes() {
            assertThat(ImageTransformService.isImageType("application/pdf")).isFalse();
            assertThat(ImageTransformService.isImageType("text/html")).isFalse();
            assertThat(ImageTransformService.isImageType(null)).isFalse();
        }
    }
}
