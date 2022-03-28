package ca.jonathanfritz.ofxcat.datastore.utils;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Closeable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class DatabaseTransaction implements Closeable {

    private final Connection connection;

    private static final Logger logger = LogManager.getLogger(DatabaseTransaction.class);

    public DatabaseTransaction(Connection connection) {
        this.connection = connection;
    }

    /**
     * Executes the specified selectStatement.
     * Use this overload if the SQL SELECT statement is not parameterized.
     * @param selectStatement the SQL SELECT statement to execute against the database
     * @param resultDeserializer an {@link SqlFunction<TransactionState, List<T>>} that is responsible for transforming
     *                           the ResultSet returned by the database query into a List<T> that the caller can use
     * @param <T> the type of object that the query is expected to return. Must extend {@link Entity}
     * @return a {@link List<T>} of results, or an empty list if the query does not return any results
     * @throws SQLException if something goes wrong. The current transaction will be rolled back before the exception
     * is thrown
     */
    public <T extends Entity> List<T> query(String selectStatement, SqlFunction<TransactionState, List<T>> resultDeserializer) throws SQLException {
        return query(selectStatement, null, resultDeserializer);
    }

    /**
     * Executes the specified selectStatement.
     * @param selectStatement the SQL SELECT statement to execute against the database
     * @param statementPreparer an {@link SqlConsumer<PreparedStatement>} that allows the caller to populate any ?
     *                          variables that appear in the selectStatement. Can be null if the selectStatement is not
     *                          parameterized
     * @param resultDeserializer an {@link SqlFunction<TransactionState, List<T>>} that is responsible for transforming
     *                           the ResultSet returned by the database query into a List<T> that the caller can use
     * @param <T> the type of object that the query is expected to return. Must extend {@link Entity}
     * @return a {@link List<T>} of results, or an empty list if the query does not return any results
     * @throws SQLException if something goes wrong. The current transaction will be rolled back before the exception
     * is thrown
     */
    public <T extends Entity> List<T> query(String selectStatement, SqlConsumer<PreparedStatement> statementPreparer, SqlFunction<TransactionState, List<T>> resultDeserializer) throws SQLException {
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
            return resultDeserializer.apply(new TransactionState(this, resultSet));

        } catch (SQLException e) {
            throw rollback(e);
        }
    }

    /**
     * Saves the specified entity to the database
     * @param insertStatement the SQL INSERT INTO statement that will be executed to insert the object into the database
     * @param statementPreparer a {@link Consumer<PreparedStatement>} that allows the caller to populate ? any variables
     *                          that appear in the insertStatement
     * @param resultDeserializer an {@link SqlFunction<TransactionState, Optional<T>>} that is responsible for
     *                           transforming the ResultSet returned by the database query into an Optional<T> that the
     *                           caller can use
     * @return a copy of the persisted entity that has its id attribute populated with the primary key of the persisted
     * record
     */
    public <T extends Entity> Optional<T> insert(String insertStatement, SqlConsumer<PreparedStatement> statementPreparer, SqlFunction<TransactionState, List<T>> resultDeserializer) throws SQLException {
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
                    final List<T> results = query(selectStatement, sp -> sp.setLong(1, id), resultDeserializer);
                    return getFirstResult(results);
                } else {
                    throw new SQLException("Insert operation does not result in a generated primary key");
                }
            }
        } catch (SQLException e) {
            throw rollback(e);
        }
    }

    public static <T extends Entity> Optional<T> getFirstResult(List<T> results) throws SQLException {
        if (results.isEmpty()) {
            return Optional.empty();
        } else if (results.size() == 1) {
            return Optional.of(results.get(0));
        } else {
            throw new SQLException(String.format("Expected a single result, but got %d", results.size()));
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

    @Override
    public void close() {
        try {
            connection.commit();
        } catch (SQLException e) {
            logger.error("Failed to commit transaction", e);
        }
    }
}
