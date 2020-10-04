package ca.jonathanfritz.ofxcat.datastore.utils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Consumes one row of the {@link ResultSet} inside of the specified {@link TransactionState} and puts the deserialized
 * object into a {@link List<T>} of results
 * @param <T> the type of object to deserialize
 */
@FunctionalInterface
public interface TransactionStateConsumer<T extends Entity> {
    void accept(TransactionState transactionState, List<T> results) throws SQLException;
}
