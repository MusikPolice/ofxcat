package ca.jonathanfritz.ofxcat.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class AppConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void defaultsHasSensibleValues() {
        AppConfig config = AppConfig.defaults();

        assertEquals("keyword-rules.yaml", config.getKeywordRulesPath());
        assertNotNull(config.getTokenMatching());
        assertEquals(0.6, config.getTokenMatching().getOverlapThreshold());
    }

    @Test
    void resolveKeywordRulesPathWithRelativePath() {
        AppConfig config = AppConfig.defaults();
        config.setKeywordRulesPath("keyword-rules.yaml");

        Path resolved = config.resolveKeywordRulesPath(tempDir);

        assertEquals(tempDir.resolve("keyword-rules.yaml"), resolved);
    }

    @Test
    void resolveKeywordRulesPathWithAbsolutePath() {
        AppConfig config = AppConfig.defaults();
        // Use tempDir to create a platform-independent absolute path
        Path absolutePath = tempDir.resolve("custom").resolve("rules.yaml").toAbsolutePath();
        config.setKeywordRulesPath(absolutePath.toString());

        // Create a different directory to resolve from
        Path differentDir = tempDir.resolve("other");

        Path resolved = config.resolveKeywordRulesPath(differentDir);

        // Absolute paths should be returned as-is, not resolved against the config dir
        assertEquals(absolutePath, resolved);
    }

    @Test
    void resolveKeywordRulesPathWithNestedRelativePath() {
        AppConfig config = AppConfig.defaults();
        config.setKeywordRulesPath("rules/custom-rules.yaml");

        Path resolved = config.resolveKeywordRulesPath(tempDir);

        assertEquals(tempDir.resolve("rules/custom-rules.yaml"), resolved);
    }

    @Test
    void tokenMatchingSettingsCanBeModified() {
        AppConfig config = AppConfig.defaults();

        config.getTokenMatching().setOverlapThreshold(0.8);

        assertEquals(0.8, config.getTokenMatching().getOverlapThreshold());
    }

    @Test
    void settersUpdateValues() {
        AppConfig config = new AppConfig();

        config.setKeywordRulesPath("custom-rules.yaml");
        AppConfig.TokenMatchingSettings newSettings = new AppConfig.TokenMatchingSettings();
        newSettings.setOverlapThreshold(0.75);
        config.setTokenMatching(newSettings);

        assertEquals("custom-rules.yaml", config.getKeywordRulesPath());
        assertEquals(0.75, config.getTokenMatching().getOverlapThreshold());
    }
}
