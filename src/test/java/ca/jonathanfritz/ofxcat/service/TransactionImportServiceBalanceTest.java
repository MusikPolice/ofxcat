package ca.jonathanfritz.ofxcat.service;

import ca.jonathanfritz.ofxcat.AbstractDatabaseTest;
import ca.jonathanfritz.ofxcat.TestUtils;
import ca.jonathanfritz.ofxcat.cleaner.TransactionCleanerFactory;
import ca.jonathanfritz.ofxcat.datastore.*;
import ca.jonathanfritz.ofxcat.datastore.dto.Account;
import ca.jonathanfritz.ofxcat.datastore.dto.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import ca.jonathanfritz.ofxcat.io.OfxAccount;
import ca.jonathanfritz.ofxcat.io.OfxBalance;
import ca.jonathanfritz.ofxcat.io.OfxExport;
import ca.jonathanfritz.ofxcat.io.OfxTransaction;
import com.webcohesion.ofx4j.domain.data.common.TransactionType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Tests for balance calculation logic in TransactionImportService.
 * These tests verify critical business logic around running balance tracking.
 */
class TransactionImportServiceBalanceTest extends AbstractDatabaseTest {

    private final TransactionCleanerFactory transactionCleanerFactory;
    private final AccountDao accountDao;
    private final CategoryDao categoryDao;
    private final CategorizedTransactionDao categorizedTransactionDao;
    private final TransactionTokenDao transactionTokenDao;
    private final TransferMatchingService transferMatchingService;
    private final TransferDao transferDao;

    public TransactionImportServiceBalanceTest() {
        this.transactionCleanerFactory = new TransactionCleanerFactory();
        this.accountDao = injector.getInstance(AccountDao.class);
        this.categoryDao = injector.getInstance(CategoryDao.class);
        this.categorizedTransactionDao = injector.getInstance(CategorizedTransactionDao.class);
        this.transactionTokenDao = new TransactionTokenDao();
        this.transferMatchingService = injector.getInstance(TransferMatchingService.class);
        this.transferDao = injector.getInstance(TransferDao.class);
    }

    @Test
    void initialBalanceCalculationWithNoTransactions() {
        // Setup: OFX export with final balance but no transactions
        final Account testAccount = accountDao.insert(TestUtils.createRandomAccount()).get();
        final Category testCategory = categoryDao.insert(new Category("Test Category")).get();
        final float finalBalance = 1000.0f;

        final OfxAccount ofxAccount = TestUtils.accountToOfxAccount(testAccount);
        final OfxBalance ofxBalance = OfxBalance.newBuilder().setAmount(finalBalance).build();
        final List<OfxExport> ofxExports = Collections.singletonList(
                new OfxExport(ofxAccount, ofxBalance, Collections.emptyList())
        );

        // Execute
        final SpyCli spyCli = new SpyCli(testCategory);
        final TransactionCategoryService transactionCategoryService = createTransactionCategoryService(
                categoryDao, categorizedTransactionDao, spyCli);
        final TransactionImportService transactionImportService = new TransactionImportService(
                spyCli, null, accountDao, transactionCleanerFactory, connection,
                categorizedTransactionDao, transactionCategoryService, categoryDao,
                transferMatchingService, transferDao, transactionTokenDao, tokenNormalizer);

        final List<CategorizedTransaction> result = transactionImportService.categorizeTransactions(ofxExports);

        // Verify: No transactions imported, no errors
        Assertions.assertEquals(0, result.size());
    }

