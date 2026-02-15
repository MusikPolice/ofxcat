package ca.jonathanfritz.ofxcat.service;

import static org.junit.jupiter.api.Assertions.*;

import ca.jonathanfritz.ofxcat.AbstractDatabaseTest;
import ca.jonathanfritz.ofxcat.TestUtils;
import ca.jonathanfritz.ofxcat.cleaner.TransactionCleanerFactory;
import ca.jonathanfritz.ofxcat.datastore.AccountDao;
import ca.jonathanfritz.ofxcat.datastore.CategorizedTransactionDao;
import ca.jonathanfritz.ofxcat.datastore.CategoryDao;
import ca.jonathanfritz.ofxcat.datastore.TransactionTokenDao;
import ca.jonathanfritz.ofxcat.datastore.TransferDao;
import ca.jonathanfritz.ofxcat.datastore.dto.Account;
import ca.jonathanfritz.ofxcat.datastore.dto.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import ca.jonathanfritz.ofxcat.datastore.utils.DatabaseTransaction;
import ca.jonathanfritz.ofxcat.io.OfxAccount;
import ca.jonathanfritz.ofxcat.io.OfxBalance;
import ca.jonathanfritz.ofxcat.io.OfxExport;
import ca.jonathanfritz.ofxcat.io.OfxTransaction;
import com.webcohesion.ofx4j.domain.data.common.TransactionType;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tests that verify tokens are correctly stored when transactions are imported.
 * This is critical for token-based matching to work for newly imported transactions.
 */
class TransactionImportTokenStorageTest extends AbstractDatabaseTest {

    private final TransactionCleanerFactory transactionCleanerFactory;
    private final AccountDao accountDao;
    private final CategoryDao categoryDao;
    private final CategorizedTransactionDao categorizedTransactionDao;
    private final TransactionTokenDao transactionTokenDao;
    private final TransferMatchingService transferMatchingService;
    private final TransferDao transferDao;

    TransactionImportTokenStorageTest() {
        this.transactionCleanerFactory = new TransactionCleanerFactory();
        this.accountDao = injector.getInstance(AccountDao.class);
        this.categoryDao = injector.getInstance(CategoryDao.class);
        this.categorizedTransactionDao = injector.getInstance(CategorizedTransactionDao.class);
        this.transactionTokenDao = new TransactionTokenDao();
        this.transferMatchingService = injector.getInstance(TransferMatchingService.class);
        this.transferDao = injector.getInstance(TransferDao.class);
    }

