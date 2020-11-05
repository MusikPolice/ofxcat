package ca.jonathanfritz.ofxcat.datastore;

import ca.jonathanfritz.ofxcat.datastore.utils.*;
import ca.jonathanfritz.ofxcat.transactions.Account;
import ca.jonathanfritz.ofxcat.transactions.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.transactions.Category;
import ca.jonathanfritz.ofxcat.transactions.Transaction;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.List;
import java.util.Optional;

public class CategorizedTransactionDao {

    private final Connection connection;
    private final SqlFunction<TransactionState, List<CategorizedTransaction>> categorizedTransactionDeserializer;

    private static final Logger logger = LoggerFactory.getLogger(CategorizedTransactionDao.class);

    @Inject
    public CategorizedTransactionDao(Connection connection, AccountDao accountDao, CategoryDao categoryDao) {
        this.connection = connection;
        this.categorizedTransactionDeserializer = new ResultSetDeserializer<>((transactionState, categorizedTransactions) -> {
            final ResultSet resultSet = transactionState.getResultSet();

            // TODO: some kind of cache for Account and Category objects would be a good idea...
            final long accountId = resultSet.getLong("account_id");
            final Account account = accountDao.select(transactionState.getDatabaseTransaction(), accountId)
                    .orElseThrow(() -> new SQLException(String.format("Account with id %d does not exist", accountId)));

            final long categoryId = resultSet.getLong("category_id");
            final Category category = categoryDao.select(transactionState.getDatabaseTransaction(), categoryId)
                    .orElseThrow(() -> new SQLException(String.format("Category with id %d does not exist", categoryId)));

            final Date date = resultSet.getDate("date");
            final float amount = resultSet.getFloat("amount");
            final String description = resultSet.getString("description");
            final String type = resultSet.getString("type");
            final float balance = resultSet.getFloat("balance");
            final Transaction transaction = Transaction.newBuilder()
                    .setAccount(account)
                    .setDate(date.toLocalDate())
                    .setAmount(amount)
                    .setDescription(description)
                    .setType(Transaction.TransactionType.valueOf(type))
                    .setBalance(balance)
                    .build();

            final long id = resultSet.getLong("id");
            categorizedTransactions.add(new CategorizedTransaction(id, transaction, category));
        });
    }

    /**
     * Gets the {@link CategorizedTransaction} with the specified id from the database
     * @param id the primary key of the CategorizedTransaction to fetch
     * @return an {@link Optional<CategorizedTransaction>} containing the specified CategorizedTransaction, or
     *      {@link Optional#empty()} if it does not exist
     */
    public Optional<CategorizedTransaction> select(long id) {
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            logger.debug("Attempting to query Transaction with id {}", id);
            final String selectStatement = "SELECT * FROM CategorizedTransaction WHERE id = ?";
            final List<CategorizedTransaction> results = t.query(selectStatement, ps -> ps.setLong(1, id), categorizedTransactionDeserializer);
            return DatabaseTransaction.getFirstResult(results);
        } catch (SQLException e) {
            logger.error("Failed to query Transaction with id {}", id, e);
            return Optional.empty();
        }
    }

    /**
     * Checks to see if the specified {@link Transaction} already exists in the database
     * @param t the {@link DatabaseTransaction} to perform this operation on
     * @param transaction the Transaction to look for
     * @return true if the specified Transaction already exists, false otherwise
     * @throws SQLException if something goes wrong
     */
    public boolean isDuplicate(DatabaseTransaction t, Transaction transaction) throws SQLException {
        logger.debug("Attempting to determine if {} is a duplicate", transaction);
        final String selectStatement = "SELECT * FROM CategorizedTransaction WHERE " +
                "date = ? AND " +
                "amount = ? AND " +
                "description = ? AND " +
                "account_id = ?;";

        final List<CategorizedTransaction> results = t.query(selectStatement, ps -> {
            ps.setDate(1, Date.valueOf(transaction.getDate()));
            ps.setFloat(2, transaction.getAmount());
            ps.setString(3, transaction.getDescription());
            ps.setLong(4, transaction.getAccount().getId());
        }, categorizedTransactionDeserializer);

        return !results.isEmpty();
    }

    /**
     * Inserts the specified {@link CategorizedTransaction} into the database
     * @param categorizedTransactionToInsert the CategorizedTransaction to insert
     * @return an {@link Optional<CategorizedTransaction>} containing the inserted CategorizedTransaction, or
     *      {@link Optional#empty()} if the operation fails
     */
    public Optional<CategorizedTransaction> insert(CategorizedTransaction categorizedTransactionToInsert) {
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            return insert(t, categorizedTransactionToInsert);
        } catch (SQLException e) {
            logger.error("Failed to insert CategorizedTransaction {}", categorizedTransactionToInsert, e);
            return Optional.empty();
        }
    }

    /**
     * Inserts the specified {@link CategorizedTransaction} into the database
     * @param t the {@link DatabaseTransaction} to perform this operation on
     * @param categorizedTransactionToInsert the CategorizedTransaction to insert
     * @return an {@link Optional<CategorizedTransaction>} containing the inserted CategorizedTransaction, or
     *      {@link Optional#empty()} if the operation fails
     * @throws SQLException if something goes wrong
     */
    public Optional<CategorizedTransaction> insert(DatabaseTransaction t, CategorizedTransaction categorizedTransactionToInsert) throws SQLException {
        logger.debug("Attempting to insert CategorizedTransaction {}", categorizedTransactionToInsert);
        final String insertStatement = "INSERT INTO CategorizedTransaction (type, date, amount, description, account_id, category_id, balance) VALUES (?, ?, ?, ?, ?, ?, ?);";
        return t.insert(insertStatement, ps -> {
            ps.setString(1, categorizedTransactionToInsert.getType().name());
            ps.setDate(2, Date.valueOf(categorizedTransactionToInsert.getDate()));
            ps.setFloat(3, categorizedTransactionToInsert.getAmount());
            ps.setString(4, categorizedTransactionToInsert.getDescription());
            ps.setLong(5, categorizedTransactionToInsert.getAccount().getId());
            ps.setLong(6, categorizedTransactionToInsert.getCategory().getId());
            ps.setFloat(7, categorizedTransactionToInsert.getBalance());
        }, categorizedTransactionDeserializer);
    }
}