    @Test
    void initialBalanceCalculationWithSingleCredit() {
        // Setup: OFX file reports final balance of $1000 after a single +$50 credit
        // The initial balance must have been $950 (because $950 + $50 = $1000)
        // This test verifies that the transaction's balance field is set to the
        // account balance AFTER the transaction ($1000, not $950)
        final Account testAccount = accountDao.insert(TestUtils.createRandomAccount()).get();
        final Category testCategory = categoryDao.insert(new Category("Test Category")).get();
        final float finalBalance = 1000.0f;
        final float creditAmount = 50.0f;
        final float expectedInitialBalance = 950.0f; // Calculated: finalBalance - creditAmount

        final OfxAccount ofxAccount = TestUtils.accountToOfxAccount(testAccount);
        final OfxTransaction credit = createOfxTransaction("FIT1", creditAmount, LocalDate.of(2023, 1, 15), ofxAccount);
        final OfxBalance ofxBalance = OfxBalance.newBuilder().setAmount(finalBalance).build();
        final List<OfxExport> ofxExports = Collections.singletonList(
                new OfxExport(ofxAccount, ofxBalance, Collections.singletonList(credit))
        );

        // Execute
        final SpyCli spyCli = new SpyCli(testCategory);
        final TransactionCategoryService transactionCategoryService = createTransactionCategoryService(
                categoryDao, categorizedTransactionDao, spyCli);
        final TransactionImportService transactionImportService = new TransactionImportService(
                spyCli, null, accountDao, transactionCleanerFactory, connection,
                categorizedTransactionDao, transactionCategoryService, categoryDao,
                transferMatchingService, transferDao, transactionTokenDao, tokenNormalizer);

        final List<CategorizedTransaction> result = transactionImportService.categorizeTransactions(ofxExports);

        // Verify: Transaction balance equals final balance (the balance AFTER the transaction)
        Assertions.assertEquals(1, result.size());
        final float actualBalance = result.getFirst().getTransaction().getBalance();

        // The transaction balance should be $1000 (the account balance after the +$50 credit was applied)
        // NOT $1050 (which would be adding the credit to the final balance)
        // This verifies: initialBalance ($950) + creditAmount ($50) = finalBalance ($1000)
        Assertions.assertEquals(finalBalance, actualBalance, 0.01f,
                "Transaction balance should be $1000 (account balance after +$50 credit applied to initial $950)");

        // Additional verification: the math should work out
        Assertions.assertEquals(expectedInitialBalance + creditAmount, finalBalance, 0.01f,
                "Initial balance + credit amount should equal final balance");
    }

    @Test
    void initialBalanceCalculationWithSingleDebit() {
        // Setup: OFX file reports final balance of $1000 after a single -$50 debit
        // The initial balance must have been $1050 (because $1050 - $50 = $1000)
        // This test verifies that the transaction's balance field is set to the
        // account balance AFTER the transaction ($1000, not $1050)
        final Account testAccount = accountDao.insert(TestUtils.createRandomAccount()).get();
        final Category testCategory = categoryDao.insert(new Category("Test Category")).get();
        final float finalBalance = 1000.0f;
        final float debitAmount = -50.0f;
        final float expectedInitialBalance = 1050.0f; // Calculated: finalBalance - debitAmount

        final OfxAccount ofxAccount = TestUtils.accountToOfxAccount(testAccount);
        final OfxTransaction debit = createOfxTransaction("FIT1", debitAmount, LocalDate.of(2023, 1, 15), ofxAccount);
        final OfxBalance ofxBalance = OfxBalance.newBuilder().setAmount(finalBalance).build();
        final List<OfxExport> ofxExports = Collections.singletonList(
                new OfxExport(ofxAccount, ofxBalance, Collections.singletonList(debit))
        );

        // Execute
        final SpyCli spyCli = new SpyCli(testCategory);
        final TransactionCategoryService transactionCategoryService = createTransactionCategoryService(
                categoryDao, categorizedTransactionDao, spyCli);
        final TransactionImportService transactionImportService = new TransactionImportService(
                spyCli, null, accountDao, transactionCleanerFactory, connection,
                categorizedTransactionDao, transactionCategoryService, categoryDao,
                transferMatchingService, transferDao, transactionTokenDao, tokenNormalizer);

        final List<CategorizedTransaction> result = transactionImportService.categorizeTransactions(ofxExports);

        // Verify: Transaction balance equals final balance (the balance AFTER the transaction)
        Assertions.assertEquals(1, result.size());
        final float actualBalance = result.getFirst().getTransaction().getBalance();

        // The transaction balance should be $1000 (the account balance after the -$50 debit was applied)
        // NOT $950 (which would be subtracting the debit from the final balance again)
        // This verifies: initialBalance ($1050) + debitAmount (-$50) = finalBalance ($1000)
        Assertions.assertEquals(finalBalance, actualBalance, 0.01f,
                "Transaction balance should be $1000 (account balance after -$50 debit applied to initial $1050)");

        // Additional verification: the math should work out
        Assertions.assertEquals(expectedInitialBalance + debitAmount, finalBalance, 0.01f,
                "Initial balance + debit amount should equal final balance");
    }

