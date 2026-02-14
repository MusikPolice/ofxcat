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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for multi-account scenarios including transfers between accounts.
 */
class MultiAccountIntegrationTest extends AbstractDatabaseTest {

    private final TransactionCleanerFactory transactionCleanerFactory;
    private final AccountDao accountDao;
    private final CategoryDao categoryDao;
    private final CategorizedTransactionDao categorizedTransactionDao;
    private final TransactionTokenDao transactionTokenDao;
    private final TransferMatchingService transferMatchingService;
    private final TransferDao transferDao;

    MultiAccountIntegrationTest() {
        this.transactionCleanerFactory = new TransactionCleanerFactory();
        this.accountDao = injector.getInstance(AccountDao.class);
        this.categoryDao = injector.getInstance(CategoryDao.class);
        this.categorizedTransactionDao = injector.getInstance(CategorizedTransactionDao.class);
        this.transactionTokenDao = new TransactionTokenDao();
        this.transferMatchingService = injector.getInstance(TransferMatchingService.class);
        this.transferDao = injector.getInstance(TransferDao.class);
    }

    /**
     * Test importing from two accounts with a transfer between them.
     * This simulates the common scenario of moving money between checking and savings.
     */
    @Test
    void transferBetweenCheckingAndSavings() {
        // Setup: Create checking and savings accounts
        Account checking =
                accountDao.insert(TestUtils.createRandomAccount("Checking")).orElseThrow();
        Account savings =
                accountDao.insert(TestUtils.createRandomAccount("Savings")).orElseThrow();

        LocalDate transferDate = LocalDate.now();
        float transferAmount = 500.00f;

        // Create the transfer transactions (source is negative, sink is positive)
        OfxTransaction sourceTransaction = OfxTransaction.newBuilder()
                .setFitId(UUID.randomUUID().toString())
                .setName("TRANSFER TO SAVINGS")
                .setAmount(-transferAmount)
                .setDate(transferDate)
                .setType(TransactionType.XFER)
                .build();

        OfxTransaction sinkTransaction = OfxTransaction.newBuilder()
                .setFitId(UUID.randomUUID().toString())
                .setName("TRANSFER FROM CHECKING")
                .setAmount(transferAmount)
                .setDate(transferDate)
                .setType(TransactionType.XFER)
                .build();

        // Create OFX exports for both accounts
        OfxBalance checkingBalance = OfxBalance.newBuilder().setAmount(1000.00f).build();
        OfxBalance savingsBalance = OfxBalance.newBuilder().setAmount(5500.00f).build();

        List<OfxExport> ofxExports = List.of(
                new OfxExport(TestUtils.accountToOfxAccount(checking), checkingBalance, List.of(sourceTransaction)),
                new OfxExport(TestUtils.accountToOfxAccount(savings), savingsBalance, List.of(sinkTransaction)));

        // Execute import
        SpyCli spyCli = new SpyCli();
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

        // Verify: Both transactions imported
        assertEquals(2, imported.size(), "Both transfer transactions should be imported");

        // Verify: Transfer was detected and recorded
        assertEquals(1, spyCli.getTransferCount(), "One transfer should be detected");

        // Verify: Transactions are categorized as TRANSFER
        assertTrue(
                imported.stream().allMatch(t -> t.getCategory().equals(Category.TRANSFER)),
                "Both transactions should be categorized as TRANSFER");

        // Verify: Transfer is stored in database
        Optional<Transfer> storedTransfer = transferDao.selectByFitId(sourceTransaction.getFitId());
        assertTrue(storedTransfer.isPresent(), "Transfer should be stored in database");
        assertEquals(
                sourceTransaction.getFitId(), storedTransfer.get().getSource().getFitId());
        assertEquals(sinkTransaction.getFitId(), storedTransfer.get().getSink().getFitId());
    }

