package ca.jonathanfritz.ofxcat.matching;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Configuration for keyword-based automatic categorization rules.
 * Rules are processed in order, and the first matching rule wins.
 */
public class KeywordRulesConfig {

    private int version = 1;
    private Settings settings = new Settings();
    private List<KeywordRule> rules = new ArrayList<>();

    // Default constructor for Jackson deserialization
    public KeywordRulesConfig() {}

    public KeywordRulesConfig(List<KeywordRule> rules) {
        this.rules = rules != null ? rules : new ArrayList<>();
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public Settings getSettings() {
        return settings;
    }

    public void setSettings(Settings settings) {
        this.settings = settings != null ? settings : new Settings();
    }

    public List<KeywordRule> getRules() {
        return rules;
    }

    public void setRules(List<KeywordRule> rules) {
        this.rules = rules != null ? rules : new ArrayList<>();
    }

    /**
     * Finds the first matching category for the given normalized tokens.
     * Rules are processed in order, and the first match wins.
     *
     * @param tokens the normalized tokens from a transaction description
     * @return the category name of the first matching rule, or empty if no match
     */
    public Optional<String> findMatchingCategory(Set<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return Optional.empty();
        }

        return rules.stream().filter(rule -> rule.matches(tokens)).findFirst().map(KeywordRule::getCategory);
    }

    /**
     * Returns true if auto-categorization is enabled.
     * When enabled, keyword matches are applied automatically.
     * When disabled, keyword matches are treated as suggestions only.
     */
    public boolean isAutoCategorizeEnabled() {
        return settings.isAutoCategorize();
    }

    /**
     * Returns all rules whose category matches the given name (case-insensitive).
     *
     * @param categoryName the category name to search for
     * @return matching rules, or an empty list if none match or input is null/blank
     */
    public List<KeywordRule> findRulesByCategory(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            return List.of();
        }

        return rules.stream()
                .filter(rule -> categoryName.equalsIgnoreCase(rule.getCategory()))
                .toList();
    }

    /**
     * Returns an empty configuration with no rules.
     */
    public static KeywordRulesConfig empty() {
        return new KeywordRulesConfig(new ArrayList<>());
    }

    /**
     * Global settings for keyword rules behavior.
     */
    public static class Settings {
        private boolean autoCategorize = true;

        public boolean isAutoCategorize() {
            return autoCategorize;
        }

        public void setAutoCategorize(boolean autoCategorize) {
            this.autoCategorize = autoCategorize;
        }
    }
}
