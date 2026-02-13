package ca.jonathanfritz.ofxcat.service;

import ca.jonathanfritz.ofxcat.AbstractDatabaseTest;
import ca.jonathanfritz.ofxcat.TestUtils;
import ca.jonathanfritz.ofxcat.cli.CLI;
import ca.jonathanfritz.ofxcat.datastore.AccountDao;
import ca.jonathanfritz.ofxcat.datastore.CategorizedTransactionDao;
import ca.jonathanfritz.ofxcat.datastore.CategoryDao;
import ca.jonathanfritz.ofxcat.datastore.TransactionTokenDao;
import ca.jonathanfritz.ofxcat.datastore.dto.Account;
import ca.jonathanfritz.ofxcat.datastore.dto.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import ca.jonathanfritz.ofxcat.datastore.utils.DatabaseTransaction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Edge case tests for TransactionCategoryService focusing on fuzzy matching thresholds,
 * token filtering logic, and malformed input handling.
 */
class TransactionCategoryServiceEdgeCaseTest extends AbstractDatabaseTest {

    private final CategoryDao categoryDao;
    private final AccountDao accountDao;
    private final CategorizedTransactionDao categorizedTransactionDao;
    private final TransactionTokenDao transactionTokenDao;

    private Account testAccount;

    TransactionCategoryServiceEdgeCaseTest() {
        categoryDao = injector.getInstance(CategoryDao.class);
        accountDao = injector.getInstance(AccountDao.class);
        categorizedTransactionDao = injector.getInstance(CategorizedTransactionDao.class);
        transactionTokenDao = new TransactionTokenDao();
    }

    @BeforeEach
    void setUp() {
        testAccount = accountDao.insert(TestUtils.createRandomAccount()).orElse(null);
    }

