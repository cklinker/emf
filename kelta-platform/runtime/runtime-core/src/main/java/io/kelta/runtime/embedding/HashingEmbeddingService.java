package io.kelta.runtime.embedding;

/**
 * Dependency-free default {@link EmbeddingService} using the feature-hashing ("hashing trick")
 * technique: tokens (words plus character trigrams) are hashed into a fixed-dimension vector with
 * signed accumulation, then L2-normalized.
 *
 * <p>It is deterministic, requires no external service or model, and captures lexical similarity —
 * texts that share words/character-grams land closer in cosine space. It is not a semantic model;
 * a deployment that wants true semantic embeddings registers its own {@link EmbeddingService} bean
 * (an external API or local model) and this default backs off.
 *
 * @since 1.0.0
 */
public class HashingEmbeddingService implements EmbeddingService {

    /** pgvector's default VECTOR dimension is 1536; the hashing default is smaller and cheaper. */
    public static final int DEFAULT_DIMENSIONS = 384;

    private static final int FNV_OFFSET = 0x811c9dc5;
    private static final int FNV_PRIME = 0x01000193;

    private final int dimensions;

    public HashingEmbeddingService() {
        this(DEFAULT_DIMENSIONS);
    }

    public HashingEmbeddingService(int dimensions) {
        if (dimensions < 1) {
            throw new IllegalArgumentException("dimensions must be >= 1, got " + dimensions);
        }
        this.dimensions = dimensions;
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public String providerId() {
        return "hashing-v1-d" + dimensions;
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[dimensions];
        if (text == null || text.isBlank()) {
            return vector;
        }
        String normalized = text.toLowerCase();
        for (String token : tokens(normalized)) {
            int hash = fnv1a(token);
            int bucket = Math.floorMod(hash, dimensions);
            // A second, independent bit of the hash decides the sign so collisions can cancel.
            float sign = (hash & 1) == 0 ? 1f : -1f;
            vector[bucket] += sign;
        }
        normalize(vector);
        return vector;
    }

    /** Word tokens plus character trigrams, so similar/misspelled words still share features. */
    private Iterable<String> tokens(String normalized) {
        java.util.List<String> tokens = new java.util.ArrayList<>();
        for (String word : normalized.split("[^a-z0-9]+")) {
            if (word.isEmpty()) {
                continue;
            }
            tokens.add("w:" + word);
            String padded = "#" + word + "#";
            for (int i = 0; i + 3 <= padded.length(); i++) {
                tokens.add("g:" + padded.substring(i, i + 3));
            }
        }
        return tokens;
    }

    private static void normalize(float[] vector) {
        double sumSquares = 0;
        for (float v : vector) {
            sumSquares += (double) v * v;
        }
        if (sumSquares == 0) {
            return;
        }
        float norm = (float) Math.sqrt(sumSquares);
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= norm;
        }
    }

    /** FNV-1a 32-bit — a small, stable, well-distributed string hash (not String.hashCode). */
    private static int fnv1a(String s) {
        int hash = FNV_OFFSET;
        for (int i = 0; i < s.length(); i++) {
            hash ^= s.charAt(i);
            hash *= FNV_PRIME;
        }
        return hash;
    }
}
