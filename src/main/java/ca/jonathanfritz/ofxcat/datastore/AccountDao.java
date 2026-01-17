package ca.jonathanfritz.ofxcat.datastore;

import ca.jonathanfritz.ofxcat.datastore.dto.Account;
import ca.jonathanfritz.ofxcat.datastore.utils.DatabaseTransaction;
import ca.jonathanfritz.ofxcat.datastore.utils.ResultSetDeserializer;
import ca.jonathanfritz.ofxcat.datastore.utils.SqlFunction;
import ca.jonathanfritz.ofxcat.datastore.utils.TransactionState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AccountDao {

    private final Connection connection;
    private final SqlFunction<TransactionState, List<Account>> accountDeserializer;

    private static final Logger logger = LogManager.getLogger(AccountDao.class);

    @Inject
    public AccountDao(Connection connection) {
        this.connection = connection;
        this.accountDeserializer = new ResultSetDeserializer<>((transactionState, accounts) -> {
            final ResultSet resultSet = transactionState.getResultSet();
            final long id = resultSet.getLong("id");
            final String bankNumber = resultSet.getString("bank_number");
            final String accountNumber = resultSet.getString("account_number");
            final String accountType = resultSet.getString("account_type");
            final String name = resultSet.getString("name");

            accounts.add(Account.newBuilder()
                    .setId(id)
                    .setBankId(bankNumber)
                    .setAccountNumber(accountNumber)
                    .setAccountType(accountType)
                    .setName(name)
                    .build());
        });
    }

    /**
     * Gets all {@link Account}s from the database
     * @return a {@link List<Account>}, or an empty list if no accounts exist
     */
    public List<Account> select() {
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            logger.debug("Attempting to select all accounts from the database");
            final String selectStatement = "SELECT * FROM Account";
            return t.query(selectStatement, accountDeserializer);
        } catch (SQLException ex) {
            logger.error("Failed to select all accounts from the database", ex);
            return new ArrayList<>();
        }
    }

    /**
     * Gets the {@link Account} with the specified id from the database
     * @param id the primary key of the Account to fetch
     * @return an {@link Optional<Account>} containing the specified Account, or {@link Optional#empty()} if it does
     *      not exist
     */
    public Optional<Account> select(long id) {
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            return select(t, id);
        }
    }

    Optional<Account> select(DatabaseTransaction t, long id) {
        try {
            logger.debug("Attempting to query Account with id {}", id);
            final String selectStatement = "SELECT * FROM Account WHERE id = ?";
            final List<Account> results = t.query(selectStatement, ps -> ps.setLong(1, id), accountDeserializer);
            return DatabaseTransaction.getFirstResult(results);
        } catch (SQLException e) {
            logger.error("Failed to query Account with id {}", id, e);
            return Optional.empty();
        }
    }

    /**
     * Gets the {@link Account} with the specified account number from the database
     * @param accountNumber the account number (assigned by the institution) of the Account to get
     * @return an {@link Optional<Account>} containing the specified Account, or {@link Optional#empty()} if it does
     *      not exist
     */
    public Optional<Account> selectByAccountNumber(String accountNumber) {
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            logger.debug("Attempting to query Account with account number {}", accountNumber);
            final String selectStatement = "SELECT * FROM Account WHERE account_number = ?";
            final List<Account> results = t.query(selectStatement, ps -> ps.setString(1, accountNumber), accountDeserializer);
            return DatabaseTransaction.getFirstResult(results);
        } catch (SQLException e) {
            logger.error("Failed to query Account with account number {}", accountNumber, e);
            return Optional.empty();
        }
    }

    /**
     * Inserts the specified {@link Account} into the database
     * @param accountToInsert the Account to insert
     * @return an {@link Optional<Account>} containing the inserted Account, or {@link Optional#empty()} if the
     *      operation fails
     */
    public Optional<Account> insert(Account accountToInsert) {
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            return insert(t, accountToInsert);
        }
    }

    public Optional<Account> insert(DatabaseTransaction t, Account accountToInsert) {
        try {
            logger.debug("Attempting to insert Account {}", accountToInsert);
            final String insertStatement = "INSERT INTO Account (bank_number, account_number, account_type, name) VALUES (?, ?, ?, ?);";
            return t.insert(insertStatement, ps -> {
                ps.setString(1, accountToInsert.getBankId());
                ps.setString(2, accountToInsert.getAccountNumber());
                ps.setString(3, accountToInsert.getAccountType());
                ps.setString(4, accountToInsert.getName());
            }, accountDeserializer);
        } catch (SQLException e) {
            logger.error("Failed to insert Account {}", accountToInsert, e);
            return Optional.empty();
        }
    }
}