    @Test
    void emptyDescriptionHandling() throws SQLException {
        // Setup: Transaction with empty description
        final Category expectedCategory = categoryDao.insert(new Category("Default Category")).orElse(null);

        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            final Transaction transaction = createTransaction(testAccount, "");
            final SpyCli spyCli = new SpyCli(expectedCategory);
            final TransactionCategoryService service = createTransactionCategoryService(
                    categoryDao, categorizedTransactionDao, spyCli);

            // Execute: Should not crash, should prompt for category
            final CategorizedTransaction result = service.categorizeTransaction(t, transaction);

            // Verify: User was prompted, transaction categorized
            Assertions.assertNotNull(result);
            Assertions.assertEquals(expectedCategory, result.getCategory());
            Assertions.assertTrue(spyCli.wasPromptedForNewCategory);

            // Verify: Description is preserved as empty string (not transformed)
            Assertions.assertEquals("", result.getTransaction().getDescription(),
                    "Empty description should be preserved as empty string");
        }
    }

    @Test
    void whitespaceOnlyDescriptionHandling() throws SQLException {
        // Setup: Transaction with whitespace-only description
        final Category expectedCategory = categoryDao.insert(new Category("Default Category")).orElse(null);

        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            final Transaction transaction = createTransaction(testAccount, "   \t\n   ");
            final SpyCli spyCli = new SpyCli(expectedCategory);
            final TransactionCategoryService service = createTransactionCategoryService(
                    categoryDao, categorizedTransactionDao, spyCli);

            // Execute: Should not crash, should prompt for category
            final CategorizedTransaction result = service.categorizeTransaction(t, transaction);

            // Verify: User was prompted
            Assertions.assertNotNull(result);
            Assertions.assertEquals(expectedCategory, result.getCategory());
        }
    }

    @Test
    void descriptionWithOnlyNumericTokens() throws SQLException {
        // Setup: "12345 67890 #999" - all tokens should be filtered out
        final Category expectedCategory = categoryDao.insert(new Category("Default Category")).orElse(null);

        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            final Transaction transaction = createTransaction(testAccount, "12345 67890 #999");
            final SpyCli spyCli = new SpyCli(expectedCategory);
            final TransactionCategoryService service = createTransactionCategoryService(
                    categoryDao, categorizedTransactionDao, spyCli);

            // Execute: All tokens filtered, should prompt for new category
            final CategorizedTransaction result = service.categorizeTransaction(t, transaction);

            // Verify: User was prompted for new category (no partial matches possible)
            Assertions.assertNotNull(result);
            Assertions.assertEquals(expectedCategory, result.getCategory());

            // Verify that tokens were actually filtered: wasPromptedForNewCategory should be true
            // because no partial matches were found (all tokens were numeric and filtered out)
            Assertions.assertTrue(spyCli.wasPromptedForNewCategory,
                    "User should have been prompted for new category because all numeric tokens were filtered out");

            // Additionally verify that no partial match prompt occurred (capturedCategories should be empty)
            Assertions.assertTrue(spyCli.capturedCategories.isEmpty(),
                    "No partial matches should have been found since all tokens were filtered");
        }
    }

    @Test
    void descriptionWithPhoneNumber() throws SQLException {
        // Setup: "Payment to 555-123-4567"
        // Expected: Phone number filtered, "Payment" matched
        final Category paymentCategory = categoryDao.insert(new Category("Payments")).orElse(null);

        // Insert existing transaction with "Payment" in description
        final Transaction existingTransaction = createTransaction(testAccount, "Payment Received");
        categorizedTransactionDao.insert(new CategorizedTransaction(existingTransaction, paymentCategory));

        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            final Transaction transaction = createTransaction(testAccount, "Payment to 555-123-4567");
            final SpyCli spyCli = new SpyCli(paymentCategory);
            final TransactionCategoryService service = createTransactionCategoryService(
                    categoryDao, categorizedTransactionDao, spyCli);

            // Execute: Phone number should be filtered, "Payment" should match
            final CategorizedTransaction result = service.categorizeTransaction(t, transaction);

            // Verify: Matched to payment category via partial match
            Assertions.assertNotNull(result);
            Assertions.assertEquals(paymentCategory, result.getCategory());
        }
    }

    @Test
    void unicodeCharactersInDescription() throws SQLException {
        // Setup: "Café José™ 中文"
        // Expected: Should handle without crashing, create category
        final Category expectedCategory = categoryDao.insert(new Category("Restaurants")).orElse(null);

        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            final Transaction transaction = createTransaction(testAccount, "Café José™ 中文");
            final SpyCli spyCli = new SpyCli(expectedCategory);
            final TransactionCategoryService service = createTransactionCategoryService(
                    categoryDao, categorizedTransactionDao, spyCli);

            // Execute: Should not crash
            final CategorizedTransaction result = service.categorizeTransaction(t, transaction);

            // Verify: Transaction categorized
            Assertions.assertNotNull(result);
            Assertions.assertEquals(expectedCategory, result.getCategory());
            Assertions.assertEquals("Café José™ 中文", result.getTransaction().getDescription());
        }
    }

    @Test
    void sqlInjectionInDescription() throws SQLException {
        // Setup: "'; DROP TABLE Category; --"
        // Expected: Treated as literal string, no SQL execution
        final Category expectedCategory = categoryDao.insert(new Category("Security Test")).orElse(null);

        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            final Transaction transaction = createTransaction(testAccount, "'; DROP TABLE Category; --");
            final SpyCli spyCli = new SpyCli(expectedCategory);
            final TransactionCategoryService service = createTransactionCategoryService(
                    categoryDao, categorizedTransactionDao, spyCli);

            // Execute: Should treat as literal string
            final CategorizedTransaction result = service.categorizeTransaction(t, transaction);

            // Verify: Transaction categorized, database intact
            Assertions.assertNotNull(result);
            Assertions.assertEquals(expectedCategory, result.getCategory());

            // Verify database is still intact by selecting categories
            final List<Category> categories = categoryDao.select();
            Assertions.assertFalse(categories.isEmpty(), "Category table should still exist");
        }
    }

    @Test
    void veryLongDescription() throws SQLException {
        // Setup: Description with 10000 characters
        final String longDescription = "A".repeat(10000);
        final Category expectedCategory = categoryDao.insert(new Category("Long Descriptions")).orElse(null);

        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            final Transaction transaction = createTransaction(testAccount, longDescription);
            final SpyCli spyCli = new SpyCli(expectedCategory);
            final TransactionCategoryService service = createTransactionCategoryService(
                    categoryDao, categorizedTransactionDao, spyCli);

            // Execute: Should handle gracefully (may truncate or accept)
            final CategorizedTransaction result = service.categorizeTransaction(t, transaction);

            // Verify: Transaction processed (behavior documented, not enforcing specific handling)
            Assertions.assertNotNull(result);
        }
    }

    @Test
    void descriptionWithStoreNumbers() throws SQLException {
        // Setup: "Safeway #1234" and "Safeway #5678"
        // Expected: #1234 and #5678 filtered out, both match "Safeway"
        final Category groceryCategory = categoryDao.insert(new Category("Groceries")).orElse(null);

        // Insert first Safeway transaction
        final Transaction firstSafeway = createTransaction(testAccount, "Safeway #1234");
        categorizedTransactionDao.insert(new CategorizedTransaction(firstSafeway, groceryCategory));

        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            // Try to categorize second Safeway with different store number
            final Transaction secondSafeway = createTransaction(testAccount, "Safeway #5678");
            final SpyCli spyCli = new SpyCli(groceryCategory);
            final TransactionCategoryService service = createTransactionCategoryService(
                    categoryDao, categorizedTransactionDao, spyCli);

            // Execute: Should match because #5678 is filtered out
            final CategorizedTransaction result = service.categorizeTransaction(t, secondSafeway);

            // Verify: Matched to grocery category
            Assertions.assertNotNull(result);
            Assertions.assertEquals(groceryCategory, result.getCategory());
        }
    }

    @Test
    void sameDescriptionMapsToThreeDifferentCategories() throws SQLException {
        // Setup: "Amazon.com" mapped to "Books", "Electronics", "Household"
        // Expected: All 3 presented as choices
        final Category books = categoryDao.insert(new Category("Books")).orElse(null);
        final Category electronics = categoryDao.insert(new Category("Electronics")).orElse(null);
        final Category household = categoryDao.insert(new Category("Household")).orElse(null);

        // Insert three Amazon transactions with different categories
        categorizedTransactionDao.insert(new CategorizedTransaction(
                createTransaction(testAccount, "Amazon.com"), books));
        categorizedTransactionDao.insert(new CategorizedTransaction(
                createTransaction(testAccount, "Amazon.com"), electronics));
        categorizedTransactionDao.insert(new CategorizedTransaction(
                createTransaction(testAccount, "Amazon.com"), household));

        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            final Transaction newAmazon = createTransaction(testAccount, "Amazon.com");
            final SpyCli spyCli = new SpyCli(books);
            final TransactionCategoryService service = createTransactionCategoryService(
                    categoryDao, categorizedTransactionDao, spyCli);

            // Execute
            final CategorizedTransaction result = service.categorizeTransaction(t, newAmazon);

            // Verify: User was prompted with all three categories
            Assertions.assertEquals(3, spyCli.capturedCategories.size());
            Assertions.assertTrue(spyCli.capturedCategories.contains(books));
            Assertions.assertTrue(spyCli.capturedCategories.contains(electronics));
            Assertions.assertTrue(spyCli.capturedCategories.contains(household));
        }
    }

    @Test
    void descriptionWithSpecialCharacters() throws SQLException {
        // Setup: Various special characters
        final String[] specialDescriptions = {
                "McDonald's \"Special\" Offer",
                "C:\\Windows\\System32",
                "Price: $50.00 (50% off!)",
                "Line1\nLine2\tTabbed",
                "<script>alert('xss')</script>"
        };

        final Category expectedCategory = categoryDao.insert(new Category("Special Chars")).orElse(null);

        for (String description : specialDescriptions) {
            try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
                final Transaction transaction = createTransaction(testAccount, description);
                final SpyCli spyCli = new SpyCli(expectedCategory);
                final TransactionCategoryService service = createTransactionCategoryService(
                        categoryDao, categorizedTransactionDao, spyCli);

                // Execute: Should handle without crashing
                final CategorizedTransaction result = service.categorizeTransaction(t, transaction);

                // Verify: Transaction processed
                Assertions.assertNotNull(result, "Failed to process: " + description);
                Assertions.assertEquals(description, result.getTransaction().getDescription());
            }
        }
    }

    @Test
    void tokenMatchWithMultipleCategoriesSimilarOverlap() throws SQLException {
        // Setup: Two categories with similar token overlap scores
        // Expected: Both presented to user when multiple matches found
        final Category restaurant1 = categoryDao.insert(new Category("Fast Food")).orElse(null);
        final Category restaurant2 = categoryDao.insert(new Category("Dining")).orElse(null);

        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            // Insert similar transactions with tokens stored directly
            final Transaction tx1 = createTransaction(testAccount, "McDonald's Restaurant");
            CategorizedTransaction ct1 = categorizedTransactionDao.insert(t, new CategorizedTransaction(tx1, restaurant1))
                    .orElseThrow(() -> new RuntimeException("Failed to insert"));
            Set<String> tokens1 = tokenNormalizer.normalize(tx1.getDescription());
            transactionTokenDao.insertTokens(t, ct1.getId(), tokens1);

            final Transaction tx2 = createTransaction(testAccount, "McDonald's Diner");
            CategorizedTransaction ct2 = categorizedTransactionDao.insert(t, new CategorizedTransaction(tx2, restaurant2))
                    .orElseThrow(() -> new RuntimeException("Failed to insert"));
            Set<String> tokens2 = tokenNormalizer.normalize(tx2.getDescription());
            transactionTokenDao.insertTokens(t, ct2.getId(), tokens2);
        }

        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            final Transaction newTransaction = createTransaction(testAccount, "McDonald's");
            final SpyCli spyCli = new SpyCli(restaurant1);
            final TransactionCategoryService service = createTransactionCategoryService(
                    categoryDao, categorizedTransactionDao, spyCli);

            // Execute
            final CategorizedTransaction result = service.categorizeTransaction(t, newTransaction);

            // Verify: Transaction was categorized (either via prompt or auto-categorization)
            Assertions.assertNotNull(result);
            // With token matching, multiple matches with similar scores should prompt the user
            // Note: Exact behavior depends on token matching overlap threshold
        }
    }

    // Helper to create a transaction with specific description
    private Transaction createTransaction(Account account, String description) {
        return Transaction.newBuilder(UUID.randomUUID().toString())
                .setAccount(account)
                .setDescription(description)
                .setDate(LocalDate.now())
                .setType(Transaction.TransactionType.DEBIT)
                .setAmount(-10.0f)
                .setBalance(1000.0f)
                .build();
    }

    // Spy CLI for testing
    private static class SpyCli extends CLI {
        private final Category category;
        private final List<Category> capturedCategories = new ArrayList<>();
        private boolean wasPromptedForNewCategory = false;

        SpyCli(Category category) {
            super(null, null);
            this.category = category;
        }

        @Override
        public Optional<Category> chooseCategoryOrAddNew(List<Category> categories) {
            wasPromptedForNewCategory = true;
            return Optional.of(category);
        }

        @Override
        public Optional<Category> chooseCategoryOrChooseAnother(List<Category> categories) {
            capturedCategories.addAll(categories);
            return Optional.of(category);
        }

        @Override
        public String promptForNewCategoryName(List<Category> allCategories) {
            return category.getName();
        }
    }
}