    @Test
    void importedTransactionGetsTokensStored() throws SQLException {
        // Given: An account and a category for new transactions
        final Account testAccount =
                accountDao.insert(TestUtils.createRandomAccount()).get();
        final Category testCategory =
                categoryDao.insert(new Category("Groceries")).get();

        // Create an OFX transaction with a multi-word description
        final OfxAccount ofxAccount = TestUtils.accountToOfxAccount(testAccount);
        final OfxTransaction ofxTxn = createOfxTransaction("FIT1", -50.0f, "SAFEWAY GROCERY STORE #1234", ofxAccount);
        final OfxBalance ofxBalance = OfxBalance.newBuilder().setAmount(950.0f).build();
        final List<OfxExport> ofxExports =
                Collections.singletonList(new OfxExport(ofxAccount, ofxBalance, Collections.singletonList(ofxTxn)));

        // When: We import the transaction
        final SpyCli spyCli = new SpyCli(testCategory);
        final TransactionCategoryService transactionCategoryService =
                createTransactionCategoryService(categoryDao, categorizedTransactionDao, spyCli);
        final TransactionImportService transactionImportService = new TransactionImportService(
                spyCli,
                null,
                accountDao,
                transactionCleanerFactory,
                connection,
                categorizedTransactionDao,
                transactionCategoryService,
                categoryDao,
                transferMatchingService,
                transferDao,
                transactionTokenDao,
                tokenNormalizer);

        final List<CategorizedTransaction> result = transactionImportService.categorizeTransactions(ofxExports);

        // Then: The transaction was imported
        assertEquals(1, result.size());
        CategorizedTransaction imported = result.getFirst();
        assertNotNull(imported.getId(), "Imported transaction should have an ID");

        // And: Tokens were stored for the transaction
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            Set<String> storedTokens = transactionTokenDao.getTokens(t, imported.getId());
            assertFalse(storedTokens.isEmpty(), "Tokens should have been stored for the imported transaction");

            // Verify expected tokens are present (normalized: lowercase, excluding numeric store number)
            assertTrue(storedTokens.contains("safeway"), "Should contain 'safeway' token");
            assertTrue(storedTokens.contains("grocery"), "Should contain 'grocery' token");
            assertTrue(storedTokens.contains("store"), "Should contain 'store' token");
            assertFalse(
                    storedTokens.stream().anyMatch(t2 -> t2.matches("\\d+")),
                    "Should not contain numeric tokens (store number filtered)");
        }
    }

    @Test
    void importedTransactionWithUnknownCategoryDoesNotGetTokens() throws SQLException {
        // Given: An account - we'll force categorization to UNKNOWN
        final Account testAccount =
                accountDao.insert(TestUtils.createRandomAccount()).get();

        // Create an OFX transaction
        final OfxAccount ofxAccount = TestUtils.accountToOfxAccount(testAccount);
        final OfxTransaction ofxTxn = createOfxTransaction("FIT1", -50.0f, "SOME MERCHANT", ofxAccount);
        final OfxBalance ofxBalance = OfxBalance.newBuilder().setAmount(950.0f).build();
        final List<OfxExport> ofxExports =
                Collections.singletonList(new OfxExport(ofxAccount, ofxBalance, Collections.singletonList(ofxTxn)));

        // When: We import the transaction and user selects UNKNOWN category
        final SpyCli spyCli = new SpyCli(Category.UNKNOWN);
        final TransactionCategoryService transactionCategoryService =
                createTransactionCategoryService(categoryDao, categorizedTransactionDao, spyCli);
        final TransactionImportService transactionImportService = new TransactionImportService(
                spyCli,
                null,
                accountDao,
                transactionCleanerFactory,
                connection,
                categorizedTransactionDao,
                transactionCategoryService,
                categoryDao,
                transferMatchingService,
                transferDao,
                transactionTokenDao,
                tokenNormalizer);

        final List<CategorizedTransaction> result = transactionImportService.categorizeTransactions(ofxExports);

        // Then: The transaction was imported with UNKNOWN category
        assertEquals(1, result.size());
        assertEquals(Category.UNKNOWN, result.getFirst().getCategory());

        // And: No tokens were stored (UNKNOWN transactions don't contribute to matching)
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            Set<String> storedTokens =
                    transactionTokenDao.getTokens(t, result.getFirst().getId());
            assertTrue(storedTokens.isEmpty(), "No tokens should be stored for UNKNOWN category transactions");
        }
    }

    @Test
    void multipleImportedTransactionsEachGetTokens() throws SQLException {
        // Given: An account and category
        final Account testAccount =
                accountDao.insert(TestUtils.createRandomAccount()).get();
        final Category testCategory =
                categoryDao.insert(new Category("Shopping")).get();

        // Create multiple OFX transactions
        final OfxAccount ofxAccount = TestUtils.accountToOfxAccount(testAccount);
        final OfxTransaction txn1 = createOfxTransaction("FIT1", -25.0f, "AMAZON MARKETPLACE", ofxAccount);
        final OfxTransaction txn2 = createOfxTransaction("FIT2", -75.0f, "WALMART SUPERCENTER", ofxAccount);
        final OfxBalance ofxBalance = OfxBalance.newBuilder().setAmount(900.0f).build();
        final List<OfxExport> ofxExports =
                Collections.singletonList(new OfxExport(ofxAccount, ofxBalance, List.of(txn1, txn2)));

        // When: We import both transactions
        final SpyCli spyCli = new SpyCli(testCategory);
        final TransactionCategoryService transactionCategoryService =
                createTransactionCategoryService(categoryDao, categorizedTransactionDao, spyCli);
        final TransactionImportService transactionImportService = new TransactionImportService(
                spyCli,
                null,
                accountDao,
                transactionCleanerFactory,
                connection,
                categorizedTransactionDao,
                transactionCategoryService,
                categoryDao,
                transferMatchingService,
                transferDao,
                transactionTokenDao,
                tokenNormalizer);

        final List<CategorizedTransaction> result = transactionImportService.categorizeTransactions(ofxExports);

        // Then: Both transactions were imported
        assertEquals(2, result.size());

        // And: Each transaction has its own tokens
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            for (CategorizedTransaction ct : result) {
                Set<String> tokens = transactionTokenDao.getTokens(t, ct.getId());
                assertFalse(tokens.isEmpty(), "Transaction '" + ct.getDescription() + "' should have tokens stored");
            }
        }
    }

    @Test
    void tokensEnableTokenBasedMatchingForSubsequentImports() throws SQLException {
        // Given: First, import a transaction that gets tokens stored
        final Account testAccount =
                accountDao.insert(TestUtils.createRandomAccount()).get();
        final Category groceryCategory =
                categoryDao.insert(new Category("Groceries")).get();

        final OfxAccount ofxAccount = TestUtils.accountToOfxAccount(testAccount);
        final OfxTransaction firstTxn = createOfxTransaction("FIT1", -50.0f, "SAFEWAY GROCERY STORE", ofxAccount);
        final OfxBalance firstBalance =
                OfxBalance.newBuilder().setAmount(950.0f).build();
        final List<OfxExport> firstExport =
                Collections.singletonList(new OfxExport(ofxAccount, firstBalance, Collections.singletonList(firstTxn)));

        final SpyCli firstSpyCli = new SpyCli(groceryCategory);
        final TransactionCategoryService firstCategoryService =
                createTransactionCategoryService(categoryDao, categorizedTransactionDao, firstSpyCli);
        final TransactionImportService firstImportService = new TransactionImportService(
                firstSpyCli,
                null,
                accountDao,
                transactionCleanerFactory,
                connection,
                categorizedTransactionDao,
                firstCategoryService,
                categoryDao,
                transferMatchingService,
                transferDao,
                transactionTokenDao,
                tokenNormalizer);

        List<CategorizedTransaction> firstResult = firstImportService.categorizeTransactions(firstExport);
        assertEquals(1, firstResult.size());
        assertEquals(groceryCategory, firstResult.getFirst().getCategory());

        // When: We import a similar transaction (different store number, same merchant)
        final OfxTransaction secondTxn = createOfxTransaction("FIT2", -75.0f, "SAFEWAY GROCERY MARKET", ofxAccount);
        final OfxBalance secondBalance =
                OfxBalance.newBuilder().setAmount(875.0f).build();
        final List<OfxExport> secondExport = Collections.singletonList(
                new OfxExport(ofxAccount, secondBalance, Collections.singletonList(secondTxn)));

        // Create a new CLI spy to track if it was prompted
        final TrackingSpyCli secondSpyCli = new TrackingSpyCli(groceryCategory);
        final TransactionCategoryService secondCategoryService =
                createTransactionCategoryService(categoryDao, categorizedTransactionDao, secondSpyCli);
        final TransactionImportService secondImportService = new TransactionImportService(
                secondSpyCli,
                null,
                accountDao,
                transactionCleanerFactory,
                connection,
                categorizedTransactionDao,
                secondCategoryService,
                categoryDao,
                transferMatchingService,
                transferDao,
                transactionTokenDao,
                tokenNormalizer);

        List<CategorizedTransaction> secondResult = secondImportService.categorizeTransactions(secondExport);

        // Then: The second transaction was auto-categorized using token matching
        assertEquals(1, secondResult.size());
        assertEquals(
                groceryCategory,
                secondResult.getFirst().getCategory(),
                "Second transaction should be auto-categorized to Groceries via token matching");
    }

    // Helper method to create OFX transactions
    private OfxTransaction createOfxTransaction(String fitId, float amount, String description, OfxAccount account) {
        final TransactionType type = amount >= 0 ? TransactionType.CREDIT : TransactionType.DEBIT;
        return OfxTransaction.newBuilder()
                .setFitId(fitId)
                .setAmount(amount)
                .setName(description)
                .setType(type)
                .setDate(LocalDate.now())
                .setAccount(account)
                .build();
    }

    // Spy CLI for testing
    private static class SpyCli extends ca.jonathanfritz.ofxcat.cli.CLI {
        protected final Category category;

        SpyCli(Category category) {
            super(null, null);
            this.category = category;
        }

        @Override
        public void printFoundNewTransaction(ca.jonathanfritz.ofxcat.datastore.dto.Transaction transaction) {
            // no-op
        }

        @Override
        public java.util.Optional<Category> chooseCategoryOrAddNew(List<Category> categories) {
            return java.util.Optional.of(category);
        }

        @Override
        public java.util.Optional<Category> chooseCategoryOrChooseAnother(List<Category> categories) {
            return java.util.Optional.of(category);
        }

        @Override
        public void printTransactionCategorizedAs(final Category category) {
            // no-op
        }
    }

    // Extended spy CLI that tracks whether prompts were shown
    private static class TrackingSpyCli extends SpyCli {
        private boolean wasPromptedForCategory = false;
        private boolean wasPromptedToChoose = false;

        TrackingSpyCli(Category category) {
            super(category);
        }

        @Override
        public java.util.Optional<Category> chooseCategoryOrAddNew(List<Category> categories) {
            wasPromptedForCategory = true;
            return java.util.Optional.of(category);
        }

        @Override
        public java.util.Optional<Category> chooseCategoryOrChooseAnother(List<Category> categories) {
            wasPromptedToChoose = true;
            return java.util.Optional.of(category);
        }

        public boolean wasPromptedForCategory() {
            return wasPromptedForCategory;
        }

        public boolean wasPromptedToChoose() {
            return wasPromptedToChoose;
        }
    }
}
