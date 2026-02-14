package ca.jonathanfritz.ofxcat.integration;

import static org.junit.jupiter.api.Assertions.*;

import ca.jonathanfritz.ofxcat.AbstractDatabaseTest;
import ca.jonathanfritz.ofxcat.TestUtils;
import ca.jonathanfritz.ofxcat.cleaner.TransactionCleanerFactory;
import ca.jonathanfritz.ofxcat.cli.CLI;
import ca.jonathanfritz.ofxcat.datastore.AccountDao;
import ca.jonathanfritz.ofxcat.datastore.CategorizedTransactionDao;
import ca.jonathanfritz.ofxcat.datastore.CategoryDao;
import ca.jonathanfritz.ofxcat.datastore.TransactionTokenDao;
import ca.jonathanfritz.ofxcat.datastore.TransferDao;
import ca.jonathanfritz.ofxcat.datastore.dto.Account;
import ca.jonathanfritz.ofxcat.datastore.dto.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Transfer;
import ca.jonathanfritz.ofxcat.io.OfxAccount;
import ca.jonathanfritz.ofxcat.io.OfxBalance;
import ca.jonathanfritz.ofxcat.io.OfxExport;
import ca.jonathanfritz.ofxcat.io.OfxTransaction;
import ca.jonathanfritz.ofxcat.service.TransactionCategoryService;
import ca.jonathanfritz.ofxcat.service.TransactionImportService;
import ca.jonathanfritz.ofxcat.service.TransferMatchingService;
import com.webcohesion.ofx4j.domain.data.common.TransactionType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for end-to-end import flows.
 * These tests verify complete import scenarios with realistic transaction patterns.
 */
class ImportFlowIntegrationTest extends AbstractDatabaseTest {

    private final TransactionCleanerFactory transactionCleanerFactory;
    private final AccountDao accountDao;
    private final CategoryDao categoryDao;
    private final CategorizedTransactionDao categorizedTransactionDao;
    private final TransactionTokenDao transactionTokenDao;
    private final TransferMatchingService transferMatchingService;
    private final TransferDao transferDao;

    ImportFlowIntegrationTest() {
        this.transactionCleanerFactory = new TransactionCleanerFactory();
        this.accountDao = injector.getInstance(AccountDao.class);
        this.categoryDao = injector.getInstance(CategoryDao.class);
        this.categorizedTransactionDao = injector.getInstance(CategorizedTransactionDao.class);
        this.transactionTokenDao = new TransactionTokenDao();
        this.transferMatchingService = injector.getInstance(TransferMatchingService.class);
        this.transferDao = injector.getInstance(TransferDao.class);
    }

