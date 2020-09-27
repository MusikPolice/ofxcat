package ca.jonathanfritz.ofxcat.dao;

import ca.jonathanfritz.ofxcat.utils.PathUtils;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DAOModule extends AbstractModule {

    private static final Logger logger = LoggerFactory.getLogger(DAOModule.class);

    /**
     * Provides a database connection - we don't ever close this connection, but Guice ensures that it is a singleton,
     * so it should die with the application
     */
    @Provides
    @Singleton
    public Connection provideConnection(PathUtils pathUtils) {
        try {
            final String connectionString = pathUtils.getDatabaseConnectionString();
            logger.info("Database connection string is {}", connectionString);

            return DriverManager.getConnection(connectionString);
        } catch (SQLException e) {
            throw new ProvisionException("Failed to connect to database", e);
        }
    }

    /**
     * Provides access to {@link Flyway}, a utility that lets us apply migrations to the database schema.
     * Similar to {@link #provideConnection(PathUtils)} above, does not close its connection, but is a singleton, so the
     * connection should die with the application
     */
    @Provides
    @Singleton
    public Flyway provideFlyway(PathUtils pathUtils) {
        final SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl(pathUtils.getDatabaseConnectionString());
        return Flyway.configure().dataSource(dataSource).load();
    }
}
