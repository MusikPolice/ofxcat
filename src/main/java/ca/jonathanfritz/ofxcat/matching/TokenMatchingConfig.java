package ca.jonathanfritz.ofxcat.matching;

/**
 * Configuration for token-based matching behavior.
 * Use {@link #defaults()} for standard configuration or {@link #builder()} for customization.
 */
public class TokenMatchingConfig {

    private static final double DEFAULT_OVERLAP_THRESHOLD = 0.8;

    private final double overlapThreshold;

    private TokenMatchingConfig(double overlapThreshold) {
        this.overlapThreshold = overlapThreshold;
    }

    /**
     * Returns the default configuration with 80% overlap threshold.
     */
    public static TokenMatchingConfig defaults() {
        return new TokenMatchingConfig(DEFAULT_OVERLAP_THRESHOLD);
    }

    /**
     * Returns a builder for creating custom configurations.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the minimum overlap ratio required for a match.
     * A value of 1.0 means all tokens must match (strict).
     * A value of 0.5 means half of tokens must match (loose).
     */
    public double getOverlapThreshold() {
        return overlapThreshold;
    }

    public static class Builder {
        private double overlapThreshold = DEFAULT_OVERLAP_THRESHOLD;

        public Builder overlapThreshold(double overlapThreshold) {
            this.overlapThreshold = overlapThreshold;
            return this;
        }

        public TokenMatchingConfig build() {
            return new TokenMatchingConfig(overlapThreshold);
        }
    }
}
