package ca.jonathanfritz.ofxcat.datastore;

import ca.jonathanfritz.ofxcat.datastore.dto.Account;
import ca.jonathanfritz.ofxcat.datastore.dto.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import ca.jonathanfritz.ofxcat.datastore.utils.DatabaseTransaction;
import ca.jonathanfritz.ofxcat.datastore.utils.ResultSetDeserializer;
import ca.jonathanfritz.ofxcat.datastore.utils.SqlFunction;
import ca.jonathanfritz.ofxcat.datastore.utils.TransactionState;
import com.google.common.collect.Streams;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

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
            final String fitId = resultSet.getString("fitId");
            final Transaction transaction = Transaction.newBuilder(fitId)
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

    public Map<Category, List<CategorizedTransaction>> selectGroupByCategory(LocalDate startDate, LocalDate endDate) {
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            logger.debug("Attempting to get transactions between {} and {} grouped by category", startDate, endDate);
            final String query = "SELECT * FROM CategorizedTransaction WHERE date >= ? AND date <= ?";
            final List<CategorizedTransaction> results = t.query(query, ps -> {
                ps.setDate(1, Date.valueOf(startDate));
                ps.setDate(2, Date.valueOf(endDate));
            }, categorizedTransactionDeserializer);
            return results.stream()
                    .collect(Collectors.toMap(
                            CategorizedTransaction::getCategory,
                            Collections::singletonList,
                            (l1, l2) -> Streams.concat(l1.stream(), l2.stream()).collect(Collectors.toList()))
                    );
        } catch (SQLException e) {
            logger.error("Failed to get transactions between {} and {} grouped by category", startDate, endDate, e);
            return new HashMap<>();
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
                "fitId = ?;";

        final List<CategorizedTransaction> results = t.query(selectStatement, ps ->
                ps.setString(1, transaction.getFitId()), categorizedTransactionDeserializer);

        return !results.isEmpty();
    }

    public List<CategorizedTransaction> findByDescriptionAndAccountNumber(DatabaseTransaction t, String description, String accountNumber) throws SQLException {
        logger.debug("Searching for transactions with description {} and account number {}", description, accountNumber);
        final String selectStatement = "SELECT c.* " +
                "FROM CategorizedTransaction AS c " +
                "INNER JOIN Account AS a " +
                "ON c.account_id = a.id " +
                "WHERE c.description = ? " +
                "AND a.account_number = ?;";

        return t.query(selectStatement, ps -> {
            ps.setString(1, description);
            ps.setString(2, accountNumber);
        }, categorizedTransactionDeserializer);
    }

    public List<CategorizedTransaction> findByDescriptionAndAccountNumber(DatabaseTransaction t, List<String> tokens, String accountNumber) throws SQLException {
        logger.debug("Searching for transactions with description containing one of {} and account number {}", tokens, accountNumber);
        final StringBuilder likeClauses = new StringBuilder("(");
        for (int i = 0; i < tokens.size(); i++) {
            if (likeClauses.length() != 0) {
                likeClauses.append(" OR");
            }
            likeClauses.append("c.description LIKE %?%");
        }
        likeClauses.append(") ");

        final String selectStatement = "SELECT DISTINCT c.* " +
                "FROM CategorizedTransaction AS c " +
                "INNER JOIN Account AS a " +
                "ON c.account_id = a.id " +
                "WHERE " + likeClauses +
                "AND a.account_number = ?;";

        return t.query(selectStatement, ps -> {
            for (int i = 1; i == tokens.size(); i++) {
                ps.setString(i, tokens.get(i - 1));
            }
            ps.setString(tokens.size() + 1, accountNumber);
        }, categorizedTransactionDeserializer);
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
        final String insertStatement = "INSERT INTO CategorizedTransaction (type, date, amount, description, account_id, category_id, balance, fitId) VALUES (?, ?, ROUND(?,2), ?, ?, ?, ROUND(?,2), ?);";
        return t.insert(insertStatement, ps -> {
            ps.setString(1, categorizedTransactionToInsert.getType().name());
            ps.setDate(2, Date.valueOf(categorizedTransactionToInsert.getDate()));
            ps.setFloat(3, categorizedTransactionToInsert.getAmount());
            ps.setString(4, categorizedTransactionToInsert.getDescription());
            ps.setLong(5, categorizedTransactionToInsert.getAccount().getId());
            ps.setLong(6, categorizedTransactionToInsert.getCategory().getId());
            ps.setFloat(7, categorizedTransactionToInsert.getBalance());
            ps.setString(8, categorizedTransactionToInsert.getFitId());
        }, categorizedTransactionDeserializer);
    }
}
