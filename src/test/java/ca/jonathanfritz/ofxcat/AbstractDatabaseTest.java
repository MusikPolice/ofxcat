package ca.jonathanfritz.ofxcat;

import ca.jonathanfritz.ofxcat.dao.DAOModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;

import java.sql.Connection;

/**
 * Parent class for unit tests that require an initialized database to be present
 */
public abstract class AbstractDatabaseTest {

    protected static Connection connection;
    private static Flyway flyway;

    @BeforeAll
    static void setup() {
        // get a connection to an in-memory database for child classes to use
        final Injector injector = Guice.createInjector(new DAOModule());
        connection = injector.getInstance(Connection.class);

        // initialize the schema of that in-memory database
        flyway = injector.getInstance(Flyway.class);
        flyway.migrate();
    }

    /**
     * Drops all data in the database and re-initializes the database schema.
     * Useful for tests that rely on having empty tables
     */
    void cleanDatabase() {
        flyway.clean();
        flyway.migrate();
    }
}
