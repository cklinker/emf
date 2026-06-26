package io.kelta.runtime.embedding;

import java.util.List;

/**
 * Turns text into a dense vector embedding for semantic / similarity search over the pgvector
 * {@code VECTOR} field type.
 *
 * <p>This is an SPI: the runtime ships a dependency-free default ({@link HashingEmbeddingService})
 * so semantic search works out of the box, and a deployment can supply a higher-quality provider
 * (e.g. an external embedding API) by registering its own {@code EmbeddingService} bean — the
 * default backs off via {@code @ConditionalOnMissingBean}.
 *
 * <p>All embeddings from one provider share a fixed {@link #dimensions()} and should be
 * L2-normalized so cosine distance ({@code <=>}) and inner product rank consistently.
 *
 * @since 1.0.0
 */
public interface EmbeddingService {

    /**
     * Embeds a single text into a vector of length {@link #dimensions()}. Blank input yields a
     * zero vector.
     */
    float[] embed(String text);

    /** The fixed dimensionality every embedding from this provider has. */
    int dimensions();

    /** A short stable identifier for the provider (for diagnostics / stored alongside vectors). */
    String providerId();

    /** Embeds several texts; default maps {@link #embed(String)} over the list. */
    default List<float[]> embedBatch(List<String> texts) {
        return texts.stream().map(this::embed).toList();
    }

    /**
     * Renders an embedding as a pgvector literal ({@code [0.1,0.2,...]}) for binding into SQL such
     * as {@code ORDER BY embedding <=> CAST(? AS vector)}.
     */
    static String toVectorLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder(embedding.length * 8 + 2);
        sb.append('[');
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(embedding[i]);
        }
        return sb.append(']').toString();
    }
}
