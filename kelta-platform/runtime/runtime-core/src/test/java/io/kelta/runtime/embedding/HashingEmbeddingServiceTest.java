package io.kelta.runtime.embedding;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("HashingEmbeddingService")
class HashingEmbeddingServiceTest {

    private final HashingEmbeddingService service = new HashingEmbeddingService(256);

    private static double cosine(float[] a, float[] b) {
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private static double magnitude(float[] v) {
        double sum = 0;
        for (float f : v) {
            sum += (double) f * f;
        }
        return Math.sqrt(sum);
    }

    @Test
    @DisplayName("produces a normalized vector of the configured dimension")
    void normalizedVector() {
        float[] v = service.embed("the quick brown fox");
        assertThat(v).hasSize(256);
        assertThat(magnitude(v)).isCloseTo(1.0, within(1e-5));
        assertThat(service.dimensions()).isEqualTo(256);
    }

    @Test
    @DisplayName("is deterministic for the same input")
    void deterministic() {
        assertThat(service.embed("hello world")).containsExactly(service.embed("hello world"));
    }

    @Test
    @DisplayName("blank input yields a zero vector")
    void blankInput() {
        assertThat(magnitude(service.embed("   "))).isZero();
        assertThat(magnitude(service.embed(null))).isZero();
    }

    @Test
    @DisplayName("lexically similar texts are closer in cosine space than dissimilar ones")
    void capturesSimilarity() {
        float[] base = service.embed("the quick brown fox jumps");
        float[] similar = service.embed("the quick brown dog jumps");
        float[] dissimilar = service.embed("quarterly revenue projections for finance");

        assertThat(cosine(base, similar)).isGreaterThan(cosine(base, dissimilar));
    }

    @Test
    @DisplayName("renders a pgvector literal")
    void vectorLiteral() {
        String literal = EmbeddingService.toVectorLiteral(new float[]{0.5f, -0.25f, 0f});
        assertThat(literal).isEqualTo("[0.5,-0.25,0.0]");
    }

    @Test
    @DisplayName("embedBatch embeds each text")
    void batch() {
        List<float[]> out = service.embedBatch(List.of("a", "b"));
        assertThat(out).hasSize(2);
        assertThat(out.get(0)).containsExactly(service.embed("a"));
    }

    @Test
    @DisplayName("providerId reflects the dimension")
    void providerId() {
        assertThat(service.providerId()).isEqualTo("hashing-v1-d256");
    }

    @Test
    @DisplayName("rejects a non-positive dimension")
    void rejectsBadDimension() {
        try {
            new HashingEmbeddingService(0);
            assertThat(false).as("expected IllegalArgumentException").isTrue();
        } catch (IllegalArgumentException expected) {
            assertThat(expected).hasMessageContaining("dimensions");
        }
    }
}
