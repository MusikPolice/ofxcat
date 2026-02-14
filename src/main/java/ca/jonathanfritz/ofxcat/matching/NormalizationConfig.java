package ca.jonathanfritz.ofxcat.matching;

import java.util.Set;

/**
 * Configuration for TokenNormalizer behavior.
 * Use {@link #defaults()} for standard configuration or {@link #builder()} for customization.
 */
public class NormalizationConfig {

    private static final Set<String> DEFAULT_STOP_WORDS =
            Set.of("the", "and", "or", "of", "for", "at", "to", "from", "in", "on", "by", "with");

    private static final int DEFAULT_MIN_TOKEN_LENGTH = 2;

    private final Set<String> stopWords;
    private final int minTokenLength;

    private NormalizationConfig(Set<String> stopWords, int minTokenLength) {
        this.stopWords = stopWords;
        this.minTokenLength = minTokenLength;
    }

    /**
     * Returns the default configuration with standard stop words and minimum token length.
     */
    public static NormalizationConfig defaults() {
        return new NormalizationConfig(DEFAULT_STOP_WORDS, DEFAULT_MIN_TOKEN_LENGTH);
    }

    /**
     * Returns a builder for creating custom configurations.
     */
    public static Builder builder() {
        return new Builder();
    }

    public Set<String> getStopWords() {
        return stopWords;
    }

    public int getMinTokenLength() {
        return minTokenLength;
    }

    public static class Builder {
        private Set<String> stopWords = DEFAULT_STOP_WORDS;
        private int minTokenLength = DEFAULT_MIN_TOKEN_LENGTH;

        public Builder stopWords(Set<String> stopWords) {
            this.stopWords = stopWords;
            return this;
        }

        public Builder minTokenLength(int minTokenLength) {
            this.minTokenLength = minTokenLength;
            return this;
        }

        public NormalizationConfig build() {
            return new NormalizationConfig(stopWords, minTokenLength);
        }
    }
}
