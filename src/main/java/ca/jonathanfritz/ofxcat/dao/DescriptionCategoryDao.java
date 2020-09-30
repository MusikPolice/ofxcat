package ca.jonathanfritz.ofxcat.dao;

import ca.jonathanfritz.ofxcat.transactions.Category;
import ca.jonathanfritz.ofxcat.transactions.DescriptionCategory;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DescriptionCategoryDao {

    private final Connection connection;
    private final CategoryDao categoryDao;

    private static final Logger logger = LoggerFactory.getLogger(DescriptionCategoryDao.class);

    @Inject
    public DescriptionCategoryDao(Connection connection, CategoryDao categoryDao) {
        this.connection = connection;
        this.categoryDao = categoryDao;
    }

    public Optional<DescriptionCategory> insert(DescriptionCategory descriptionCategoryToInsert) {
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            // make sure that the category in question exists
            logger.debug("Attempting to find existing Category with name {}", descriptionCategoryToInsert.getCategory().getName());
            final Category category = categoryDao.selectByName(t, descriptionCategoryToInsert.getCategory().getName())
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
            }, new DescriptionCategoryDeserializer(t));
        } catch (SQLException e) {
            logger.error("Failed to insert DescriptionCategory {}", descriptionCategoryToInsert, e);
            return Optional.empty();
        }
    }

    /**
     * Selects all {@link DescriptionCategory} objects from the database
     * @return a {@link List<DescriptionCategory>} containing the results, or an empty list if there are no results
     */
    public List<DescriptionCategory> selectAll() {
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            logger.debug("Attempting to select all DescriptionCategory objects");
            final String selectStatement = "SELECT * FROM DescriptionCategory;";
            return t.query(selectStatement, new DescriptionCategoryDeserializer(t));
        } catch (SQLException e) {
            logger.error("Failed to select all DescriptionCategory objects", e);
            return new ArrayList<>();
        }
    }

    /**
     * Finds the {@link DescriptionCategory} object that has both the specified description and {@link Category}
     * @param description the description to search for
     * @param category the Category to search for
     * @return an {@link Optional<DescriptionCategory>} containing the result, or {@link Optional#empty()} if not found
     */
    public Optional<DescriptionCategory> selectByDescriptionAndCategory(String description, Category category) {
        try(DatabaseTransaction t = new DatabaseTransaction(connection)) {
            logger.debug("Attempting to select DescriptionCategory with description {} and Category {}", description, category);
            final String selectStatement = "SELECT dc.id AS id, dc.description AS description, dc.category_id AS category_id " +
                    "FROM DescriptionCategory dc " +
                    "INNER JOIN Category c ON dc.category_id = c.id " +
                    "WHERE upper(dc.description) = ? AND upper(c.name) = ?;";
            final List<DescriptionCategory> results = t.query(selectStatement, ps -> {
                ps.setString(1, description.toUpperCase());
                ps.setString(2, category.getName().toUpperCase());
            }, new DescriptionCategoryDeserializer(t));
            return DatabaseTransaction.getFirstResult(results);
        } catch (SQLException e) {
            logger.error("Failed to select DescriptionCategory with description {} and Category {}", description, category, e);
            return Optional.empty();
        }
    }

    private class DescriptionCategoryDeserializer implements SqlFunction<ResultSet, List<DescriptionCategory>> {

        private final DatabaseTransaction t;

        private DescriptionCategoryDeserializer(DatabaseTransaction t) {
            this.t = t;
        }

        @Override
        public List<DescriptionCategory> apply(ResultSet resultSet) throws SQLException {
            final List<DescriptionCategory> results = new ArrayList<>();
            while (resultSet.next()) {
                final long id = resultSet.getLong("id");
                final String description = resultSet.getString("description");

                // we can take advantage of the currently active transaction to fetch the FK category
                // TODO: we can probably do an inner join here to make this more efficient
                final long categoryId = resultSet.getLong("category_id");
                final Category category = categoryDao.select(t, categoryId)
                        .orElseThrow(() -> new SQLException(String.format("Category with id %d does not exist", categoryId)));

                results.add(new DescriptionCategory(id, description, category));
            }
            return results;
        }
    }
}
