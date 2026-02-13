package ca.jonathanfritz.ofxcat;

import ca.jonathanfritz.ofxcat.datastore.AccountDao;
import ca.jonathanfritz.ofxcat.datastore.CategoryDao;
import ca.jonathanfritz.ofxcat.datastore.CategorizedTransactionDao;
import ca.jonathanfritz.ofxcat.datastore.dto.Account;
import ca.jonathanfritz.ofxcat.datastore.dto.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import ca.jonathanfritz.ofxcat.datastore.utils.DatabaseTransaction;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Adversarial input tests to verify the application handles malicious or edge-case
 * inputs safely without crashes, data corruption, or security vulnerabilities.
 */
class AdversarialInputTest extends AbstractDatabaseTest {

    private final AccountDao accountDao;
    private final CategoryDao categoryDao;
    private final CategorizedTransactionDao transactionDao;

    AdversarialInputTest() {
        this.accountDao = injector.getInstance(AccountDao.class);
        this.categoryDao = injector.getInstance(CategoryDao.class);
        this.transactionDao = injector.getInstance(CategorizedTransactionDao.class);
    }

    // ==================== SQL Injection Tests ====================

    @Test
    void sqlInjectionInCategoryName() throws SQLException {
        // Setup: Category name with SQL injection attempt
        String maliciousName = "Test'; DROP TABLE Category; --";

        Category insertedCategory;
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            Category category = new Category(maliciousName);
            insertedCategory = categoryDao.insert(t, category).orElse(null);
        }

        // Verify: Category was inserted safely
        // Note: Category names are uppercased by the system
        assertNotNull(insertedCategory, "Category with SQL injection should be inserted");
        assertEquals(maliciousName.toUpperCase(), insertedCategory.getName(),
                "Category name should be stored literally (uppercased), not executed as SQL");

