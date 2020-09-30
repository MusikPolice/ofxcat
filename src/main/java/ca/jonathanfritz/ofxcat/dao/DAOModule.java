package ca.jonathanfritz.ofxcat.dao;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class DAOModule extends AbstractModule {

    private final String connectionString;

    private static final Logger logger = LoggerFactory.getLogger(DAOModule.class);

    /**
     * Wires up a connection to the specified database
     */
    public DAOModule(String connectionString) {
        this.connectionString = connectionString;
        logger.info("Database connection string is {}", connectionString);
    }

    /**
     * Wires up an in-memory database for testing purposes
     */
    public DAOModule() {
        this.connectionString = "jdbc:sqlite:mem:testdb";
        logger.info("Database connection string is {}", connectionString);
    }

    @Provides
    public DataSource provideDataSource() {
        // if any SQLite-specific config is required, this is where it should be added
        final SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl(connectionString);
        return dataSource;
    }

    /**
     * Provides a database connection - we don't ever close this connection, but Guice ensures that it is a singleton,
     * so it should die with the application
     */
    @Provides
    @Singleton
    public Connection provideConnection(DataSource dataSource) {
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            throw new ProvisionException("Failed to connect to database", e);
        }
    }

    /**
     * Provides access to {@link Flyway}, a utility that lets us apply migrations to the database schema.
     * Flyway uses the specified {@link DataSource} to create and close its own connection to the database
     */
    @Provides
    @Singleton
    public Flyway provideFlyway(DataSource dataSource) {
        // note that Flyway will use the DataSource to create its own Connection to the database, so in-memory databases
        // must be named, or else Flyway will act on a different database than the application.
        // this limitation does not apply to on-disk databases.
        return Flyway.configure().dataSource(dataSource).load();
    }
}