    /**
     * Test importing from multiple accounts in a single import session.
     * Simulates downloading statements from multiple accounts at once.
     */
    @Test
    void importFromMultipleAccountsSimultaneously() {
        // Setup: Create three accounts
        Account checking = accountDao
                .insert(TestUtils.createRandomAccount("Primary Checking"))
                .orElseThrow();
        Account savings =
                accountDao.insert(TestUtils.createRandomAccount("Savings")).orElseThrow();
        Account creditCard =
                accountDao.insert(TestUtils.createRandomAccount("Credit Card")).orElseThrow();

        Category expense = categoryDao.insert(new Category("General Expense")).orElseThrow();

        LocalDate date = LocalDate.now();

        // Create transactions for each account
        List<OfxTransaction> checkingTransactions = List.of(
                createOfxTransaction("PAYCHECK", 3000.00f, date, TransactionType.CREDIT),
                createOfxTransaction("RENT", -1500.00f, date.plusDays(1), TransactionType.DEBIT));

        List<OfxTransaction> savingsTransactions =
                List.of(createOfxTransaction("INTEREST", 5.50f, date, TransactionType.CREDIT));

        List<OfxTransaction> creditCardTransactions = List.of(
                createOfxTransaction("AMAZON", -75.00f, date, TransactionType.DEBIT),
                createOfxTransaction("GAS STATION", -45.00f, date.plusDays(2), TransactionType.DEBIT),
                createOfxTransaction("PAYMENT THANK YOU", 500.00f, date.plusDays(5), TransactionType.CREDIT));

        // Create OFX exports
        List<OfxExport> ofxExports = List.of(
                new OfxExport(
                        TestUtils.accountToOfxAccount(checking),
                        OfxBalance.newBuilder().setAmount(1500.00f).build(),
                        checkingTransactions),
                new OfxExport(
                        TestUtils.accountToOfxAccount(savings),
                        OfxBalance.newBuilder().setAmount(10005.50f).build(),
                        savingsTransactions),
                new OfxExport(
                        TestUtils.accountToOfxAccount(creditCard),
                        OfxBalance.newBuilder().setAmount(-620.00f).build(),
                        creditCardTransactions));

        // Execute
        SpyCli spyCli = new SpyCli(expense);
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

        // Verify: All 6 transactions imported
        assertEquals(6, imported.size(), "All 6 transactions should be imported");

        // Verify: Transactions distributed across accounts correctly
        long checkingCount =
                imported.stream().filter(t -> t.getAccount().equals(checking)).count();
        long savingsCount =
                imported.stream().filter(t -> t.getAccount().equals(savings)).count();
        long creditCardCount =
                imported.stream().filter(t -> t.getAccount().equals(creditCard)).count();

        assertEquals(2, checkingCount, "Checking should have 2 transactions");
        assertEquals(1, savingsCount, "Savings should have 1 transaction");
        assertEquals(3, creditCardCount, "Credit card should have 3 transactions");
    }

    /**
     * Test that transfers are correctly matched across separate import sessions.
     * Simulates the case where user imports checking first, then savings later.
     */
    @Test
    void transferMatchedAcrossSeparateImports() {
        // Setup
        Account checking =
                accountDao.insert(TestUtils.createRandomAccount("Checking")).orElseThrow();
        Account savings =
                accountDao.insert(TestUtils.createRandomAccount("Savings")).orElseThrow();

        LocalDate transferDate = LocalDate.now();
        float transferAmount = 200.00f;

        // First import: Checking account with outgoing transfer
        OfxTransaction sourceTransaction = OfxTransaction.newBuilder()
                .setFitId(UUID.randomUUID().toString())
                .setName("TRANSFER TO SAVINGS")
                .setAmount(-transferAmount)
                .setDate(transferDate)
                .setType(TransactionType.XFER)
                .build();

        List<OfxExport> checkingExport = List.of(new OfxExport(
                TestUtils.accountToOfxAccount(checking),
                OfxBalance.newBuilder().setAmount(800.00f).build(),
                List.of(sourceTransaction)));

        SpyCli spyCli1 = new SpyCli();
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

        List<CategorizedTransaction> firstImport = tis1.categorizeTransactions(checkingExport);

        // Verify first import: Transaction imported, no transfer yet
        assertEquals(1, firstImport.size());
        assertEquals(0, spyCli1.getTransferCount(), "No transfer match yet (sink not imported)");

        // Second import: Savings account with incoming transfer
        OfxTransaction sinkTransaction = OfxTransaction.newBuilder()
                .setFitId(UUID.randomUUID().toString())
                .setName("TRANSFER FROM CHECKING")
                .setAmount(transferAmount)
                .setDate(transferDate)
                .setType(TransactionType.XFER)
                .build();

        List<OfxExport> savingsExport = List.of(new OfxExport(
                TestUtils.accountToOfxAccount(savings),
                OfxBalance.newBuilder().setAmount(5200.00f).build(),
                List.of(sinkTransaction)));

        SpyCli spyCli2 = new SpyCli();
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

        List<CategorizedTransaction> secondImport = tis2.categorizeTransactions(savingsExport);

        // Verify second import: Transaction imported, transfer now matched
        assertEquals(1, secondImport.size());
        assertEquals(1, spyCli2.getTransferCount(), "Transfer should now be matched");

        // Verify transfer is stored
        Optional<Transfer> storedTransfer = transferDao.selectByFitId(sourceTransaction.getFitId());
        assertTrue(storedTransfer.isPresent(), "Transfer should be stored in database");
    }

