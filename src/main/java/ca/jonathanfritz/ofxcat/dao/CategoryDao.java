package ca.jonathanfritz.ofxcat.dao;

import ca.jonathanfritz.ofxcat.transactions.Category;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class CategoryDao {

    private final Connection connection;
    private final CategoryDeserializer categoryDeserializer;

    private static final Logger logger = LoggerFactory.getLogger(CategoryDao.class);

    @Inject
    public CategoryDao(Connection connection) {
        this.connection = connection;
        this.categoryDeserializer = new CategoryDeserializer();
    }

    public Optional<Category> select(long id) {
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            final String selectStatement = "SELECT * FROM Category WHERE id = ?";
            return t.query(selectStatement, ps -> ps.setLong(1, id), categoryDeserializer);
        } catch (SQLException e) {
            logger.error("Failed to query Category with id {}", id, e);
            return Optional.empty();
        }
    }

    // TODO: should this return an optional or throw an exception on failure?
    public Optional<Category> insert(Category categoryToInsert) {
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            final String insertStatement = "INSERT INTO Category (name) VALUES (?);";
            return t.insert(insertStatement, ps -> ps.setString(1, categoryToInsert.getName()), categoryDeserializer);
        } catch (SQLException e) {
            logger.error("Failed to insert category", e);
            return Optional.empty();
        }
    }

    private static class CategoryDeserializer implements SqlFunction<ResultSet, Optional<Category>> {
        @Override
        public Optional<Category> apply(ResultSet resultSet) throws SQLException {
            if (resultSet.next()) {
                final long id = resultSet.getLong("id");
                final String name = resultSet.getString("name");
                return Optional.of(new Category(id, name));
            } else {
                return Optional.empty();
            }
        }
    }
}