    /**
     * Test importing a first-time file with multiple transactions over 30 days.
     * Simulates a user's first import experience.
     */
    @Test
    void firstTimeImportWithMultipleTransactions() {
        // Setup: Create an account and categories
        Account checkingAccount =
                accountDao.insert(TestUtils.createRandomAccount("My Checking")).orElseThrow();

        // Create categories that will be used for categorization
        Category groceries = categoryDao.insert(new Category("Groceries")).orElseThrow();
        Category restaurants = categoryDao.insert(new Category("Restaurants")).orElseThrow();
        Category utilities = categoryDao.insert(new Category("Utilities")).orElseThrow();

        // Create 10 transactions over 30 days with recognizable merchants
        LocalDate baseDate = LocalDate.now().minusDays(30);
        List<OfxTransaction> transactions = new ArrayList<>();

        // Add grocery transactions
        transactions.add(createOfxTransaction("SAFEWAY STORE #1234", -75.50f, baseDate, TransactionType.DEBIT));
        transactions.add(
                createOfxTransaction("WHOLE FOODS MARKET", -120.00f, baseDate.plusDays(7), TransactionType.DEBIT));
        transactions.add(
                createOfxTransaction("SAFEWAY STORE #5678", -45.25f, baseDate.plusDays(14), TransactionType.DEBIT));

        // Add restaurant transactions
        transactions.add(
                createOfxTransaction("CHIPOTLE MEXICAN GRILL", -12.50f, baseDate.plusDays(3), TransactionType.DEBIT));
        transactions.add(
                createOfxTransaction("STARBUCKS COFFEE", -6.75f, baseDate.plusDays(10), TransactionType.DEBIT));

        // Add utility payment
        transactions.add(
                createOfxTransaction("CITY WATER UTILITY", -85.00f, baseDate.plusDays(15), TransactionType.DEBIT));

        // Add paycheck (credit)
        transactions.add(createOfxTransaction(
                "DIRECT DEP ACME CORP PAYROLL", 2500.00f, baseDate.plusDays(15), TransactionType.CREDIT));

        // Add random transactions
        transactions.add(createOfxTransaction("AMAZON.COM", -45.99f, baseDate.plusDays(20), TransactionType.DEBIT));
        transactions.add(createOfxTransaction("NETFLIX.COM", -15.99f, baseDate.plusDays(1), TransactionType.DEBIT));
        transactions.add(
                createOfxTransaction("ATM WITHDRAWAL", -100.00f, baseDate.plusDays(25), TransactionType.DEBIT));

        // Create OFX export
        OfxAccount ofxAccount = TestUtils.accountToOfxAccount(checkingAccount);
        OfxBalance balance = OfxBalance.newBuilder().setAmount(1500.00f).build();
        List<OfxExport> ofxExports = List.of(new OfxExport(ofxAccount, balance, transactions));

        // Execute import with SpyCli that returns pre-defined categories
        SpyCli spyCli = new SpyCli(List.of(groceries, restaurants, utilities));
        TransactionCategoryService transactionCategoryService =
                createTransactionCategoryService(categoryDao, categorizedTransactionDao, spyCli);
        TransactionImportService transactionImportService = new TransactionImportService(
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

        List<CategorizedTransaction> imported = transactionImportService.categorizeTransactions(ofxExports);

        // Verify: All 10 transactions were imported
        assertEquals(10, imported.size(), "All 10 transactions should be imported");

        // Verify: Transactions are stored in database (check via selectByFitId for first transaction)
        assertTrue(
                imported.stream().allMatch(t -> categorizedTransactionDao
                        .selectByFitId(t.getFitId())
                        .isPresent()),
                "All transactions should be stored in database");

        // Verify: Each transaction has a category
        assertTrue(
                imported.stream().allMatch(t -> t.getCategory() != null),
                "All transactions should have a category assigned");

        // Verify: CLI was notified of each new transaction
        assertEquals(10, spyCli.getTransactionCount(), "CLI should be notified of each transaction");
    }

    /**
     * Test that importing the same file twice doesn't create duplicate transactions.
     */
    @Test
    void reimportingSameFileDoesNotCreateDuplicates() {
        // Setup
        Account account = accountDao.insert(TestUtils.createRandomAccount()).orElseThrow();
        Category category = categoryDao.insert(new Category("Test")).orElseThrow();

        LocalDate date = LocalDate.now();
        List<OfxTransaction> transactions = List.of(
                createOfxTransaction("MERCHANT A", -50.00f, date, TransactionType.DEBIT),
                createOfxTransaction("MERCHANT B", -25.00f, date, TransactionType.DEBIT));

        OfxAccount ofxAccount = TestUtils.accountToOfxAccount(account);
        OfxBalance balance = OfxBalance.newBuilder().setAmount(500.00f).build();
        List<OfxExport> ofxExports = List.of(new OfxExport(ofxAccount, balance, transactions));

        // First import
        SpyCli spyCli1 = new SpyCli(List.of(category));
        TransactionCategoryService tcs1 =
                createTransactionCategoryService(categoryDao, categorizedTransactionDao, spyCli1);
        TransactionImportService tis1 = new TransactionImportService(
                spyCli1,
                null,
                accountDao,
                transactionCleanerFactory,
                connection,
                categorizedTransactionDao,
                tcs1,
                categoryDao,
                transferMatchingService,
                transferDao,
                transactionTokenDao,
                tokenNormalizer);

        List<CategorizedTransaction> firstImport = tis1.categorizeTransactions(ofxExports);
        assertEquals(2, firstImport.size(), "First import should have 2 transactions");

        // Second import of same file
        SpyCli spyCli2 = new SpyCli(List.of(category));
        TransactionCategoryService tcs2 =
                createTransactionCategoryService(categoryDao, categorizedTransactionDao, spyCli2);
        TransactionImportService tis2 = new TransactionImportService(
                spyCli2,
                null,
                accountDao,
                transactionCleanerFactory,
                connection,
                categorizedTransactionDao,
                tcs2,
                categoryDao,
                transferMatchingService,
                transferDao,
                transactionTokenDao,
                tokenNormalizer);

        List<CategorizedTransaction> secondImport = tis2.categorizeTransactions(ofxExports);
        assertEquals(0, secondImport.size(), "Second import should have 0 new transactions (duplicates ignored)");

        // Verify database still has only 2 transactions (by checking fitIds from first import)
        assertTrue(
                firstImport.stream().allMatch(t -> categorizedTransactionDao
                        .selectByFitId(t.getFitId())
                        .isPresent()),
                "Original transactions should still exist in database");
    }

    /**
     * Test importing transactions that span multiple months.
     */
    @Test
    void importTransactionsAcrossMultipleMonths() {
        // Setup
        Account account = accountDao.insert(TestUtils.createRandomAccount()).orElseThrow();
        Category category = categoryDao.insert(new Category("Monthly")).orElseThrow();

        // Create transactions across 3 months
        List<OfxTransaction> transactions = new ArrayList<>();
        LocalDate month1 = LocalDate.of(2024, 1, 15);
        LocalDate month2 = LocalDate.of(2024, 2, 15);
        LocalDate month3 = LocalDate.of(2024, 3, 15);

        transactions.add(createOfxTransaction("JAN PAYMENT", -100.00f, month1, TransactionType.DEBIT));
        transactions.add(createOfxTransaction("FEB PAYMENT", -100.00f, month2, TransactionType.DEBIT));
        transactions.add(createOfxTransaction("MAR PAYMENT", -100.00f, month3, TransactionType.DEBIT));

        OfxAccount ofxAccount = TestUtils.accountToOfxAccount(account);
        OfxBalance balance = OfxBalance.newBuilder().setAmount(700.00f).build();
        List<OfxExport> ofxExports = List.of(new OfxExport(ofxAccount, balance, transactions));

        // Execute
        SpyCli spyCli = new SpyCli(List.of(category));
        TransactionCategoryService tcs =
                createTransactionCategoryService(categoryDao, categorizedTransactionDao, spyCli);
        TransactionImportService tis = new TransactionImportService(
                spyCli,
                null,
                accountDao,
                transactionCleanerFactory,
                connection,
                categorizedTransactionDao,
                tcs,
                categoryDao,
                transferMatchingService,
                transferDao,
                transactionTokenDao,
                tokenNormalizer);

        List<CategorizedTransaction> imported = tis.categorizeTransactions(ofxExports);

        // Verify
        assertEquals(3, imported.size());

        // Verify dates are preserved correctly
        Set<LocalDate> dates =
                imported.stream().map(t -> t.getTransaction().getDate()).collect(Collectors.toSet());
        assertTrue(dates.contains(month1));
        assertTrue(dates.contains(month2));
        assertTrue(dates.contains(month3));
    }

    /**
     * Test import with mix of credits and debits calculates balances correctly.
     */
    @Test
    void importWithMixedCreditsAndDebits() {
        // Setup
        Account account = accountDao.insert(TestUtils.createRandomAccount()).orElseThrow();
        Category income = categoryDao.insert(new Category("Income")).orElseThrow();
        Category expense = categoryDao.insert(new Category("Expense")).orElseThrow();

        LocalDate date = LocalDate.now();
        List<OfxTransaction> transactions = List.of(
                createOfxTransaction("PAYCHECK", 1000.00f, date, TransactionType.CREDIT),
                createOfxTransaction("RENT", -800.00f, date.plusDays(1), TransactionType.DEBIT),
                createOfxTransaction("BONUS", 200.00f, date.plusDays(2), TransactionType.CREDIT),
                createOfxTransaction("GROCERY", -50.00f, date.plusDays(3), TransactionType.DEBIT));

        OfxAccount ofxAccount = TestUtils.accountToOfxAccount(account);
        float endingBalance = 350.00f; // 1000 - 800 + 200 - 50
        OfxBalance balance = OfxBalance.newBuilder().setAmount(endingBalance).build();
        List<OfxExport> ofxExports = List.of(new OfxExport(ofxAccount, balance, transactions));

        // Execute
        SpyCli spyCli = new SpyCli(List.of(income, expense));
        TransactionCategoryService tcs =
                createTransactionCategoryService(categoryDao, categorizedTransactionDao, spyCli);
        TransactionImportService tis = new TransactionImportService(
                spyCli,
                null,
                accountDao,
                transactionCleanerFactory,
                connection,
                categorizedTransactionDao,
                tcs,
                categoryDao,
                transferMatchingService,
                transferDao,
                transactionTokenDao,
                tokenNormalizer);

        List<CategorizedTransaction> imported = tis.categorizeTransactions(ofxExports);

        // Verify
        assertEquals(4, imported.size());

        // Verify credits and debits
        long credits = imported.stream()
                .filter(t -> t.getTransaction().getAmount() > 0)
                .count();
        long debits = imported.stream()
                .filter(t -> t.getTransaction().getAmount() < 0)
                .count();

        assertEquals(2, credits, "Should have 2 credit transactions");
        assertEquals(2, debits, "Should have 2 debit transactions");

        // Verify total
        float total = imported.stream().map(t -> t.getTransaction().getAmount()).reduce(0f, Float::sum);
        assertEquals(350.00f, total, 0.01f, "Net change should match");
    }

    // ==================== Helper Methods ====================

    private OfxTransaction createOfxTransaction(String name, float amount, LocalDate date, TransactionType type) {
        return OfxTransaction.newBuilder()
                .setFitId(UUID.randomUUID().toString())
                .setName(name)
                .setAmount(amount)
                .setDate(date)
                .setType(type)
                .build();
    }

    // ==================== Test Double ====================

    private static class SpyCli extends CLI {
        private final List<Category> categoriesToReturn;
        private int categoryIndex = 0;
        private int transactionCount = 0;

        SpyCli(List<Category> categoriesToReturn) {
            super(null, null);
            this.categoriesToReturn = categoriesToReturn;
        }

        @Override
        public void printFoundNewTransaction(Transaction transaction) {
            transactionCount++;
        }

        @Override
        public Optional<Category> chooseCategoryOrAddNew(List<Category> categories) {
            return chooseCategory();
        }

        @Override
        public Optional<Category> chooseCategoryOrChooseAnother(List<Category> categories) {
            return chooseCategory();
        }

        private Optional<Category> chooseCategory() {
            // Cycle through the provided categories
            if (categoriesToReturn.isEmpty()) {
                return Optional.of(Category.UNKNOWN);
            }
            Category category = categoriesToReturn.get(categoryIndex % categoriesToReturn.size());
            categoryIndex++;
            return Optional.of(category);
        }

        @Override
        public void printTransactionCategorizedAs(Category category) {
            // no-op
        }

        @Override
        public void printFoundNewTransfer(Transfer transfer) {
            // no-op
        }

        public int getTransactionCount() {
            return transactionCount;
        }
    }
}