    @Test
    void runningBalanceWithMultipleTransactions() {
        // Setup: Initial $1000, transactions of -$100, +$50, -$200
        // Expected balances: $900, $950, $750
        final Account testAccount = accountDao.insert(TestUtils.createRandomAccount()).get();
        final Category testCategory = categoryDao.insert(new Category("Test Category")).get();
        final float finalBalance = 750.0f;

        final OfxAccount ofxAccount = TestUtils.accountToOfxAccount(testAccount);
        final OfxTransaction debit1 = createOfxTransaction("FIT1", -100.0f, LocalDate.of(2023, 1, 1), ofxAccount);
        final OfxTransaction credit = createOfxTransaction("FIT2", 50.0f, LocalDate.of(2023, 1, 2), ofxAccount);
        final OfxTransaction debit2 = createOfxTransaction("FIT3", -200.0f, LocalDate.of(2023, 1, 3), ofxAccount);

        final OfxBalance ofxBalance = OfxBalance.newBuilder().setAmount(finalBalance).build();
        final List<OfxExport> ofxExports = Collections.singletonList(
                new OfxExport(ofxAccount, ofxBalance, Arrays.asList(debit1, credit, debit2))
        );

        // Execute
        final SpyCli spyCli = new SpyCli(testCategory);
        final TransactionCategoryService transactionCategoryService = createTransactionCategoryService(
                categoryDao, categorizedTransactionDao, spyCli);
        final TransactionImportService transactionImportService = new TransactionImportService(
                spyCli, null, accountDao, transactionCleanerFactory, connection,
                categorizedTransactionDao, transactionCategoryService, categoryDao,
                transferMatchingService, transferDao, transactionTokenDao, tokenNormalizer);

        final List<CategorizedTransaction> result = transactionImportService.categorizeTransactions(ofxExports);

        // Verify: Three transactions with correct running balances
        Assertions.assertEquals(3, result.size());

        // Sort by date to verify in chronological order
        result.sort((t1, t2) -> t1.getTransaction().getDate().compareTo(t2.getTransaction().getDate()));

        Assertions.assertEquals(900.0f, result.get(0).getTransaction().getBalance(), 0.01f,
                "First transaction balance should be $900");
        Assertions.assertEquals(950.0f, result.get(1).getTransaction().getBalance(), 0.01f,
                "Second transaction balance should be $950");
        Assertions.assertEquals(750.0f, result.get(2).getTransaction().getBalance(), 0.01f,
                "Third transaction balance should be $750");
    }

    @Test
    void transactionsProcessedInDateOrderRegardlessOfOfxOrder() {
        // Setup: Transactions in OFX file are out of chronological order
        // Balance calculation should process them in date order
        final Account testAccount = accountDao.insert(TestUtils.createRandomAccount()).get();
        final Category testCategory = categoryDao.insert(new Category("Test Category")).get();
        final float finalBalance = 750.0f;

        final OfxAccount ofxAccount = TestUtils.accountToOfxAccount(testAccount);
        // Create transactions out of order in the list
        final OfxTransaction jan3 = createOfxTransaction("FIT3", -200.0f, LocalDate.of(2023, 1, 3), ofxAccount);
        final OfxTransaction jan1 = createOfxTransaction("FIT1", -100.0f, LocalDate.of(2023, 1, 1), ofxAccount);
        final OfxTransaction jan2 = createOfxTransaction("FIT2", 50.0f, LocalDate.of(2023, 1, 2), ofxAccount);

        final OfxBalance ofxBalance = OfxBalance.newBuilder().setAmount(finalBalance).build();
        // Pass transactions in non-chronological order
        final List<OfxExport> ofxExports = Collections.singletonList(
                new OfxExport(ofxAccount, ofxBalance, Arrays.asList(jan3, jan1, jan2))
        );

        // Execute
        final SpyCli spyCli = new SpyCli(testCategory);
        final TransactionCategoryService transactionCategoryService = createTransactionCategoryService(
                categoryDao, categorizedTransactionDao, spyCli);
        final TransactionImportService transactionImportService = new TransactionImportService(
                spyCli, null, accountDao, transactionCleanerFactory, connection,
                categorizedTransactionDao, transactionCategoryService, categoryDao,
                transferMatchingService, transferDao, transactionTokenDao, tokenNormalizer);

        final List<CategorizedTransaction> result = transactionImportService.categorizeTransactions(ofxExports);

        // Verify: Balances calculated in chronological order despite OFX file order
        Assertions.assertEquals(3, result.size());

        // Find each transaction by fitId
        final CategorizedTransaction ct1 = result.stream()
                .filter(ct -> ct.getTransaction().getFitId().equals("FIT1"))
                .findFirst().orElseThrow();
        final CategorizedTransaction ct2 = result.stream()
                .filter(ct -> ct.getTransaction().getFitId().equals("FIT2"))
                .findFirst().orElseThrow();
        final CategorizedTransaction ct3 = result.stream()
                .filter(ct -> ct.getTransaction().getFitId().equals("FIT3"))
                .findFirst().orElseThrow();

        Assertions.assertEquals(900.0f, ct1.getTransaction().getBalance(), 0.01f,
                "Jan 1 transaction should have balance $900");
        Assertions.assertEquals(950.0f, ct2.getTransaction().getBalance(), 0.01f,
                "Jan 2 transaction should have balance $950");
        Assertions.assertEquals(750.0f, ct3.getTransaction().getBalance(), 0.01f,
                "Jan 3 transaction should have balance $750");
    }

