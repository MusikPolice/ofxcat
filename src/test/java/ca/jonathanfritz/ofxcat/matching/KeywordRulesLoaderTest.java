package ca.jonathanfritz.ofxcat.matching;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class KeywordRulesLoaderTest {

    private KeywordRulesLoader loader;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        loader = new KeywordRulesLoader();
    }

    @Test
    void loadsValidYamlFile() throws IOException {
        // Setup: Create a valid YAML file
        String yaml = """
                version: 1
                settings:
                  auto_categorize: true
                rules:
                  - keywords: [starbucks]
                    category: RESTAURANTS
                  - keywords: [walmart]
                    category: GROCERIES
                  - keywords: [tim, hortons]
                    category: RESTAURANTS
                    match_all: true
                """;
        Path yamlFile = tempDir.resolve("keyword-rules.yaml");
        Files.writeString(yamlFile, yaml);

        // Execute: Load the file
        KeywordRulesConfig config = loader.load(yamlFile);

        // Verify: Config loaded correctly
        assertEquals(1, config.getVersion());
        assertTrue(config.isAutoCategorizeEnabled());
        assertEquals(3, config.getRules().size());

        // Verify: Rules work as expected
        assertEquals("RESTAURANTS", config.findMatchingCategory(Set.of("starbucks")).orElse(null));
        assertEquals("GROCERIES", config.findMatchingCategory(Set.of("walmart")).orElse(null));
        assertEquals("RESTAURANTS", config.findMatchingCategory(Set.of("tim", "hortons")).orElse(null));
        assertFalse(config.findMatchingCategory(Set.of("tim")).isPresent()); // Requires both
    }

    @Test
    void handlesEmptyFile() throws IOException {
        // Setup: Create an empty file
        Path emptyFile = tempDir.resolve("empty.yaml");
        Files.writeString(emptyFile, "");

        // Execute: Load the empty file
        KeywordRulesConfig config = loader.load(emptyFile);

        // Verify: Returns empty config
        assertTrue(config.getRules().isEmpty());
    }

    @Test
    void handlesMissingFile() {
        // Setup: Path to a non-existent file
        Path missingFile = tempDir.resolve("does-not-exist.yaml");

        // Execute: Try to load missing file
        KeywordRulesConfig config = loader.load(missingFile);

        // Verify: Returns empty config
        assertTrue(config.getRules().isEmpty());
    }

    @Test
    void handlesNullPath() {
        // Execute: Load with null path
        KeywordRulesConfig config = loader.load((Path) null);

        // Verify: Returns empty config
        assertTrue(config.getRules().isEmpty());
    }

    @Test
    void handlesInvalidYaml() throws IOException {
        // Setup: Create a file with invalid YAML
        String invalidYaml = """
                this is not valid yaml: [
                  broken: structure
                """;
        Path invalidFile = tempDir.resolve("invalid.yaml");
        Files.writeString(invalidFile, invalidYaml);

        // Execute: Try to load invalid file
        KeywordRulesConfig config = loader.load(invalidFile);

        // Verify: Returns empty config (graceful fallback)
        assertTrue(config.getRules().isEmpty());
    }

    @Test
    void loadFromStringWithValidYaml() {
        // Setup: Valid YAML string
        String yaml = """
                rules:
                  - keywords: [netflix]
                    category: ENTERTAINMENT
                """;

        // Execute: Load from string
        KeywordRulesConfig config = loader.loadFromString(yaml);

        // Verify: Loaded correctly
        assertEquals(1, config.getRules().size());
        assertEquals("ENTERTAINMENT", config.findMatchingCategory(Set.of("netflix")).orElse(null));
    }

    @Test
    void loadFromStringWithNullString() {
        // Execute: Load from null string
        KeywordRulesConfig config = loader.loadFromString(null);

        // Verify: Returns empty config
        assertTrue(config.getRules().isEmpty());
    }

    @Test
    void loadFromStringWithBlankString() {
        // Execute: Load from blank string
        KeywordRulesConfig config = loader.loadFromString("   ");

        // Verify: Returns empty config
        assertTrue(config.getRules().isEmpty());
    }

    @Test
    void loadsSettingsFromYaml() throws IOException {
        // Setup: YAML with auto_categorize disabled
        String yaml = """
                settings:
                  auto_categorize: false
                rules:
                  - keywords: [starbucks]
                    category: RESTAURANTS
                """;
        Path yamlFile = tempDir.resolve("keyword-rules.yaml");
        Files.writeString(yamlFile, yaml);

        // Execute: Load the file
        KeywordRulesConfig config = loader.load(yamlFile);

        // Verify: Settings loaded correctly
        assertFalse(config.isAutoCategorizeEnabled());
    }

    @Test
    void handlesYamlWithOnlyRules() throws IOException {
        // Setup: YAML with only rules (no version or settings)
        String yaml = """
                rules:
                  - keywords: [costco]
                    category: GROCERIES
                """;
        Path yamlFile = tempDir.resolve("keyword-rules.yaml");
        Files.writeString(yamlFile, yaml);

        // Execute: Load the file
        KeywordRulesConfig config = loader.load(yamlFile);

        // Verify: Rules loaded, defaults used for missing fields
        assertEquals(1, config.getRules().size());
        assertTrue(config.isAutoCategorizeEnabled()); // Default
        assertEquals("GROCERIES", config.findMatchingCategory(Set.of("costco")).orElse(null));
    }

    @Test
    void ignoresUnknownProperties() throws IOException {
        // Setup: YAML with extra properties
        String yaml = """
                version: 1
                unknown_property: some_value
                rules:
                  - keywords: [starbucks]
                    category: RESTAURANTS
                    unknown_rule_property: ignored
                """;
        Path yamlFile = tempDir.resolve("keyword-rules.yaml");
        Files.writeString(yamlFile, yaml);

        // Execute: Load the file (should not fail on unknown properties)
        KeywordRulesConfig config = loader.load(yamlFile);

        // Verify: Rules loaded despite unknown properties
        assertEquals(1, config.getRules().size());
        assertEquals("RESTAURANTS", config.findMatchingCategory(Set.of("starbucks")).orElse(null));
    }
}
