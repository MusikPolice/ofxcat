package ca.jonathanfritz.ofxcat.matching;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for TokenNormalizer which converts transaction descriptions into normalized token sets.
 */
class TokenNormalizerTest {

    private TokenNormalizer tokenNormalizer;

    @BeforeEach
    void setUp() {
        tokenNormalizer = new TokenNormalizer();
    }

    @Test
    void lowercasesAllTokens() {
        // Setup: uppercase input
        String input = "STARBUCKS COFFEE";

        // Execute
        Set<String> tokens = tokenNormalizer.normalize(input);

        // Verify: all tokens are lowercase
        assertEquals(2, tokens.size());
        assertTrue(tokens.contains("starbucks"));
        assertTrue(tokens.contains("coffee"));
    }

    @Test
    void removesPunctuation() {
        // Setup: input with apostrophes and other punctuation
        String input = "MCDONALD'S";

        // Execute
        Set<String> tokens = tokenNormalizer.normalize(input);

        // Verify: punctuation is removed
        assertEquals(1, tokens.size());
        assertTrue(tokens.contains("mcdonalds"));
    }

    @Test
    void removesStoreNumbers() {
        // Setup: input with store number
        String input = "STARBUCKS #4756";

        // Execute
        Set<String> tokens = tokenNormalizer.normalize(input);

        // Verify: store number is removed, merchant name remains
        assertEquals(1, tokens.size());
        assertTrue(tokens.contains("starbucks"));
    }

    @Test
    void removesPhoneNumbers() {
        // Setup: input with phone number
        String input = "STARBUCKS 800-782-7282";

        // Execute
        Set<String> tokens = tokenNormalizer.normalize(input);

        // Verify: phone number is removed
        assertEquals(1, tokens.size());
        assertTrue(tokens.contains("starbucks"));
    }

    @Test
    void removesShortTokens() {
        // Setup: input with single character tokens
        String input = "A & W RESTAURANT";

        // Execute
        Set<String> tokens = tokenNormalizer.normalize(input);

        // Verify: single character tokens are removed
        assertEquals(2, tokens.size());
        assertTrue(tokens.contains("aw"));
        assertTrue(tokens.contains("restaurant"));
    }

    @Test
    void removesStopWords() {
        // Setup: input with stop words
        String input = "THE BEER STORE OF ONTARIO";

        // Execute
        Set<String> tokens = tokenNormalizer.normalize(input);

        // Verify: stop words are removed
        assertEquals(3, tokens.size());
        assertTrue(tokens.contains("beer"));
        assertTrue(tokens.contains("store"));
        assertTrue(tokens.contains("ontario"));
    }

    @Test
    void handlesNullInput() {
        // Setup: null input
        Set<String> tokens = tokenNormalizer.normalize(null);

        // Verify: returns empty set
        assertTrue(tokens.isEmpty());
    }

    @Test
    void handlesEmptyString() {
        // Setup: empty string
        Set<String> tokens = tokenNormalizer.normalize("");

        // Verify: returns empty set
        assertTrue(tokens.isEmpty());
    }

    @Test
    void handlesBlankString() {
        // Setup: whitespace-only string
        Set<String> tokens = tokenNormalizer.normalize("   \t\n   ");

        // Verify: returns empty set
        assertTrue(tokens.isEmpty());
    }

    @Test
    void decodesXmlEntities() {
        // Setup: input with XML-encoded ampersand (common in OFX files)
        String input = "A&amp;W #4330";

        // Execute
        Set<String> tokens = tokenNormalizer.normalize(input);

        // Verify: XML entity is decoded, punctuation removed, store number filtered
        assertEquals(1, tokens.size());
        assertTrue(tokens.contains("aw"));
    }

    @Test
    void retainsPaymentProcessorNames() {
        // Setup: Square payment processor pattern
        String squareInput = "SP * ONCE UPON A CHILD";

        // Execute
        Set<String> squareTokens = tokenNormalizer.normalize(squareInput);

        // Verify: SP is retained (payment processor), asterisk removed
        // "a" is filtered as because it is a short token
        assertEquals(4, squareTokens.size());
        assertTrue(squareTokens.contains("sp"));
        assertTrue(squareTokens.contains("once"));
        assertTrue(squareTokens.contains("upon"));
        assertTrue(squareTokens.contains("child"));

        // Setup: PayPal pattern
        String paypalInput = "PAYPAL *FACERECORDS";

        // Execute
        Set<String> paypalTokens = tokenNormalizer.normalize(paypalInput);

        // Verify: PayPal and merchant name retained
        assertEquals(2, paypalTokens.size());
        assertTrue(paypalTokens.contains("paypal"));
        assertTrue(paypalTokens.contains("facerecords"));
    }

