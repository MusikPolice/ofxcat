package ca.jonathanfritz.ofxcat;

import ca.jonathanfritz.ofxcat.datastore.utils.DatastoreModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.sql.Connection;

/**
 * Parent class for unit tests that require an initialized database to be present
 */
public abstract class AbstractDatabaseTest {

    protected final Injector injector;
    protected final Connection connection;
    private static Flyway flyway;

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
}
