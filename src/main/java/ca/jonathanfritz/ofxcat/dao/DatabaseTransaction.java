package ca.jonathanfritz.ofxcat.dao;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.function.Consumer;

// TODO test me
public class DatabaseTransaction implements Closeable {

    private final Connection connection;

    private static Logger logger = LoggerFactory.getLogger(DatabaseTransaction.class);

    DatabaseTransaction(Connection connection) {
        this.connection = connection;
    }

    /**
     * Executes the specified selectStatement.
     * @param selectStatement the SQL SELECT statement to execute against the database
     * @param statementPreparer an {@link SqlConsumer<PreparedStatement>} that allows the caller to populate any ?
     *                          variables that appear in the selectStatement. Can be null if the selectStatement is not
     *                          parameterized
     * @param resultDeserializer an {@link SqlFunction<ResultSet, Optional<T>>} that is responsible for transforming the
     *                           ResultSet returned by the database query into an Optional<T> that the caller can use
     * @param <T> the type of object that the query is expected to return. Must extend {@link Entity}
     * @return an {@link Optional<T>} or {@link Optional#empty()} if the query does not return a result
     * @throws SQLException if something goes wrong. The current transaction will be rolled back before the exception
     * is thrown
     */
    public <T extends Entity> Optional<T> query(String selectStatement, SqlConsumer<PreparedStatement> statementPreparer, SqlFunction<ResultSet, Optional<T>> resultDeserializer) throws SQLException {
        connection.setAutoCommit(false);

        // verify syntax
        if (!StringUtils.startsWith(selectStatement, "SELECT")) {
            throw rollback(new SQLException("selectStatement must start with SELECT"));
        }

        // populate the sql parameters
        try (PreparedStatement statement = connection.prepareStatement(selectStatement)) {
            if (statementPreparer != null) {
                statementPreparer.accept(statement);
            }

            // execute the query and return the results
            final ResultSet resultSet = statement.executeQuery();
            return resultDeserializer.apply(resultSet);

        } catch (SQLException e) {
            throw rollback(e);
        }
    }

    /**
     * Saves the specified entity to the database
     * @param insertStatement the SQL INSERT INTO statement that will be executed to insert the object into the database
     * @param statementPreparer a {@link Consumer<PreparedStatement>} that allows the caller to populate ? any variables
     *                          that appear in the insertStatement
     * @param resultDeserializer an {@link SqlFunction<ResultSet, Optional<T>>} that is responsible for transforming the
     *                           ResultSet returned by the database query into an Optional<T> that the caller can use
     * @return a copy of the persisted entity that has its id attribute populated with the primary key of the persisted
     * record
     */
    public <T extends Entity> Optional<T> insert(String insertStatement, SqlConsumer<PreparedStatement> statementPreparer, SqlFunction<ResultSet, Optional<T>> resultDeserializer) throws SQLException {
        connection.setAutoCommit(false);

        // verify syntax
        if (!StringUtils.startsWith(insertStatement, "INSERT INTO")) {
            throw rollback(new SQLException("insertStatement must start with INSERT INTO"));
        }

        // populate the sql parameters
        try (PreparedStatement statement = connection.prepareStatement(insertStatement)) {
            statementPreparer.accept(statement);

            // execute the query
            final int numResults = statement.executeUpdate();
            if (numResults == 0) {
                throw new SQLException("Insert operation failed");
            }

            // get the returned object
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    // we can construct a simple query based on what we know about the table name and returned id
                    final String tableName = getTableNameFromInsertStatement(insertStatement);
                    final String selectStatement = String.format("SELECT * FROM %s WHERE id = ?", tableName);
                    final long id = generatedKeys.getLong(1);
                    return query(selectStatement, sp -> sp.setLong(1, id), resultDeserializer);
                } else {
                    throw new SQLException("Insert operation does not result in a generated primary key");
                }
            }
        } catch (SQLException e) {
            throw rollback(e);
        }
    }

    /**
     * Extracts tableName, assuming that insertStatement has format "INSERT INTO tableName ..."
     */
    private String getTableNameFromInsertStatement(String insertStatement) {
        final String prefixRemoved = insertStatement.toUpperCase()
                .replace("INSERT INTO ", "");
        return prefixRemoved.substring(0, prefixRemoved.indexOf(" ")).toLowerCase();
    }

    /**
     * Rolls back the current transaction, handling exceptions and returning the provided exception so that
     * it can be re-thrown as necessary.
     * If an exception occurs during the rollback operation, that exception is added to the returned stack trace.
     */
    private SQLException rollback(SQLException e) {
        try {
            logger.error("An SQLException occurred. Rolling back transaction", e);
            connection.rollback();
            return e;
        } catch (SQLException ex) {
            ex.setNextException(e);
            return new SQLException("Failed to rollback transaction", ex);
        }
    }

    // TODO add update(...) and delete(...) functions

    @Override
    public void close() {
        try {
            connection.commit();
        } catch (SQLException e) {
            logger.error("Failed to commit transaction", e);
        }
    }
}
