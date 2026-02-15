package ca.jonathanfritz.ofxcat.matching;

import java.util.List;
import java.util.Set;

/**
 * Represents a keyword matching rule for automatic transaction categorization.
 * A rule matches when its keywords are found in the normalized tokens of a transaction description.
 */
public class KeywordRule {

    private List<String> keywords;
    private String category;
    private boolean matchAll;

    // Default constructor for Jackson deserialization
    public KeywordRule() {}

    public KeywordRule(List<String> keywords, String category, boolean matchAll) {
        this.keywords = keywords;
        this.category = category;
        this.matchAll = matchAll;
    }

    public KeywordRule(List<String> keywords, String category) {
        this(keywords, category, false);
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public void setKeywords(List<String> keywords) {
        this.keywords = keywords;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public boolean isMatchAll() {
        return matchAll;
    }

    public void setMatchAll(boolean matchAll) {
        this.matchAll = matchAll;
    }

    /**
     * Tests whether this rule matches the given set of normalized tokens.
     *
     * @param tokens the normalized tokens from a transaction description
     * @return true if the rule matches, false otherwise
     */
    public boolean matches(Set<String> tokens) {
        if (keywords == null || keywords.isEmpty() || tokens == null || tokens.isEmpty()) {
            return false;
        }

        // Normalize keywords to lowercase for comparison
        List<String> normalizedKeywords =
                keywords.stream().map(String::toLowerCase).toList();

        if (matchAll) {
            // All keywords must be present in tokens
            return tokens.containsAll(normalizedKeywords);
        } else {
            // Any keyword present triggers match
            return normalizedKeywords.stream().anyMatch(tokens::contains);
        }
    }

    @Override
    public String toString() {
        return "KeywordRule{" + "keywords="
                + keywords + ", category='"
                + category + '\'' + ", matchAll="
                + matchAll + '}';
    }
}
