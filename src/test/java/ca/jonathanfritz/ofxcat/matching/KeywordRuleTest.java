package ca.jonathanfritz.ofxcat.matching;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class KeywordRuleTest {

    @Test
    void matchesAnyKeywordByDefault() {
        // Setup: Rule with multiple keywords, matchAll defaults to false
        KeywordRule rule = new KeywordRule(List.of("starbucks", "coffee"), "RESTAURANTS");

        // Verify: Matches if any keyword is present
        assertTrue(rule.matches(Set.of("starbucks", "downtown")));
        assertTrue(rule.matches(Set.of("coffee", "shop")));
        assertTrue(rule.matches(Set.of("starbucks", "coffee")));

        // Verify: No match if neither keyword is present
        assertFalse(rule.matches(Set.of("tim", "hortons")));
    }

    @Test
    void matchesAllKeywordsWhenRequired() {
        // Setup: Rule with matchAll = true
        KeywordRule rule = new KeywordRule(List.of("tim", "hortons"), "RESTAURANTS", true);

        // Verify: Only matches when ALL keywords are present
        assertTrue(rule.matches(Set.of("tim", "hortons", "coffee")));

        // Verify: No match if only some keywords are present
        assertFalse(rule.matches(Set.of("tim", "coffee")));
        assertFalse(rule.matches(Set.of("hortons", "coffee")));
    }

    @Test
    void caseInsensitiveMatching() {
        // Setup: Rule with lowercase keywords
        KeywordRule rule = new KeywordRule(List.of("starbucks"), "RESTAURANTS");

        // Verify: Keywords are normalized to lowercase for comparison
        // The tokens are expected to already be lowercase from TokenNormalizer
        assertTrue(rule.matches(Set.of("starbucks")));

        // Setup: Rule with mixed case keywords
        KeywordRule mixedCaseRule = new KeywordRule(List.of("STARBUCKS", "Starbucks"), "RESTAURANTS");

        // Verify: Rule keywords are normalized to lowercase
        assertTrue(mixedCaseRule.matches(Set.of("starbucks")));
    }

    @Test
    void matchesNormalizedTokens() {
        // Setup: Rule matching common transaction patterns
        KeywordRule rule = new KeywordRule(List.of("walmart"), "GROCERIES");

        // Verify: Matches normalized tokens (already processed by TokenNormalizer)
        // TokenNormalizer would convert "WAL-MART #1155" to {"walmart"}
        assertTrue(rule.matches(Set.of("walmart")));

        // Verify: Extra tokens don't prevent match
        assertTrue(rule.matches(Set.of("walmart", "store", "1155")));
    }

    @Test
    void emptyKeywordsDoesNotMatch() {
        // Setup: Rule with empty keywords
        KeywordRule rule = new KeywordRule(List.of(), "RESTAURANTS");

        // Verify: Never matches
        assertFalse(rule.matches(Set.of("starbucks")));
        assertFalse(rule.matches(Set.of()));
    }

    @Test
    void nullKeywordsDoesNotMatch() {
        // Setup: Rule with null keywords
        KeywordRule rule = new KeywordRule(null, "RESTAURANTS");

        // Verify: Never matches
        assertFalse(rule.matches(Set.of("starbucks")));
    }

    @Test
    void emptyTokensDoesNotMatch() {
        // Setup: Valid rule
        KeywordRule rule = new KeywordRule(List.of("starbucks"), "RESTAURANTS");

        // Verify: Empty tokens never match
        assertFalse(rule.matches(Set.of()));
    }

    @Test
    void nullTokensDoesNotMatch() {
        // Setup: Valid rule
        KeywordRule rule = new KeywordRule(List.of("starbucks"), "RESTAURANTS");

        // Verify: Null tokens never match
        assertFalse(rule.matches(null));
    }

    @Test
    void matchAllWithSingleKeyword() {
        // Setup: Rule with single keyword and matchAll = true
        KeywordRule rule = new KeywordRule(List.of("netflix"), "ENTERTAINMENT", true);

        // Verify: Works the same as matchAll = false for single keyword
        assertTrue(rule.matches(Set.of("netflix", "subscription")));
        assertFalse(rule.matches(Set.of("spotify")));
    }
}
