package ca.jonathanfritz.ofxcat.matching;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class KeywordRulesConfigTest {

    @Test
    void firstMatchWins() {
        // Setup: Two rules that could both match "pizza"
        KeywordRulesConfig config = new KeywordRulesConfig(List.of(
                new KeywordRule(List.of("pizza", "hut"), "FAST_FOOD", true),
                new KeywordRule(List.of("pizza"), "RESTAURANTS")));

        // Execute: Match with just "pizza"
        Optional<String> result = config.findMatchingCategory(Set.of("pizza", "downtown"));

        // Verify: First rule doesn't match (needs both "pizza" and "hut")
        // Second rule matches, so returns RESTAURANTS
        assertTrue(result.isPresent());
        assertEquals("RESTAURANTS", result.get());

        // Execute: Match with "pizza" and "hut"
        result = config.findMatchingCategory(Set.of("pizza", "hut"));

        // Verify: First rule matches, returns FAST_FOOD
        assertTrue(result.isPresent());
        assertEquals("FAST_FOOD", result.get());
    }

    @Test
    void noMatchReturnsEmpty() {
        // Setup: Rules that won't match our tokens
        KeywordRulesConfig config = new KeywordRulesConfig(List.of(
                new KeywordRule(List.of("starbucks"), "RESTAURANTS"),
                new KeywordRule(List.of("walmart"), "GROCERIES")));

        // Execute: Search with non-matching tokens
        Optional<String> result = config.findMatchingCategory(Set.of("unknown", "merchant"));

        // Verify: No match found
        assertFalse(result.isPresent());
    }

    @Test
    void starbucksMatchesRestaurants() {
        // Setup: Config with Starbucks rule
        KeywordRulesConfig config =
                new KeywordRulesConfig(List.of(new KeywordRule(List.of("starbucks"), "RESTAURANTS")));

        // Execute: Match normalized Starbucks tokens
        Optional<String> result = config.findMatchingCategory(Set.of("starbucks"));

        // Verify: Matches RESTAURANTS
        assertTrue(result.isPresent());
        assertEquals("RESTAURANTS", result.get());
    }

    @Test
    void walmartMatchesGroceries() {
        // Setup: Config with Walmart rule
        KeywordRulesConfig config = new KeywordRulesConfig(List.of(new KeywordRule(List.of("walmart"), "GROCERIES")));

        // Execute: Match normalized Walmart tokens
        Optional<String> result = config.findMatchingCategory(Set.of("walmart"));

        // Verify: Matches GROCERIES
        assertTrue(result.isPresent());
        assertEquals("GROCERIES", result.get());
    }

    @Test
    void timHortonsRequiresBothKeywords() {
        // Setup: Config with Tim Hortons rule requiring both keywords
        KeywordRulesConfig config =
                new KeywordRulesConfig(List.of(new KeywordRule(List.of("tim", "hortons"), "RESTAURANTS", true)));

        // Execute: Match with both keywords
        Optional<String> result = config.findMatchingCategory(Set.of("tim", "hortons", "coffee"));
        assertTrue(result.isPresent());
        assertEquals("RESTAURANTS", result.get());

        // Execute: Match with only one keyword
        result = config.findMatchingCategory(Set.of("tim", "downtown"));
        assertFalse(result.isPresent());

        result = config.findMatchingCategory(Set.of("hortons", "coffee"));
        assertFalse(result.isPresent());
    }

    @Test
    void emptyConfigReturnsEmpty() {
        // Setup: Empty configuration
        KeywordRulesConfig config = KeywordRulesConfig.empty();

        // Execute: Try to match
        Optional<String> result = config.findMatchingCategory(Set.of("starbucks"));

        // Verify: No match (no rules)
        assertFalse(result.isPresent());
    }

    @Test
    void emptyTokensReturnsEmpty() {
        // Setup: Valid config
        KeywordRulesConfig config =
                new KeywordRulesConfig(List.of(new KeywordRule(List.of("starbucks"), "RESTAURANTS")));

        // Execute: Search with empty tokens
        Optional<String> result = config.findMatchingCategory(Set.of());

        // Verify: No match
        assertFalse(result.isPresent());
    }

    @Test
    void nullTokensReturnsEmpty() {
        // Setup: Valid config
        KeywordRulesConfig config =
                new KeywordRulesConfig(List.of(new KeywordRule(List.of("starbucks"), "RESTAURANTS")));

        // Execute: Search with null tokens
        Optional<String> result = config.findMatchingCategory(null);

        // Verify: No match
        assertFalse(result.isPresent());
    }

    @Test
    void autoCategorizeEnabledByDefault() {
        // Setup: Default configuration
        KeywordRulesConfig config = new KeywordRulesConfig();

        // Verify: Auto-categorize is enabled by default
        assertTrue(config.isAutoCategorizeEnabled());
    }

    @Test
    void autoCategorizeCanBeDisabled() {
        // Setup: Configuration with auto-categorize disabled
        KeywordRulesConfig config = new KeywordRulesConfig();
        KeywordRulesConfig.Settings settings = new KeywordRulesConfig.Settings();
        settings.setAutoCategorize(false);
        config.setSettings(settings);

        // Verify: Auto-categorize is disabled
        assertFalse(config.isAutoCategorizeEnabled());
    }

    @Test
    void findRulesByCategoryReturnsCaseInsensitiveMatches() {
        // Setup: Rules with mixed case category names
        KeywordRule rule1 = new KeywordRule(List.of("starbucks"), "RESTAURANTS");
        KeywordRule rule2 = new KeywordRule(List.of("mcdonalds"), "Restaurants");
        KeywordRule rule3 = new KeywordRule(List.of("walmart"), "GROCERIES");
        KeywordRulesConfig config = new KeywordRulesConfig(List.of(rule1, rule2, rule3));

        // Execute: Search case-insensitively
        List<KeywordRule> result = config.findRulesByCategory("restaurants");

        // Verify: Both restaurant rules returned
        assertEquals(2, result.size());
        assertTrue(result.contains(rule1));
        assertTrue(result.contains(rule2));
    }

    @Test
    void findRulesByCategoryReturnsEmptyListWhenNoMatch() {
        // Setup: Rules that don't match
        KeywordRulesConfig config =
                new KeywordRulesConfig(List.of(new KeywordRule(List.of("starbucks"), "RESTAURANTS")));

        // Execute: Search for non-existent category
        List<KeywordRule> result = config.findRulesByCategory("NONEXISTENT");

        // Verify: Empty list returned
        assertTrue(result.isEmpty());
    }

    @Test
    void findRulesByCategoryReturnsEmptyListForNullInput() {
        KeywordRulesConfig config =
                new KeywordRulesConfig(List.of(new KeywordRule(List.of("starbucks"), "RESTAURANTS")));

        assertTrue(config.findRulesByCategory(null).isEmpty());
        assertTrue(config.findRulesByCategory("").isEmpty());
        assertTrue(config.findRulesByCategory("   ").isEmpty());
    }

    @Test
    void multipleRulesForSameCategory() {
        // Setup: Multiple rules mapping to same category
        KeywordRulesConfig config = new KeywordRulesConfig(List.of(
                new KeywordRule(List.of("starbucks"), "RESTAURANTS"),
                new KeywordRule(List.of("mcdonalds"), "RESTAURANTS"),
                new KeywordRule(List.of("tim", "hortons"), "RESTAURANTS", true)));

        // Verify: Each rule matches independently
        assertEquals(
                "RESTAURANTS", config.findMatchingCategory(Set.of("starbucks")).orElse(null));
        assertEquals(
                "RESTAURANTS", config.findMatchingCategory(Set.of("mcdonalds")).orElse(null));
        assertEquals(
                "RESTAURANTS",
                config.findMatchingCategory(Set.of("tim", "hortons")).orElse(null));
    }
}