    @Test
    void transactionsSpanningYearBoundary() {
        // Setup: Transactions from Dec 31 and Jan 1
        final Account testAccount = accountDao.insert(TestUtils.createRandomAccount()).get();
        final Category testCategory = categoryDao.insert(new Category("Test Category")).get();
        final float finalBalance = 900.0f;

        final OfxAccount ofxAccount = TestUtils.accountToOfxAccount(testAccount);
        final OfxTransaction dec31 = createOfxTransaction("FIT1", -100.0f, LocalDate.of(2022, 12, 31), ofxAccount);
        final OfxTransaction jan1 = createOfxTransaction("FIT2", 50.0f, LocalDate.of(2023, 1, 1), ofxAccount);

        final OfxBalance ofxBalance = OfxBalance.newBuilder().setAmount(finalBalance).build();
        // Pass in reverse order to verify date sorting works across year boundary
        final List<OfxExport> ofxExports = Collections.singletonList(
                new OfxExport(ofxAccount, ofxBalance, Arrays.asList(jan1, dec31))
        );

        // Execute
        final SpyCli spyCli = new SpyCli(testCategory);
        final TransactionCategoryService transactionCategoryService = createTransactionCategoryService(
                categoryDao, categorizedTransactionDao, spyCli);
        final TransactionImportService transactionImportService = new TransactionImportService(
                spyCli, null, accountDao, transactionCleanerFactory, connection,
                categorizedTransactionDao, transactionCategoryService, categoryDao,
                transferMatchingService, transferDao, transactionTokenDao, tokenNormalizer);

        final List<CategorizedTransaction> result = transactionImportService.categorizeTransactions(ofxExports);

        // Verify: Transactions processed in correct chronological order
        Assertions.assertEquals(2, result.size());

        final CategorizedTransaction dec31Result = result.stream()
                .filter(ct -> ct.getTransaction().getFitId().equals("FIT1"))
                .findFirst().orElseThrow();
        final CategorizedTransaction jan1Result = result.stream()
                .filter(ct -> ct.getTransaction().getFitId().equals("FIT2"))
                .findFirst().orElseThrow();

        // Dec 31 should have lower balance than Jan 1
        Assertions.assertTrue(dec31Result.getTransaction().getBalance() < jan1Result.getTransaction().getBalance(),
                "Dec 31 balance should be less than Jan 1 balance");
        Assertions.assertEquals(finalBalance, jan1Result.getTransaction().getBalance(), 0.01f,
                "Jan 1 transaction should have final balance");
    }

    // Helper method to create OFX transactions
    private OfxTransaction createOfxTransaction(String fitId, float amount, LocalDate date, OfxAccount account) {
        final TransactionType type = amount >= 0 ? TransactionType.CREDIT : TransactionType.DEBIT;
        return OfxTransaction.newBuilder()
                .setFitId(fitId)
                .setAmount(amount)
                .setName("Test Transaction " + fitId)
                .setType(type)
                .setDate(date)
                .setAccount(account)
                .build();
    }

    // Spy CLI for testing
    private static class SpyCli extends ca.jonathanfritz.ofxcat.cli.CLI {
        private final Category category;

        public SpyCli(Category category) {
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
}
