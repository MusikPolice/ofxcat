package ca.jonathanfritz.ofxcat.utils;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Tests for StringUtils utility methods.
 */
class StringUtilsTest {

    @Test
    void coerceNullableStringWithNull() {
        // Setup: null input
        String input = null;

        // Execute
        String result = StringUtils.coerceNullableString(input);

        // Verify: Returns empty string
        assertEquals("", result);
    }

    @Test
    void coerceNullableStringWithEmptyString() {
        // Setup: empty string
        String input = "";

        // Execute
        String result = StringUtils.coerceNullableString(input);

        // Verify: Returns empty string
        assertEquals("", result);
    }

    @Test
    void coerceNullableStringWithWhitespaceOnly() {
        // Setup: whitespace-only string
        String input = "   \t\n  ";

        // Execute
        String result = StringUtils.coerceNullableString(input);

        // Verify: Returns empty string (whitespace is considered blank)
        assertEquals("", result);
    }

    @Test
    void coerceNullableStringWithLeadingTrailingWhitespace() {
        // Setup: string with leading/trailing whitespace
        String input = "  hello world  ";

        // Execute
        String result = StringUtils.coerceNullableString(input);

        // Verify: Returns trimmed string
        assertEquals("hello world", result);
    }

    @Test
    void coerceNullableStringWithValidString() {
        // Setup: normal string without whitespace issues
        String input = "test";

        // Execute
        String result = StringUtils.coerceNullableString(input);

        // Verify: Returns the string unchanged
        assertEquals("test", result);
    }

    @Test
    void coerceNullableStringWithUnicode() {
        // Setup: string with unicode characters
        String input = "  café résumé 日本語  ";

        // Execute
        String result = StringUtils.coerceNullableString(input);

        // Verify: Returns trimmed string with unicode preserved
        assertEquals("café résumé 日本語", result);
    }

    @Test
    void coerceNullableStringWithSpecialCharacters() {
        // Setup: string with special characters
        String input = "  @#$%^&*()  ";

        // Execute
        String result = StringUtils.coerceNullableString(input);

        // Verify: Returns trimmed string with special chars preserved
        assertEquals("@#$%^&*()", result);
    }
}
