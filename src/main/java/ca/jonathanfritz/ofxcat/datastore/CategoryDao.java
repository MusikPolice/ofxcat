package ca.jonathanfritz.ofxcat.datastore;

import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import ca.jonathanfritz.ofxcat.datastore.utils.DatabaseTransaction;
import ca.jonathanfritz.ofxcat.datastore.utils.ResultSetDeserializer;
import ca.jonathanfritz.ofxcat.datastore.utils.SqlFunction;
import ca.jonathanfritz.ofxcat.datastore.utils.TransactionState;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CategoryDao {

    private final Connection connection;
    private final SqlFunction<TransactionState, List<Category>> categoryDeserializer;

    private static final Logger logger = LogManager.getLogger(CategoryDao.class);

    @Inject
    public CategoryDao(Connection connection) {
        this.connection = connection;
        this.categoryDeserializer = new ResultSetDeserializer<>((transactionState, categories) -> {
            final ResultSet resultSet = transactionState.getResultSet();
            final long id = resultSet.getLong("id");
            final String name = resultSet.getString("name");
            categories.add(new Category(id, name));
        });
    }

    /**
     * Gets the {@link Category} with the specified id from the database
     * @param id the primary key of the Category to fetch
     * @return an {@link Optional<Category>} containing the specified Category, or {@link Optional#empty()} if it does
     *      not exist
     */
    public Optional<Category> select(long id) {
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            return select(t, id);
        }
    }

    Optional<Category> select(DatabaseTransaction t, long id) {
        try {
            logger.debug("Attempting to query Category with id {}", id);
            final String selectStatement = "SELECT * FROM Category WHERE id = ?";
            final List<Category> results = t.query(selectStatement, ps -> ps.setLong(1, id), categoryDeserializer);
            return DatabaseTransaction.getFirstResult(results);
        } catch (SQLException e) {
            logger.error("Failed to query Category with id {}", id, e);
            return Optional.empty();
        }
    }

    /**
     * Gets the {@link Category} with the specified name from the database. This query is case-insensitive.
     * @param name the name of the Category to fetch
     * @return an {@link Optional<Category>} containing the specified Category, or {@link Optional#empty()} if it does
     *      not exist
     */
    public Optional<Category> select(String name) {
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            return select(t, name);
        }
    }

    Optional<Category> select(DatabaseTransaction t, String name) {
        try {
            logger.debug("Attempting to query Category with name {}", name);
            final String selectStatement = "SELECT * FROM Category WHERE upper(name) = ?";
            final List<Category> results =
                    t.query(selectStatement, ps -> ps.setString(1, name.toUpperCase()), categoryDeserializer);
            return DatabaseTransaction.getFirstResult(results);
        } catch (SQLException e) {
            logger.error("Failed to query Category with name {}", name, e);
            return Optional.empty();
        }
    }

    /**
     * Selects all {@link Category} objects from the database
     * @return a {@link List<Category>} containing the results, or an empty list if there are no results
     */
    public List<Category> select() {
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            logger.debug("Attempting to select all Category objects");
            final String selectStatement = "SELECT * FROM Category ORDER BY name ASC;";
            return t.query(selectStatement, categoryDeserializer);
        } catch (SQLException e) {
            logger.error("Failed to select all Category objects", e);
            return new ArrayList<>();
        }
    }

    /**
     * Inserts the specified categoryToInsert into the database
     * @param categoryToInsert the {@link Category} to insert
     * @return an {@link Optional<Category>} containing the inserted Category, or {@link Optional#empty()} if the
     *      operation fails
     */
    public Optional<Category> insert(Category categoryToInsert) {
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            return insert(t, categoryToInsert);
        }
    }

    public Optional<Category> insert(DatabaseTransaction t, Category categoryToInsert) {
        try {
            logger.debug("Attempting to insert Category {}", categoryToInsert);
            final String insertStatement = "INSERT INTO Category (name) VALUES (?);";
            return t.insert(insertStatement, ps -> ps.setString(1, categoryToInsert.getName()), categoryDeserializer);
        } catch (SQLException e) {
            logger.error("Failed to insert Category {}", categoryToInsert, e);
            return Optional.empty();
        }
    }

    /**
     * Deletes the {@link Category} with the specified id from the database
     * @param categoryId the primary key of the Category to delete
     * @return true if the Category was deleted, false if it did not exist
     */
    public boolean delete(long categoryId) {
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            return delete(t, categoryId);
        } catch (SQLException e) {
            logger.error("Failed to delete Category with id {}", categoryId, e);
            return false;
        }
    }

    boolean delete(DatabaseTransaction t, long categoryId) throws SQLException {
        logger.debug("Attempting to delete Category with id {}", categoryId);
        final String deleteStatement = "DELETE FROM Category WHERE id = ?";
        int rowsDeleted = t.execute(deleteStatement, ps -> ps.setLong(1, categoryId));
        return rowsDeleted > 0;
    }

    /**
     * Gets the {@link Category} with the specified name from the database, creating it if it doesn't exist.
     * This is useful when keyword rules reference categories that may not yet exist in the database.
     *
     * @param name the name of the Category to get or create
     * @return an {@link Optional<Category>} containing the Category, or {@link Optional#empty()} if the operation fails
     */
    public Optional<Category> getOrCreate(String name) {
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            return getOrCreate(t, name);
        }
    }

    /**
     * Gets the {@link Category} with the specified name from the database, creating it if it doesn't exist.
     * Uses the provided transaction for atomic operation.
     *
     * @param t the database transaction to use
     * @param name the name of the Category to get or create
     * @return an {@link Optional<Category>} containing the Category, or {@link Optional#empty()} if the operation fails
     */
    public Optional<Category> getOrCreate(DatabaseTransaction t, String name) {
        // First, try to find the existing category
        Optional<Category> existing = select(t, name);
        if (existing.isPresent()) {
            logger.debug("Found existing Category with name {}", name);
            return existing;
        }

        // If not found, create it
        logger.info("Creating new Category with name {}", name);
        return insert(t, new Category(name));
    }
}
