package ca.jonathanfritz.ofxcat.config;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppConfigLoaderTest {

    private AppConfigLoader loader;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        loader = new AppConfigLoader();
    }

    @Test
    void createsDefaultConfigWhenNotExists() {
        // Execute: Load from empty directory
        AppConfigLoader.LoadResult result = loader.loadOrCreate(tempDir);

        // Verify: Config created with defaults
        assertTrue(result.wasCreated());
        assertNotNull(result.config());
        assertEquals("keyword-rules.yaml", result.config().getKeywordRulesPath());
        assertEquals(0.6, result.config().getTokenMatching().getOverlapThreshold());

        // Verify: File was created
        assertTrue(Files.exists(result.configPath()));
    }

    @Test
    void loadsExistingConfig() throws IOException {
        // Setup: Create a config file
        String yaml =
                """
                keyword_rules_path: custom-rules.yaml
                token_matching:
                  overlap_threshold: 0.75
                """;
        Path configFile = tempDir.resolve("config.yaml");
        Files.writeString(configFile, yaml);

        // Execute: Load existing config
        AppConfigLoader.LoadResult result = loader.loadOrCreate(tempDir);

        // Verify: Config loaded from file
        assertFalse(result.wasCreated());
        assertEquals("custom-rules.yaml", result.config().getKeywordRulesPath());
        assertEquals(0.75, result.config().getTokenMatching().getOverlapThreshold());
    }

    @Test
    void handlesEmptyConfigFile() throws IOException {
        // Setup: Create an empty config file
        Path configFile = tempDir.resolve("config.yaml");
        Files.writeString(configFile, "");

        // Execute: Load empty config
        AppConfigLoader.LoadResult result = loader.loadOrCreate(tempDir);

        // Verify: Returns defaults
        assertFalse(result.wasCreated());
        assertEquals("keyword-rules.yaml", result.config().getKeywordRulesPath());
    }

    @Test
    void handlesPartialConfig() throws IOException {
        // Setup: Create a config with only some fields
        String yaml = """
                keyword_rules_path: my-rules.yaml
                """;
        Path configFile = tempDir.resolve("config.yaml");
        Files.writeString(configFile, yaml);

        // Execute: Load partial config
        AppConfigLoader.LoadResult result = loader.loadOrCreate(tempDir);

        // Verify: Specified field loaded, others use defaults
        assertFalse(result.wasCreated());
        assertEquals("my-rules.yaml", result.config().getKeywordRulesPath());
        assertNotNull(result.config().getTokenMatching());
    }

    @Test
    void handlesInvalidYaml() throws IOException {
        // Setup: Create an invalid YAML file
        String invalidYaml =
                """
                this is not valid: [
                  broken: yaml
                """;
        Path configFile = tempDir.resolve("config.yaml");
        Files.writeString(configFile, invalidYaml);

        // Execute: Load invalid config
        AppConfigLoader.LoadResult result = loader.loadOrCreate(tempDir);

        // Verify: Returns defaults gracefully
        assertFalse(result.wasCreated());
        assertNotNull(result.config());
    }

    @Test
    void ignoresUnknownProperties() throws IOException {
        // Setup: Create a config with unknown properties
        String yaml =
                """
                keyword_rules_path: rules.yaml
                unknown_property: some_value
                token_matching:
                  overlap_threshold: 0.5
                  another_unknown: ignored
                """;
        Path configFile = tempDir.resolve("config.yaml");
        Files.writeString(configFile, yaml);

        // Execute: Load config with unknown properties
        AppConfigLoader.LoadResult result = loader.loadOrCreate(tempDir);

        // Verify: Known properties loaded, unknown ignored
        assertFalse(result.wasCreated());
        assertEquals("rules.yaml", result.config().getKeywordRulesPath());
        assertEquals(0.5, result.config().getTokenMatching().getOverlapThreshold());
    }

    @Test
    void createsDirectoryIfNeeded() {
        // Setup: Use a nested directory that doesn't exist
        Path nestedDir = tempDir.resolve("nested").resolve("config").resolve("dir");
        assertFalse(Files.exists(nestedDir));

        // Execute: Load from non-existent directory
        AppConfigLoader.LoadResult result = loader.loadOrCreate(nestedDir);

        // Verify: Directory and config created
        assertTrue(result.wasCreated());
        assertTrue(Files.exists(nestedDir));
        assertTrue(Files.exists(result.configPath()));
    }

    @Test
    void generatedConfigContainsComments() throws IOException {
        // Execute: Create default config
        AppConfigLoader.LoadResult result = loader.loadOrCreate(tempDir);

        // Verify: File contains helpful comments
        String content = Files.readString(result.configPath());
        assertTrue(content.contains("# OFXCat Configuration"));
        assertTrue(content.contains("# Path to keyword rules file"));
        assertTrue(content.contains("# Token-based transaction matching"));
        assertTrue(content.contains("# Minimum overlap ratio"));
    }

    @Test
    void saveConfig() throws IOException {
        // Setup: Create a config
        AppConfig config = AppConfig.defaults();
        config.setKeywordRulesPath("saved-rules.yaml");
        config.getTokenMatching().setOverlapThreshold(0.9);
        Path configPath = tempDir.resolve("config.yaml");

        // Execute: Save config
        loader.save(config, configPath);

        // Verify: File created with correct content
        assertTrue(Files.exists(configPath));
        String content = Files.readString(configPath);
        assertTrue(content.contains("saved-rules.yaml"));
        assertTrue(content.contains("0.9"));
    }

    @Test
    void configPathIsCorrect() {
        // Execute: Load config
        AppConfigLoader.LoadResult result = loader.loadOrCreate(tempDir);

        // Verify: Config path is in the expected location
        assertEquals(tempDir.resolve("config.yaml"), result.configPath());
    }
}