    @Test
    void starbucksVariantsNormalizeIdentically() {
        // Setup: Starbucks with store number
        String storeNumber = "STARBUCKS #4756";
        Set<String> storeTokens = tokenNormalizer.normalize(storeNumber);

        // Setup: Starbucks with phone number
        String phoneNumber = "STARBUCKS 800-782-7282";
        Set<String> phoneTokens = tokenNormalizer.normalize(phoneNumber);

        // Verify: Both normalize to same token set
        assertEquals(storeTokens, phoneTokens);
        assertEquals(Set.of("starbucks"), storeTokens);
    }

    @Test
    void walmartVariantsNormalizeIdentically() {
        // Setup: Walmart with different store numbers
        String store1 = "WAL-MART #1155";
        String store2 = "WAL-MART #3045";

        // Execute
        Set<String> tokens1 = tokenNormalizer.normalize(store1);
        Set<String> tokens2 = tokenNormalizer.normalize(store2);

        // Verify: Both normalize to same token set
        assertEquals(tokens1, tokens2);
        assertEquals(Set.of("walmart"), tokens1);
    }

    @Test
    void mcdonaldsVariantsNormalizeIdentically() {
        // Setup: McDonald's with store number
        String input = "MCDONALD'S #290";

        // Execute
        Set<String> tokens = tokenNormalizer.normalize(input);

        // Verify: Apostrophe removed, store number filtered
        assertEquals(Set.of("mcdonalds"), tokens);
    }

    @Test
    void amazonVariantsNormalizeIdentically() {
        // Setup: Amazon.ca with random order ID
        String order1 = "Amazon.ca*T23YP3F33";
        String order2 = "Amazon.ca*X56OF5GV3";

        // Execute
        Set<String> tokens1 = tokenNormalizer.normalize(order1);
        Set<String> tokens2 = tokenNormalizer.normalize(order2);

        // Verify: Both normalize to same core tokens
        // Note: order IDs are alphanumeric but mixed case, they should be included as tokens
        assertEquals(3, tokens1.size());
        assertTrue(tokens1.contains("amazon"));
        assertTrue(tokens1.contains("ca"));

        assertEquals(3, tokens2.size());
        assertTrue(tokens2.contains("amazon"));
        assertTrue(tokens2.contains("ca"));
    }

    @Test
    void usesDefaultConfig() {
        // Setup: Create normalizer with default config
        NormalizationConfig config = NormalizationConfig.defaults();
        TokenNormalizer configuredNormalizer = new TokenNormalizer(config);
        String input = "THE STARBUCKS COFFEE";

        // Execute
        Set<String> tokens = configuredNormalizer.normalize(input);

        // Verify: Default config filters stop words like "the"
        assertEquals(2, tokens.size());
        assertTrue(tokens.contains("starbucks"));
        assertTrue(tokens.contains("coffee"));
    }

    @Test
    void respectsCustomStopWords() {
        // Setup: Create config with custom stop words that includes "coffee"
        NormalizationConfig config =
                NormalizationConfig.builder().stopWords(Set.of("coffee")).build();
        TokenNormalizer configuredNormalizer = new TokenNormalizer(config);
        String input = "STARBUCKS COFFEE";

        // Execute
        Set<String> tokens = configuredNormalizer.normalize(input);

        // Verify: Custom stop word "coffee" is filtered
        assertEquals(1, tokens.size());
        assertTrue(tokens.contains("starbucks"));
    }

    @Test
    void respectsCustomMinTokenLength() {
        // Setup: Create config with minimum token length of 3
        NormalizationConfig config =
                NormalizationConfig.builder().minTokenLength(3).build();
        TokenNormalizer configuredNormalizer = new TokenNormalizer(config);
        String input = "SP STARBUCKS";

        // Execute
        Set<String> tokens = configuredNormalizer.normalize(input);

        // Verify: "sp" is filtered (length 2 < minLength 3)
        assertEquals(1, tokens.size());
        assertTrue(tokens.contains("starbucks"));
    }
}
