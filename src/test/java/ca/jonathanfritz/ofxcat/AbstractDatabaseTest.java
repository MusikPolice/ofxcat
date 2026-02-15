package ca.jonathanfritz.ofxcat;

import ca.jonathanfritz.ofxcat.cli.CLI;
import ca.jonathanfritz.ofxcat.datastore.CategorizedTransactionDao;
import ca.jonathanfritz.ofxcat.datastore.CategoryDao;
import ca.jonathanfritz.ofxcat.datastore.TransactionTokenDao;
import ca.jonathanfritz.ofxcat.datastore.utils.DatastoreModule;
import ca.jonathanfritz.ofxcat.matching.KeywordRulesConfig;
import ca.jonathanfritz.ofxcat.matching.TokenMatchingConfig;
import ca.jonathanfritz.ofxcat.matching.TokenMatchingService;
import ca.jonathanfritz.ofxcat.matching.TokenNormalizer;
import ca.jonathanfritz.ofxcat.service.TransactionCategoryService;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.sql.Connection;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;

/**
 * Parent class for unit tests that require an initialized database to be present
 */
@Tag("database")
public abstract class AbstractDatabaseTest {

    protected final Injector injector;
    protected final Connection connection;
    private static Flyway flyway;

    // Shared test dependencies for TransactionCategoryService
    protected final TokenNormalizer tokenNormalizer = new TokenNormalizer();
    protected final TokenMatchingConfig tokenMatchingConfig = TokenMatchingConfig.defaults();

    public AbstractDatabaseTest() {
        // get a connection to an in-memory database for child classes to use
        injector = Guice.createInjector(DatastoreModule.inMemory());
        connection = injector.getInstance(Connection.class);
    }

    @BeforeEach
    protected void migrate() {
        // initialize the schema of that in-memory database
        flyway = injector.getInstance(Flyway.class);
        flyway.migrate();
    }

    @AfterEach
    protected void cleanup() {
        flyway.clean();
        flyway.migrate();
    }

    /**
     * Creates a TransactionCategoryService for testing with the specified CLI.
     * Uses empty keyword rules to avoid auto-categorization from rules during tests.
     */
    protected TransactionCategoryService createTransactionCategoryService(
            CategoryDao categoryDao, CategorizedTransactionDao categorizedTransactionDao, CLI cli) {
        TransactionTokenDao transactionTokenDao = new TransactionTokenDao();
        TokenMatchingService tokenMatchingService = new TokenMatchingService(
                connection, transactionTokenDao, categoryDao, tokenNormalizer, tokenMatchingConfig);
        KeywordRulesConfig keywordRulesConfig = KeywordRulesConfig.empty();

        return new TransactionCategoryService(
                categoryDao, categorizedTransactionDao, tokenNormalizer, tokenMatchingService, keywordRulesConfig, cli);
    }
}
