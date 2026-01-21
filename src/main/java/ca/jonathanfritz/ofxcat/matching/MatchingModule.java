package ca.jonathanfritz.ofxcat.matching;

import ca.jonathanfritz.ofxcat.config.AppConfig;
import ca.jonathanfritz.ofxcat.config.AppConfigLoader;
import ca.jonathanfritz.ofxcat.utils.PathUtils;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

/**
 * Guice module for matching-related dependencies.
 * Provides bindings for TokenNormalizer, KeywordRulesConfig, and related services.
 */
public class MatchingModule extends AbstractModule {

    private static final Logger logger = LogManager.getLogger(MatchingModule.class);

    private final AppConfig appConfig;
    private final Path configDirectory;

    public MatchingModule(AppConfig appConfig, Path configDirectory) {
        this.appConfig = appConfig;
        this.configDirectory = configDirectory;
    }

    @Provides
    @Singleton
    public AppConfig provideAppConfig() {
        return appConfig;
    }

    @Provides
    @Singleton
    public TokenNormalizer provideTokenNormalizer() {
        return new TokenNormalizer();
    }

    @Provides
    @Singleton
    public TokenMatchingConfig provideTokenMatchingConfig() {
        return TokenMatchingConfig.builder()
                .overlapThreshold(appConfig.getTokenMatching().getOverlapThreshold())
                .build();
    }

    @Provides
    @Singleton
    public KeywordRulesConfig provideKeywordRulesConfig() {
        Path keywordRulesPath = appConfig.resolveKeywordRulesPath(configDirectory);

        // First try to load user rules from config directory
        KeywordRulesLoader loader = new KeywordRulesLoader();
        KeywordRulesConfig userConfig = loader.load(keywordRulesPath);

        if (!userConfig.getRules().isEmpty()) {
            logger.info("Loaded {} keyword rules from {}", userConfig.getRules().size(), keywordRulesPath);
            return userConfig;
        }

        // Fall back to bundled defaults from classpath
        logger.info("No user keyword rules found, loading bundled defaults");
        return loadBundledDefaults(loader);
    }

    private KeywordRulesConfig loadBundledDefaults(KeywordRulesLoader loader) {
        try (var is = getClass().getClassLoader().getResourceAsStream("keyword-rules.yaml")) {
            if (is == null) {
                logger.warn("No bundled keyword-rules.yaml found in classpath");
                return KeywordRulesConfig.empty();
            }
            String yaml = new String(is.readAllBytes());
            KeywordRulesConfig config = loader.loadFromString(yaml);
            logger.info("Loaded {} bundled keyword rules", config.getRules().size());
            return config;
        } catch (Exception e) {
            logger.error("Failed to load bundled keyword rules: {}", e.getMessage());
            return KeywordRulesConfig.empty();
        }
    }
}
