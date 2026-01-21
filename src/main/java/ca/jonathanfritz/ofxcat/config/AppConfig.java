package ca.jonathanfritz.ofxcat.config;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Application configuration loaded from ~/.ofxcat/config.yaml.
 * Contains all user-configurable settings.
 */
public class AppConfig {

    private String keywordRulesPath;
    private TokenMatchingSettings tokenMatching;

    public AppConfig() {
        // Default values
        this.keywordRulesPath = "keyword-rules.yaml";
        this.tokenMatching = new TokenMatchingSettings();
    }

    /**
     * Creates a default configuration with sensible defaults.
     */
    public static AppConfig defaults() {
        return new AppConfig();
    }

    public String getKeywordRulesPath() {
        return keywordRulesPath;
    }

    public void setKeywordRulesPath(String keywordRulesPath) {
        this.keywordRulesPath = keywordRulesPath;
    }

    public TokenMatchingSettings getTokenMatching() {
        return tokenMatching;
    }

    public void setTokenMatching(TokenMatchingSettings tokenMatching) {
        this.tokenMatching = tokenMatching;
    }

    /**
     * Resolves the keyword rules path relative to the config directory.
     * If the path is absolute, returns it as-is.
     * If the path is relative, resolves it against the config directory.
     *
     * @param configDirectory the directory containing config.yaml
     * @return the resolved absolute path to the keyword rules file
     */
    @JsonIgnore
    public Path resolveKeywordRulesPath(Path configDirectory) {
        Path rulesPath = Paths.get(keywordRulesPath);
        if (rulesPath.isAbsolute()) {
            return rulesPath;
        }
        return configDirectory.resolve(rulesPath);
    }

    /**
     * Settings for token-based transaction matching.
     */
    public static class TokenMatchingSettings {
        private double overlapThreshold;

        public TokenMatchingSettings() {
            // Default: 60% overlap required for a match
            this.overlapThreshold = 0.6;
        }

        public double getOverlapThreshold() {
            return overlapThreshold;
        }

        public void setOverlapThreshold(double overlapThreshold) {
            this.overlapThreshold = overlapThreshold;
        }
    }
}
