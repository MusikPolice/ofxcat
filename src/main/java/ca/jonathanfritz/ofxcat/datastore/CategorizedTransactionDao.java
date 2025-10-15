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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CategorizedTransactionDao {

    private final Connection connection;
    private final SqlFunction<TransactionState, List<CategorizedTransaction>> categorizedTransactionDeserializer;

    private static final Logger logger = LogManager.getLogger(CategorizedTransactionDao.class);

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
            logger.debug("Attempting to query CategorizedTransaction with id {}", id);
            final String selectStatement = "SELECT * FROM CategorizedTransaction WHERE id = ?";
            final List<CategorizedTransaction> results = t.query(selectStatement, ps -> ps.setLong(1, id), categorizedTransactionDeserializer);
            return DatabaseTransaction.getFirstResult(results);
        } catch (SQLException e) {
            logger.error("Failed to query CategorizedTransaction with id {}", id, e);
            return Optional.empty();
        }
    }

    public Optional<CategorizedTransaction> selectByFitId(String fitId) {
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            logger.debug("Attempting to query CategorizedTransaction with fitId {}", fitId);
            final String selectStatement = "SELECT * FROM CategorizedTransaction WHERE fitId = ?";
            final List<CategorizedTransaction> results = t.query(selectStatement, ps -> ps.setString(1, fitId), categorizedTransactionDeserializer);
            return DatabaseTransaction.getFirstResult(results);
        } catch (SQLException e) {
            logger.error("Failed to query CategorizedTransaction with fitId {}", fitId, e);
            return Optional.empty();
        }
    }

    public Map<Category, List<CategorizedTransaction>> selectGroupByCategory(LocalDate startDate, LocalDate endDate) {
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            logger.debug("Attempting to get CategorizedTransactions between {} and {} grouped by category", startDate, endDate);
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
            logger.error("Failed to get CategorizedTransactions between {} and {} grouped by category", startDate, endDate, e);
            return new HashMap<>();
        }
    }

    /**
     * Finds all categorized transactions that belong to the specified category, and that occurred between the specified dates
     * @param category the category that returned transactions belong to
     * @param startDate the earliest date on which a returned transaction can occur, inclusive
     * @param endDate the latest date on which a returned transaction can occur, inclusive
     * @return a list of matching {@link CategorizedTransaction}, sorted by date ascending
     */
    public List<CategorizedTransaction> selectByCategory(final Category category, final LocalDate startDate, final LocalDate endDate) {
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            logger.debug("Attempting to get CategorizedTransactions in Category {} that occurred between {} and {}", category, startDate, endDate);
            final String query = "SELECT * FROM CategorizedTransaction " +
                    "WHERE category_id = ? AND date >= ? AND date <= ? " +
                    "ORDER BY date ASC";
            return t.query(query, ps -> {
                ps.setLong(1, category.getId());
                ps.setDate(2, Date.valueOf(startDate));
                ps.setDate(3, Date.valueOf(endDate));
            }, categorizedTransactionDeserializer);
        } catch (SQLException e) {
            logger.error("Failed to get CategorizedTransactions in Category {} that occurred between {} and {}", category, startDate, endDate);
            return Collections.emptyList();
        }
    }

    /**
     * Checks to see if a transaction exists in the database that has the same fitId as the specified {@link Transaction}
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

    public List<CategorizedTransaction> findByDescription(DatabaseTransaction t, String description) throws SQLException {
        logger.debug("Searching for CategorizedTransactions with description {}", description);
        final String selectStatement = "SELECT * " +
                "FROM CategorizedTransaction " +
                "WHERE description = ?;";

        return t.query(selectStatement, ps -> ps.setString(1, description), categorizedTransactionDeserializer);
    }

    public List<CategorizedTransaction> findByDescription(DatabaseTransaction t, List<String> tokens) throws SQLException {
        logger.debug("Searching for CategorizedTransactions with description containing one of {}", tokens);
        final StringBuilder likeClauses = new StringBuilder("(");
        for (int i = 0; i < tokens.size(); i++) {
            if (likeClauses.length() > 1) {
                likeClauses.append(" OR ");
            }
            likeClauses.append("description LIKE ?");
        }
        likeClauses.append(") ");

        final String selectStatement = "SELECT DISTINCT * " +
                "FROM CategorizedTransaction " +
                "WHERE " + likeClauses + ";";

        return t.query(selectStatement, ps -> {
            for (int i = 1; i <= tokens.size(); i++) {
                ps.setString(i, "%" + tokens.get(i - 1).toUpperCase(Locale.ROOT) + "%");
            }
        }, categorizedTransactionDeserializer);
    }

    /**
     * Finds all TRANSFER type transactions that are not a source or sink in the Transfer table.
     */
    public Map<Account, List<Transaction>> findUnlinkedTransfers() {
        logger.debug("Attempting to find CategorizedTransactions with Category TRANSFER that have not been used as the source or sink of a Transfer");
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            final String selectStatement = "SELECT DISTINCT * " +
                    "FROM CategorizedTransaction " +
                    "WHERE category_id = 1 " +
                    "AND id NOT IN (" +
                    "  SELECT source_id FROM Transfer UNION ALL SELECT sink_id FROM Transfer " +
                    ");";
            return t.query(selectStatement, categorizedTransactionDeserializer).stream()
                    .collect(Collectors.toMap(
                            Transaction::getAccount,
                            categorizedTransaction -> List.of(categorizedTransaction.getTransaction()),
                            (list1, list2) -> Stream.concat(list1.stream(), list2.stream()).collect(Collectors.toList())
                        ));
        } catch (SQLException ex) {
            logger.error("Failed to find CategorizedTransactions with Category TRANSFER that have not been used as the source or sink of a Transfer");
            return new HashMap<>();
        }
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
