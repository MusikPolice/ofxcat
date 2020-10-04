package ca.jonathanfritz.ofxcat.datastore.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic deserializer for {@link ResultSet} objects that contain rows of type {@link Entity}
 * @param <T> the type of object to deserialize
 */
public class ResultSetDeserializer<T extends Entity> implements SqlFunction<TransactionState, List<T>> {

    private final TransactionStateConsumer<T> deserializer;

    private static final Logger logger = LoggerFactory.getLogger(ResultSetDeserializer.class);

    /**
     * Creates an instance of this deserializer
     * @param deserializer an {@link TransactionStateConsumer} that accepts the {@link ResultSet} to be deserialized as its first
     *                     parameter and a {@link List<T>} of results as its second. The position of the ResultSet is
     *                     managed by this object. The BiConsumer is only responsible for deserializing a single row and
     *                     adding the resulting object to the list.
     */
    public ResultSetDeserializer(TransactionStateConsumer<T> deserializer) {
        this.deserializer = deserializer;
    }

    @Override
    public List<T> apply(TransactionState transactionState) throws SQLException {
        final List<T> results = new ArrayList<>();
        final ResultSet resultSet = transactionState.getResultSet();
        while (resultSet.next()) {
            deserializer.accept(transactionState, results);
        }

        if (results.isEmpty()) {
            logger.debug("ResultSet contained zero results");
        } else {
            logger.debug("Found {} results", results.size());
        }
        return results;
    }
}
