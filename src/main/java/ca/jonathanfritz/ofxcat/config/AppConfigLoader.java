package ca.jonathanfritz.ofxcat.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Loads and saves application configuration from YAML files.
 * Handles auto-creation of config with defaults when file doesn't exist.
 */
public class AppConfigLoader {

    private static final Logger logger = LogManager.getLogger(AppConfigLoader.class);
    private static final String CONFIG_FILE_NAME = "config.yaml";

    private final ObjectMapper yamlMapper;

    public AppConfigLoader() {
        YAMLFactory yamlFactory = new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        this.yamlMapper = new ObjectMapper(yamlFactory)
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Result of loading configuration, includes whether the file was newly created.
     */
    public record LoadResult(AppConfig config, Path configPath, boolean wasCreated) {}

    /**
     * Loads configuration from the specified directory.
     * If config.yaml doesn't exist, creates it with default values.
     *
     * @param configDirectory the directory to load config from (typically ~/.ofxcat/)
     * @return the load result containing the config and whether it was newly created
     */
    public LoadResult loadOrCreate(Path configDirectory) {
        Path configPath = configDirectory.resolve(CONFIG_FILE_NAME);

        if (Files.exists(configPath)) {
            return loadExisting(configPath);
        } else {
            return createDefault(configDirectory, configPath);
        }
    }

    /**
     * Loads configuration from an existing file.
     *
     * @param configPath path to the configuration file
     * @return the load result
     */
    private LoadResult loadExisting(Path configPath) {
        try {
            if (Files.size(configPath) == 0) {
                logger.debug("Config file is empty, using defaults: {}", configPath);
                return new LoadResult(AppConfig.defaults(), configPath, false);
            }

            AppConfig config = yamlMapper.readValue(configPath.toFile(), AppConfig.class);
            if (config == null) {
                config = AppConfig.defaults();
            }
            logger.info("Loaded configuration from {}", configPath);
            return new LoadResult(config, configPath, false);
        } catch (IOException e) {
            logger.error("Failed to load config from {}: {}. Using defaults.", configPath, e.getMessage());
            return new LoadResult(AppConfig.defaults(), configPath, false);
        }
    }

    /**
     * Creates a new configuration file with default values.
     *
     * @param configDirectory the directory to create the config in
     * @param configPath      the full path to the config file
     * @return the load result indicating the file was created
     */
    private LoadResult createDefault(Path configDirectory, Path configPath) {
        AppConfig config = AppConfig.defaults();

        try {
            // Ensure directory exists
            if (!Files.exists(configDirectory)) {
                Files.createDirectories(configDirectory);
                logger.debug("Created config directory: {}", configDirectory);
            }

            // Write config with comments
            String yamlContent = generateConfigWithComments(config);
            Files.writeString(configPath, yamlContent);
            logger.info("Created default configuration at {}", configPath);

            return new LoadResult(config, configPath, true);
        } catch (IOException e) {
            logger.error("Failed to create config file at {}: {}. Using defaults.", configPath, e.getMessage());
            return new LoadResult(config, configPath, false);
        }
    }

    /**
     * Generates YAML configuration content with helpful comments.
     */
    private String generateConfigWithComments(AppConfig config) throws IOException {
        return "# OFXCat Configuration\n" + "# Edit this file to customize application behavior.\n"
                + "\n"
                + "# Path to keyword rules file (relative to this config directory, or absolute)\n"
                + "# Default: keyword-rules.yaml\n"
                + "keyword_rules_path: "
                + config.getKeywordRulesPath() + "\n" + "\n"
                + "# Token-based transaction matching settings\n"
                + "token_matching:\n"
                + "  # Minimum overlap ratio (0.0-1.0) required for a match\n"
                + "  # Higher values require more tokens to match\n"
                + "  # Default: 0.6 (60% of tokens must match)\n"
                + "  overlap_threshold: "
                + config.getTokenMatching().getOverlapThreshold() + "\n";
    }

    /**
     * Saves configuration to a file.
     *
     * @param config     the configuration to save
     * @param configPath the path to save to
     * @throws IOException if saving fails
     */
    public void save(AppConfig config, Path configPath) throws IOException {
        String yamlContent = generateConfigWithComments(config);
        Files.writeString(configPath, yamlContent);
        logger.info("Saved configuration to {}", configPath);
    }
}
