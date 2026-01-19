package ca.jonathanfritz.ofxcat.matching;

import java.util.Set;

/**
 * Converts transaction descriptions into normalized token sets for matching.
 */
public class TokenNormalizer {

    private final NormalizationConfig config;

    /**
     * Creates a TokenNormalizer with default configuration.
     */
    public TokenNormalizer() {
        this(NormalizationConfig.defaults());
    }

    /**
     * Creates a TokenNormalizer with custom configuration.
     *
     * @param config the normalization configuration to use
     */
    public TokenNormalizer(NormalizationConfig config) {
        this.config = config;
    }

    /**
     * Normalizes a transaction description into a set of tokens.
     *
     * @param description the transaction description (already cleaned by TransactionCleaner)
     * @return a set of normalized tokens
     */
    public Set<String> normalize(String description) {
        if (description == null || description.isBlank()) {
            return Set.of();
        }

        // Decode XML entities first (OFX files encode special characters)
        String decoded = decodeXmlEntities(description);

        // Lowercase
        String lowercased = decoded.toLowerCase();

        // Step 1: Merge single-letter initials around ampersands
        // e.g., "A & W" -> "aw", "H&M" -> "hm", "B & J Photo" -> "bj photo"
        // This preserves context for business names based on owner initials
        String initialsmerged = lowercased.replaceAll("([a-z])\\s*&\\s*([a-z])", "$1$2");

        // Step 2: Remove remaining "joiner" punctuation that should merge adjacent words
        // e.g., MCDONALD'S -> mcdonalds, WAL-MART -> walmart
        String merged = initialsmerged.replaceAll("[-'&]", "");

        // Step 3: Split on remaining non-alphanumeric characters (delimiters)
        // e.g., Amazon.ca*T23YP3F33 -> [amazon, ca, t23yp3f33]
        String[] parts = merged.split("[^a-z0-9]+");

        // Apply filters to each token
        Set<String> tokens = new java.util.HashSet<>();
        for (String part : parts) {
            if (isValidToken(part)) {
                tokens.add(part);
            }
        }

        return tokens;
    }

    private boolean isValidToken(String token) {
        return !token.isEmpty()
                && token.length() >= config.getMinTokenLength()
                && !isNumeric(token)
                && !config.getStopWords().contains(token);
    }

    private boolean isNumeric(String str) {
        return str.matches("\\d+");
    }

    private String decodeXmlEntities(String input) {
        return input
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'");
    }
}
