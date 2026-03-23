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
    private VendorGroupingSettings vendorGrouping;
    private SubscriptionDetectionSettings subscriptionDetection;

    public AppConfig() {
        // Default values
        this.keywordRulesPath = "keyword-rules.yaml";
        this.tokenMatching = new TokenMatchingSettings();
        this.vendorGrouping = new VendorGroupingSettings();
        this.subscriptionDetection = new SubscriptionDetectionSettings();
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

    public VendorGroupingSettings getVendorGrouping() {
        return vendorGrouping;
    }

    public void setVendorGrouping(VendorGroupingSettings vendorGrouping) {
        this.vendorGrouping = vendorGrouping;
    }

    public SubscriptionDetectionSettings getSubscriptionDetection() {
        return subscriptionDetection;
    }

    public void setSubscriptionDetection(SubscriptionDetectionSettings subscriptionDetection) {
        this.subscriptionDetection = subscriptionDetection;
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

    /**
     * Settings for vendor grouping, which clusters transactions by token overlap.
     */
    public static class VendorGroupingSettings {
        private double overlapThreshold;

        public VendorGroupingSettings() {
            // Default: 60% overlap required to assign two transactions to the same vendor
            this.overlapThreshold = 0.6;
        }

        public double getOverlapThreshold() {
            return overlapThreshold;
        }

        public void setOverlapThreshold(double overlapThreshold) {
            this.overlapThreshold = overlapThreshold;
        }
    }

    /**
     * Settings for subscription detection.
     */
    public static class SubscriptionDetectionSettings {
        private int minOccurrences;
        private double amountTolerance;
        private int intervalToleranceDays;

        public SubscriptionDetectionSettings() {
            // Default: at least 3 transactions needed to establish a recurring pattern
            this.minOccurrences = 3;
            // Default: amount may vary by up to 5% and still be considered the same subscription
            this.amountTolerance = 0.05;
            // Default: interval may vary by up to 3 days from a canonical billing period
            this.intervalToleranceDays = 3;
        }

        public int getMinOccurrences() {
            return minOccurrences;
        }

        public void setMinOccurrences(int minOccurrences) {
            this.minOccurrences = minOccurrences;
        }

        public double getAmountTolerance() {
            return amountTolerance;
        }

        public void setAmountTolerance(double amountTolerance) {
            this.amountTolerance = amountTolerance;
        }

        public int getIntervalToleranceDays() {
            return intervalToleranceDays;
        }

        public void setIntervalToleranceDays(int intervalToleranceDays) {
            this.intervalToleranceDays = intervalToleranceDays;
        }
    }
}