    /**
     * Test handling of transactions from a new account (account not yet in database).
     */
    @Test
    void importFromNewAccountCreatesAccount() {
        // Setup: Create a category but NO account
        Category category = categoryDao.insert(new Category("Test Category")).orElseThrow();

        // Create transactions for a "new" account
        String newBankId = "NEW_BANK_123";
        String newAccountNumber = "9999888877776666";

        OfxAccount newOfxAccount = OfxAccount.newBuilder()
                .setBankId(newBankId)
                .setAccountId(newAccountNumber)
                .setAccountType("CHECKING")
                .build();

        List<OfxTransaction> transactions =
                List.of(createOfxTransaction("TEST MERCHANT", -50.00f, LocalDate.now(), TransactionType.DEBIT));

        List<OfxExport> ofxExports = List.of(new OfxExport(
                newOfxAccount, OfxBalance.newBuilder().setAmount(500.00f).build(), transactions));

        // Execute with a CLI that provides account name
        SpyCliWithAccountNaming spyCli = new SpyCliWithAccountNaming(category, "My New Account");
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

        // Verify: Transaction imported
        assertEquals(1, imported.size());

        // Verify: New account was created in database
        List<Account> accounts = accountDao.select();
        assertTrue(
                accounts.stream()
                        .anyMatch(a -> a.getAccountNumber().equals(newAccountNumber)
                                && a.getBankId().equals(newBankId)),
                "New account should be created in database");
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

    // ==================== Test Doubles ====================

    private static class SpyCli extends CLI {
        private final Category categoryToReturn;
        private int transferCount = 0;
        private int transactionCount = 0;

        SpyCli() {
            super(null, null);
            this.categoryToReturn = null;
        }

        SpyCli(Category categoryToReturn) {
            super(null, null);
            this.categoryToReturn = categoryToReturn;
        }

        @Override
        public void printFoundNewTransaction(Transaction transaction) {
            transactionCount++;
        }

        @Override
        public Optional<Category> chooseCategoryOrAddNew(List<Category> categories) {
            return chooseCategory(categories);
        }

        @Override
        public Optional<Category> chooseCategoryOrChooseAnother(List<Category> categories) {
            return chooseCategory(categories);
        }

        private Optional<Category> chooseCategory(List<Category> categories) {
            if (categoryToReturn != null) {
                return Optional.of(categoryToReturn);
            }
            return categories.isEmpty() ? Optional.of(Category.UNKNOWN) : Optional.of(categories.get(0));
        }

        @Override
        public void printTransactionCategorizedAs(Category category) {
            // no-op
        }

        @Override
        public void printFoundNewTransfer(Transfer transfer) {
            transferCount++;
        }

        public int getTransferCount() {
            return transferCount;
        }

        public int getTransactionCount() {
            return transactionCount;
        }
    }

    private static class SpyCliWithAccountNaming extends SpyCli {
        private final String accountNameToReturn;

        SpyCliWithAccountNaming(Category categoryToReturn, String accountNameToReturn) {
            super(categoryToReturn);
            this.accountNameToReturn = accountNameToReturn;
        }

        @Override
        public Account assignAccountName(ca.jonathanfritz.ofxcat.io.OfxAccount ofxAccount) {
            return Account.newBuilder()
                    .setAccountNumber(ofxAccount.getAccountId())
                    .setBankId(ofxAccount.getBankId())
                    .setAccountType(ofxAccount.getAccountType())
                    .setName(accountNameToReturn)
                    .build();
        }
    }
}
