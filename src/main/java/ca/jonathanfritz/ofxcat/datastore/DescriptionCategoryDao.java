package ca.jonathanfritz.ofxcat.datastore;

import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import ca.jonathanfritz.ofxcat.datastore.dto.DescriptionCategory;
import ca.jonathanfritz.ofxcat.datastore.utils.DatabaseTransaction;
import ca.jonathanfritz.ofxcat.datastore.utils.ResultSetDeserializer;
import ca.jonathanfritz.ofxcat.datastore.utils.SqlFunction;
import ca.jonathanfritz.ofxcat.datastore.utils.TransactionState;
import com.google.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DescriptionCategoryDao {

    private final Connection connection;
    private final CategoryDao categoryDao;
    private final SqlFunction<TransactionState, List<DescriptionCategory>> descriptionCategoryDeserializer;

    private static final Logger logger = LogManager.getLogger(DescriptionCategoryDao.class);

    @Inject
    public DescriptionCategoryDao(Connection connection, CategoryDao categoryDao) {
        this.connection = connection;
        this.categoryDao = categoryDao;
        this.descriptionCategoryDeserializer = new ResultSetDeserializer<>((transactionState, results) -> {
            final ResultSet resultSet = transactionState.getResultSet();
            final long id = resultSet.getLong("id");
            final String description = resultSet.getString("description");

            // we can take advantage of the currently active transaction to fetch the FK category
            // TODO: it isn't clear that this is the best approach - on one hand, we reduce code duplication and make
            //      CategoryDao responsible for access to the Category table. On the other, we're doing an extra SELECT
            //      for every row in the ResultSet, when we could have done an INNER JOIN instead. The problem with the
            //      latter approach is that an INSERT operation may or may not be able to return joined results. Test?
            final long categoryId = resultSet.getLong("category_id");
            final Category category = categoryDao.select(transactionState.getDatabaseTransaction(), categoryId)
                    .orElseThrow(() -> new SQLException(String.format("Category with id %d does not exist", categoryId)));

            results.add(new DescriptionCategory(id, description, category));
        });
    }

    public Optional<DescriptionCategory> insert(DescriptionCategory descriptionCategoryToInsert) {
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            return insert(t, descriptionCategoryToInsert);
        } catch (SQLException e) {
            logger.error("Failed to insert DescriptionCategory {}", descriptionCategoryToInsert, e);
            return Optional.empty();
        }
    }

    public Optional<DescriptionCategory> insert(DatabaseTransaction t, DescriptionCategory descriptionCategoryToInsert) throws SQLException {
        // make sure that the category in question exists
        logger.debug("Attempting to find existing Category with name {}", descriptionCategoryToInsert.getCategory().getName());
        final Category category = categoryDao.select(t, descriptionCategoryToInsert.getCategory().getName())
                .or(() -> {
                    logger.debug("Implicitly creating Category with name {}", descriptionCategoryToInsert.getCategory().getName());
                    return categoryDao.insert(t, descriptionCategoryToInsert.getCategory());
                }).get();

        // insert the DescriptionCategory object
        logger.debug("Attempting to insert DescriptionCategory {} with Category {}", descriptionCategoryToInsert, category);
        final String insertStatement = "INSERT INTO DescriptionCategory (description, category_id) VALUES (?, ?);";
        return t.insert(insertStatement, ps -> {
            ps.setString(1, descriptionCategoryToInsert.getDescription());
            ps.setLong(2, category.getId());
        }, descriptionCategoryDeserializer);
    }

    /**
     * Selects all {@link DescriptionCategory} objects from the database
     * @return a {@link List<DescriptionCategory>} containing the results, or an empty list if there are no results
     */
    public List<DescriptionCategory> selectAll() {
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            return selectAll(t);
        } catch (SQLException e) {
            logger.error("Failed to select all DescriptionCategory objects", e);
            return new ArrayList<>();
        }
    }

    public List<DescriptionCategory> selectAll(DatabaseTransaction t) throws SQLException {
        logger.debug("Attempting to select all DescriptionCategory objects");
        final String selectStatement = "SELECT * FROM DescriptionCategory;";
        return t.query(selectStatement, descriptionCategoryDeserializer);
    }

    /**
     * Finds the {@link DescriptionCategory} object that has both the specified description and {@link Category}
     * @param description the description to search for
     * @param category the Category to search for
     * @return an {@link Optional<DescriptionCategory>} containing the result, or {@link Optional#empty()} if not found
     */
    public Optional<DescriptionCategory> selectByDescriptionAndCategory(String description, Category category) {
        try(DatabaseTransaction t = new DatabaseTransaction(connection)) {
            return selectByDescriptionAndCategory(t, description, category);
        } catch (SQLException e) {
            logger.error("Failed to select DescriptionCategory with description {} and Category {}", description, category, e);
            return Optional.empty();
        }
    }

    public Optional<DescriptionCategory> selectByDescriptionAndCategory(DatabaseTransaction t, String description, Category category) throws SQLException {
        logger.debug("Attempting to select DescriptionCategory with description {} and Category {}", description, category);
        final String selectStatement = "SELECT dc.id AS id, dc.description AS description, dc.category_id AS category_id " +
                "FROM DescriptionCategory dc " +
                "INNER JOIN Category c ON dc.category_id = c.id " +
                "WHERE upper(dc.description) = ? AND upper(c.name) = ?;";
        final List<DescriptionCategory> results = t.query(selectStatement, ps -> {
            ps.setString(1, description.toUpperCase());
            ps.setString(2, category.getName().toUpperCase());
        }, descriptionCategoryDeserializer);
        return DatabaseTransaction.getFirstResult(results);
    }
}