        // Verify table wasn't dropped by attempting another operation
        assertDoesNotThrow(() -> categoryDao.select(),
                "Category table should still exist after SQL injection attempt");
    }

    @Test
    void sqlInjectionInAccountName() {
        // Setup: Account with SQL injection in name
        Account account = Account.newBuilder()
                .setBankId("BANK001")
                .setAccountNumber("12345")
                .setAccountType("CHECKING")
                .setName("'; DELETE FROM Account; --")
                .build();

        // Execute
        var result = accountDao.insert(account);

        // Verify: Account was inserted safely
        assertTrue(result.isPresent(), "Account should be inserted despite SQL injection characters");

        // Verify table wasn't affected
        var accounts = accountDao.select();
        assertFalse(accounts.isEmpty(), "Account table should still have data");
    }

    @Test
    void sqlInjectionInTransactionDescription() throws SQLException {
        // Setup: Create account and category first
        Account account = accountDao.insert(TestUtils.createRandomAccount()).orElseThrow();
        Category category;
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            category = categoryDao.insert(t, new Category("Test Category")).orElseThrow();
        }

        // Create transaction with SQL injection in description
        String maliciousDescription = "Payment'; UPDATE CategorizedTransaction SET amount=0; --";
        Transaction transaction = Transaction.newBuilder(UUID.randomUUID().toString())
                .setAccount(account)
                .setAmount(-50.0f)
                .setDate(LocalDate.now())
                .setDescription(maliciousDescription)
                .setType(Transaction.TransactionType.DEBIT)
                .build();

        CategorizedTransaction categorized = new CategorizedTransaction(transaction, category);

        // Execute
        var result = transactionDao.insert(categorized);

        // Verify: Transaction stored safely
        assertTrue(result.isPresent(), "Transaction should be inserted");
        assertEquals(maliciousDescription, result.get().getTransaction().getDescription(),
                "Description should be stored literally, not executed as SQL");
    }

    // ==================== Unicode Edge Cases ====================

    @Test
    void unicodeNormalizationInCategoryName() throws SQLException {
        // Setup: Unicode string with accented character
        String unicodeName = "café";

        Category insertedCategory;
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            insertedCategory = categoryDao.insert(t, new Category(unicodeName)).orElse(null);
        }

        // Verify: Unicode category is stored correctly
        // Note: Category names are uppercased by the system
        assertNotNull(insertedCategory, "Unicode category should be inserted");
        assertEquals(unicodeName.toUpperCase(), insertedCategory.getName(),
                "Unicode characters should be preserved (uppercased)");
    }

    @Test
    void rightToLeftTextInDescription() throws SQLException {
        // Setup: Hebrew/Arabic text (RTL)
        Account account = accountDao.insert(TestUtils.createRandomAccount()).orElseThrow();
        Category category;
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            category = categoryDao.insert(t, new Category("RTL Test")).orElseThrow();
        }

        String rtlDescription = "תשלום עבור מוצר"; // Hebrew text
        Transaction transaction = Transaction.newBuilder(UUID.randomUUID().toString())
                .setAccount(account)
                .setAmount(-25.0f)
                .setDate(LocalDate.now())
                .setDescription(rtlDescription)
                .setType(Transaction.TransactionType.DEBIT)
                .build();

        CategorizedTransaction categorized = new CategorizedTransaction(transaction, category);

        // Execute
        var result = transactionDao.insert(categorized);

        // Verify: RTL text is stored correctly
        assertTrue(result.isPresent());
        assertEquals(rtlDescription, result.get().getTransaction().getDescription());
    }

    @Test
    void zeroWidthCharactersInDescription() throws SQLException {
        // Setup: Text with zero-width characters
        Account account = accountDao.insert(TestUtils.createRandomAccount()).orElseThrow();
        Category category;
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            category = categoryDao.insert(t, new Category("ZeroWidth Test")).orElseThrow();
        }

        // Zero-width space (U+200B) and zero-width non-joiner (U+200C)
        String descriptionWithZeroWidth = "Normal\u200BText\u200CWith\u200DInvisible";
        Transaction transaction = Transaction.newBuilder(UUID.randomUUID().toString())
                .setAccount(account)
                .setAmount(-10.0f)
                .setDate(LocalDate.now())
                .setDescription(descriptionWithZeroWidth)
                .setType(Transaction.TransactionType.DEBIT)
                .build();

        CategorizedTransaction categorized = new CategorizedTransaction(transaction, category);

        // Execute
        var result = transactionDao.insert(categorized);

        // Verify: Zero-width characters are preserved
        assertTrue(result.isPresent());
        assertEquals(descriptionWithZeroWidth, result.get().getTransaction().getDescription());
    }

    @Test
    void emojiInCategoryName() throws SQLException {
        // Setup: Category with emoji
        String emojiCategory = "Food 🍕🍔🌮";

        Category insertedCategory;
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            insertedCategory = categoryDao.insert(t, new Category(emojiCategory)).orElse(null);
        }

        // Verify: Emoji category is stored correctly
        // Note: Category names are uppercased, emoji are preserved
        assertNotNull(insertedCategory, "Category with emoji should be inserted");
        assertEquals(emojiCategory.toUpperCase(), insertedCategory.getName(),
                "Emoji characters should be preserved in category name (text uppercased)");
    }

    // ==================== Extreme Numeric Values ====================

    @Test
    void veryLargeTransactionAmount() throws SQLException {
        // Setup: Transaction with very large amount
        Account account = accountDao.insert(TestUtils.createRandomAccount()).orElseThrow();
        Category category;
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            category = categoryDao.insert(t, new Category("Large Amount")).orElseThrow();
        }

        float largeAmount = Float.MAX_VALUE / 2; // Very large but not overflow
        Transaction transaction = Transaction.newBuilder(UUID.randomUUID().toString())
                .setAccount(account)
                .setAmount(largeAmount)
                .setDate(LocalDate.now())
                .setDescription("Very large deposit")
                .setType(Transaction.TransactionType.CREDIT)
                .build();

        CategorizedTransaction categorized = new CategorizedTransaction(transaction, category);

        // Execute
        var result = transactionDao.insert(categorized);

        // Verify: Large amount is handled
        assertTrue(result.isPresent());
        assertEquals(largeAmount, result.get().getTransaction().getAmount(), 1.0f);
    }

    @Test
    void verySmallTransactionAmount() throws SQLException {
        // Setup: Transaction with very small amount
        Account account = accountDao.insert(TestUtils.createRandomAccount()).orElseThrow();
        Category category;
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            category = categoryDao.insert(t, new Category("Small Amount")).orElseThrow();
        }

        // Use 0.01 (one cent) - the smallest practical transaction amount
        // Note: Sub-cent amounts (like 0.001) may be truncated by the database
        float smallAmount = 0.01f;
        Transaction transaction = Transaction.newBuilder(UUID.randomUUID().toString())
                .setAccount(account)
                .setAmount(smallAmount)
                .setDate(LocalDate.now())
                .setDescription("Tiny interest payment")
                .setType(Transaction.TransactionType.CREDIT)
                .build();

        CategorizedTransaction categorized = new CategorizedTransaction(transaction, category);

        // Execute
        var result = transactionDao.insert(categorized);

        // Verify: Small amount is handled
        assertTrue(result.isPresent());
        assertEquals(smallAmount, result.get().getTransaction().getAmount(), 0.001f);
    }

    @Test
    void negativeZeroAmount() throws SQLException {
        // Setup: Transaction with negative zero (-0.0)
        Account account = accountDao.insert(TestUtils.createRandomAccount()).orElseThrow();
        Category category;
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            category = categoryDao.insert(t, new Category("Zero Test")).orElseThrow();
        }

        float negativeZero = -0.0f;
        Transaction transaction = Transaction.newBuilder(UUID.randomUUID().toString())
                .setAccount(account)
                .setAmount(negativeZero)
                .setDate(LocalDate.now())
                .setDescription("Negative zero test")
                .setType(Transaction.TransactionType.DEBIT)
                .build();

        CategorizedTransaction categorized = new CategorizedTransaction(transaction, category);

        // Execute
        var result = transactionDao.insert(categorized);

        // Verify: Negative zero is handled (may be normalized to 0.0)
        assertTrue(result.isPresent());
        assertEquals(0.0f, result.get().getTransaction().getAmount(), 0.0f);
    }

    // ==================== Extremely Long Strings ====================

    @Test
    void veryLongCategoryName() throws SQLException {
        // Setup: Very long category name
        String longName = "A".repeat(1000);

        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            // Execute: Should either succeed or fail gracefully (not crash)
            assertDoesNotThrow(() -> categoryDao.insert(t, new Category(longName)));
        }
    }

    @Test
    void veryLongTransactionDescription() throws SQLException {
        // Setup: Transaction with very long description
        Account account = accountDao.insert(TestUtils.createRandomAccount()).orElseThrow();
        Category category;
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            category = categoryDao.insert(t, new Category("Long Desc")).orElseThrow();
        }

        String longDescription = "Payment for ".repeat(500); // Very long description
        Transaction transaction = Transaction.newBuilder(UUID.randomUUID().toString())
                .setAccount(account)
                .setAmount(-100.0f)
                .setDate(LocalDate.now())
                .setDescription(longDescription)
                .setType(Transaction.TransactionType.DEBIT)
                .build();

        CategorizedTransaction categorized = new CategorizedTransaction(transaction, category);

        // Execute: Should handle gracefully
        assertDoesNotThrow(() -> transactionDao.insert(categorized));
    }

    // ==================== Control Characters ====================

    @Test
    void nullBytesInDescription() throws SQLException {
        // Setup: Description with null bytes
        Account account = accountDao.insert(TestUtils.createRandomAccount()).orElseThrow();
        Category category;
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            category = categoryDao.insert(t, new Category("Null Byte Test")).orElseThrow();
        }

        String descriptionWithNull = "Before\0After";
        Transaction transaction = Transaction.newBuilder(UUID.randomUUID().toString())
                .setAccount(account)
                .setAmount(-50.0f)
                .setDate(LocalDate.now())
                .setDescription(descriptionWithNull)
                .setType(Transaction.TransactionType.DEBIT)
                .build();

        CategorizedTransaction categorized = new CategorizedTransaction(transaction, category);

        // Execute: Should handle gracefully (may truncate at null or preserve)
        assertDoesNotThrow(() -> transactionDao.insert(categorized));
    }

    @Test
    void controlCharactersInDescription() throws SQLException {
        // Setup: Description with various control characters
        Account account = accountDao.insert(TestUtils.createRandomAccount()).orElseThrow();
        Category category;
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            category = categoryDao.insert(t, new Category("Control Char Test")).orElseThrow();
        }

        // Tab, newline, carriage return, bell, backspace
        String descriptionWithControl = "Line1\tTabbed\nLine2\r\nLine3\u0007Bell\u0008Backspace";
        Transaction transaction = Transaction.newBuilder(UUID.randomUUID().toString())
                .setAccount(account)
                .setAmount(-30.0f)
                .setDate(LocalDate.now())
                .setDescription(descriptionWithControl)
                .setType(Transaction.TransactionType.DEBIT)
                .build();

        CategorizedTransaction categorized = new CategorizedTransaction(transaction, category);

        // Execute: Should handle gracefully
        var result = transactionDao.insert(categorized);
        assertTrue(result.isPresent(), "Transaction with control characters should be insertable");
    }

    // ==================== Boundary Dates ====================

    @Test
    void veryOldTransactionDate() throws SQLException {
        // Setup: Transaction from long ago
        Account account = accountDao.insert(TestUtils.createRandomAccount()).orElseThrow();
        Category category;
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            category = categoryDao.insert(t, new Category("Old Date")).orElseThrow();
        }

        LocalDate veryOldDate = LocalDate.of(1900, 1, 1);
        Transaction transaction = Transaction.newBuilder(UUID.randomUUID().toString())
                .setAccount(account)
                .setAmount(-10.0f)
                .setDate(veryOldDate)
                .setDescription("Very old transaction")
                .setType(Transaction.TransactionType.DEBIT)
                .build();

        CategorizedTransaction categorized = new CategorizedTransaction(transaction, category);

        // Execute
        var result = transactionDao.insert(categorized);

        // Verify: Old date is handled
        assertTrue(result.isPresent());
        assertEquals(veryOldDate, result.get().getTransaction().getDate());
    }

    @Test
    void futureDateTransaction() throws SQLException {
        // Setup: Transaction in the future
        Account account = accountDao.insert(TestUtils.createRandomAccount()).orElseThrow();
        Category category;
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            category = categoryDao.insert(t, new Category("Future Date")).orElseThrow();
        }

        LocalDate futureDate = LocalDate.now().plusYears(10);
        Transaction transaction = Transaction.newBuilder(UUID.randomUUID().toString())
                .setAccount(account)
                .setAmount(-10.0f)
                .setDate(futureDate)
                .setDescription("Future transaction")
                .setType(Transaction.TransactionType.DEBIT)
                .build();

        CategorizedTransaction categorized = new CategorizedTransaction(transaction, category);

        // Execute
        var result = transactionDao.insert(categorized);

        // Verify: Future date is accepted
        assertTrue(result.isPresent());
        assertEquals(futureDate, result.get().getTransaction().getDate());
    }
}
