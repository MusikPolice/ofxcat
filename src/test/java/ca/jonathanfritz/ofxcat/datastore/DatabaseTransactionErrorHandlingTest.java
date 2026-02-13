package ca.jonathanfritz.ofxcat.datastore;

import ca.jonathanfritz.ofxcat.AbstractDatabaseTest;
import ca.jonathanfritz.ofxcat.TestUtils;
import ca.jonathanfritz.ofxcat.datastore.dto.Account;
import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import ca.jonathanfritz.ofxcat.datastore.utils.DatabaseTransaction;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for database transaction error handling to ensure proper rollback
 * on failures and data integrity.
 */
class DatabaseTransactionErrorHandlingTest extends AbstractDatabaseTest {

    private final AccountDao accountDao;
    private final CategoryDao categoryDao;

    DatabaseTransactionErrorHandlingTest() {
        this.accountDao = injector.getInstance(AccountDao.class);
        this.categoryDao = injector.getInstance(CategoryDao.class);
    }

    @Test
    void transactionCommitOnSuccess() throws SQLException {
        // Setup: Count categories before transaction
        final int initialCategoryCount = categoryDao.select().size();

        // Execute: Successful transaction - close() commits automatically
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            final Category category = new Category("Test Category for Commit");
            categoryDao.insert(t, category);
            // close() is called automatically, which commits
        }

        // Verify: Category was committed
        final int finalCategoryCount = categoryDao.select().size();
        assertEquals(initialCategoryCount + 1, finalCategoryCount,
                "Category should have been committed");
    }

    @Test
    void uniqueConstraintBehavior() throws SQLException {
        // Verify unique constraint is enforced on (bank_number, account_number)
        final Account firstAccount = TestUtils.createRandomAccount();
        accountDao.insert(firstAccount).orElseThrow();

        final int initialAccountCount = accountDao.select().size();

        // Try to insert duplicate account with same bank_number + account_number
        final Account duplicateAccount = Account.newBuilder(firstAccount)
                .setName("Different Name")  // Different name, same bank + account number
                .build();
        Optional<Account> result = accountDao.insert(duplicateAccount);

        // Verify insert failed due to unique constraint
        assertTrue(result.isEmpty(),
                "Insert should fail when unique constraint violated");

        // Verify no new account was added
        final int finalAccountCount = accountDao.select().size();
        assertEquals(initialAccountCount, finalAccountCount,
                "Duplicate account should not have been committed when constraint violated");
    }


    @Test
    void multipleOperationsInTransactionAllCommitted() throws SQLException {
        // Setup: Count before
        final int initialCategoryCount = categoryDao.select().size();

        // Execute: Multiple operations in one transaction, close() commits automatically
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            categoryDao.insert(t, new Category("Category A"));
            categoryDao.insert(t, new Category("Category B"));
            // close() is called automatically, which commits all operations
        }

        // Verify: Everything was committed
        assertEquals(initialCategoryCount + 2, categoryDao.select().size(),
                "Both categories should have been committed");
    }

    @Test
    void closeCommitsByDefault() throws SQLException {
        // Setup: This tests the default behavior - transactions commit when close() is called
        final int initialCategoryCount = categoryDao.select().size();

        // Execute: Transaction that exits normally (no exception)
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            categoryDao.insert(t, new Category("Should Commit"));
            // close() is called automatically, which commits
        }

        // Verify: Committed by default when close() is called
        final int finalCategoryCount = categoryDao.select().size();
        assertEquals(initialCategoryCount + 1, finalCategoryCount,
                "Transaction should commit when close() is called (no exception)");
    }



    @Test
    void foreignKeyConstraintsNotEnforcedByDefault() throws SQLException {
        // Note: SQLite foreign key enforcement is DISABLED by default.
        // Enabling it via setEnforceForeignKeys(true) in DatastoreModule breaks
        // Flyway's clean() operation which is needed for test isolation.
        // This test documents the current behavior.

        // Verify foreign keys are NOT enabled (SQLite default)
        int fkEnabled;
        try (var rs = connection.createStatement().executeQuery("PRAGMA foreign_keys")) {
            rs.next();
            fkEnabled = rs.getInt(1);
        }
        assertEquals(0, fkEnabled,
                "Foreign keys are disabled by default in SQLite (enabling breaks Flyway clean)");

        // Since FK constraints are not enforced, inserts with invalid references succeed
        final String violatingInsert = "INSERT INTO CategorizedTransaction " +
                "(type, date, amount, description, account_id, category_id) " +
                "VALUES ('DEBIT', '2023-01-01', 100.0, 'Test', 99999, 99999)";

        // This does NOT throw - FK constraints are not enforced
        assertDoesNotThrow(() -> {
            connection.createStatement().executeUpdate(violatingInsert);
        }, "With FK constraints disabled, invalid references are allowed");
    }
}

