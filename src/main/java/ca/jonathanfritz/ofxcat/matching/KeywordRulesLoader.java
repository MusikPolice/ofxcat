package ca.jonathanfritz.ofxcat.matching;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads keyword rules configuration from YAML files.
 * Handles missing files, empty files, and invalid YAML gracefully.
 */
public class KeywordRulesLoader {

    private static final Logger logger = LogManager.getLogger(KeywordRulesLoader.class);

    private final ObjectMapper yamlMapper;

    public KeywordRulesLoader() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory())
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Loads keyword rules from a file path.
     * Returns an empty configuration if the file doesn't exist, is empty, or contains invalid YAML.
     *
     * @param path the path to the YAML configuration file
     * @return the loaded configuration, or empty configuration on error
     */
    public KeywordRulesConfig load(Path path) {
        if (path == null) {
            logger.debug("Keyword rules path is null, returning empty configuration");
            return KeywordRulesConfig.empty();
        }

        if (!Files.exists(path)) {
            logger.debug("Keyword rules file does not exist: {}", path);
            return KeywordRulesConfig.empty();
        }

        try {
            return loadFromPath(path);
        } catch (IOException e) {
            logger.error("Failed to load keyword rules from {}: {}", path, e.getMessage());
            return KeywordRulesConfig.empty();
        }
    }

    /**
     * Loads keyword rules from a YAML string.
     * Useful for testing.
     *
     * @param yaml the YAML content as a string
     * @return the loaded configuration, or empty configuration on error
     */
    public KeywordRulesConfig loadFromString(String yaml) {
        if (yaml == null || yaml.isBlank()) {
            logger.debug("YAML string is null or blank, returning empty configuration");
            return KeywordRulesConfig.empty();
        }

        try {
            KeywordRulesConfig config = yamlMapper.readValue(yaml, KeywordRulesConfig.class);
            if (config == null) {
                return KeywordRulesConfig.empty();
            }
            logger.info("Loaded {} keyword rules", config.getRules().size());
            return config;
        } catch (IOException e) {
            logger.error("Failed to parse keyword rules YAML: {}", e.getMessage());
            return KeywordRulesConfig.empty();
        }
    }

    /**
     * Saves a keyword rules configuration to a YAML file.
     * Creates parent directories if they don't exist.
     *
     * @param config the configuration to save
     * @param path the path to write the YAML file to
     * @throws IOException if the file cannot be written
     */
    public void save(KeywordRulesConfig config, Path path) throws IOException {
        final Path parentDir = path.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }
        yamlMapper.writeValue(path.toFile(), config);
        logger.info("Saved {} keyword rules to {}", config.getRules().size(), path);
    }

    private KeywordRulesConfig loadFromPath(Path path) throws IOException {
        // Check if file is empty
        if (Files.size(path) == 0) {
            logger.debug("Keyword rules file is empty: {}", path);
            return KeywordRulesConfig.empty();
        }

        try (InputStream is = Files.newInputStream(path)) {
            return loadFromStream(is);
        }
    }

    private KeywordRulesConfig loadFromStream(InputStream inputStream) throws IOException {
        KeywordRulesConfig config = yamlMapper.readValue(inputStream, KeywordRulesConfig.class);
        if (config == null) {
            return KeywordRulesConfig.empty();
        }
        logger.info("Loaded {} keyword rules", config.getRules().size());
        return config;
    }
}
