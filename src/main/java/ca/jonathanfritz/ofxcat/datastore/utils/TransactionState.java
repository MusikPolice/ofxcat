package ca.jonathanfritz.ofxcat.datastore.utils;

import java.sql.ResultSet;

/**
 * Holds the current state of a transaction so that implementations of {@link TransactionStateConsumer} can access both
 * the open {@link DatabaseTransaction} and the {@link ResultSet} returned by the previous query
 */
public class TransactionState {
    private final DatabaseTransaction databaseTransaction;
    private final ResultSet resultSet;

    public TransactionState(DatabaseTransaction databaseTransaction, ResultSet resultSet) {
        this.databaseTransaction = databaseTransaction;
        this.resultSet = resultSet;
    }

    public DatabaseTransaction getDatabaseTransaction() {
        return databaseTransaction;
    }

    public ResultSet getResultSet() {
        return resultSet;
    }
}
