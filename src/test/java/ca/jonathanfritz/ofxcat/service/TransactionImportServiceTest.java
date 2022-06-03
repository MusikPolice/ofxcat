package ca.jonathanfritz.ofxcat.service;

import ca.jonathanfritz.ofxcat.AbstractDatabaseTest;
import ca.jonathanfritz.ofxcat.TestUtils;
import ca.jonathanfritz.ofxcat.cleaner.TransactionCleanerFactory;
import ca.jonathanfritz.ofxcat.cli.CLI;
import ca.jonathanfritz.ofxcat.datastore.AccountDao;
import ca.jonathanfritz.ofxcat.datastore.CategorizedTransactionDao;
import ca.jonathanfritz.ofxcat.datastore.CategoryDao;
import ca.jonathanfritz.ofxcat.datastore.dto.Account;
import ca.jonathanfritz.ofxcat.datastore.dto.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import ca.jonathanfritz.ofxcat.io.OfxAccount;
import ca.jonathanfritz.ofxcat.io.OfxBalance;
import ca.jonathanfritz.ofxcat.io.OfxExport;
import ca.jonathanfritz.ofxcat.io.OfxTransaction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.*;

class TransactionImportServiceTest extends AbstractDatabaseTest {

    private final TransactionCleanerFactory transactionCleanerFactory;
    private final AccountDao accountDao;
    private final CategoryDao categoryDao;
    private final CategorizedTransactionDao categorizedTransactionDao;
    private final TransferMatchingService transferMatchingService;

    public TransactionImportServiceTest() {
        this.transactionCleanerFactory = new TransactionCleanerFactory();
        this.accountDao = injector.getInstance(AccountDao.class);
        this.categoryDao = injector.getInstance(CategoryDao.class);
        this.categorizedTransactionDao = injector.getInstance(CategorizedTransactionDao.class);
        this.transferMatchingService = injector.getInstance(TransferMatchingService.class);
    }

    @Test
    void categorizeTransactionsSingleTransactionTest() {
        // there is one existing account and category
        final String fitId = UUID.randomUUID().toString();
        final Account testAccount = accountDao.insert(TestUtils.createRandomAccount()).get();
        final Category testCategory = categoryDao.insert(new Category("Test Category")).get();

        // create an OFX file that contains a new transaction belonging to both
        final CategorizedTransaction testTransaction = new CategorizedTransaction(TestUtils.createRandomTransaction(testAccount, fitId), testCategory);
        final OfxAccount ofxAccount = TestUtils.accountToOfxAccount(testAccount);
        final OfxTransaction transaction = TestUtils.transactionToOfxTransaction(testTransaction);
        final List<OfxTransaction> transactions = Collections.singletonList(transaction);
        final List<OfxExport> ofxExports = Collections.singletonList(new OfxExport(ofxAccount, OfxBalance.newBuilder().setAmount(0f).build(), transactions));

        // try to insert it
        final SpyCli spyCli = new SpyCli();
        final TransactionCategoryService transactionCategoryService = new TransactionCategoryService(categoryDao, null, categorizedTransactionDao, connection, spyCli);
        final TransactionImportService transactionImportService = new TransactionImportService(spyCli, null, accountDao, transactionCleanerFactory, connection, categorizedTransactionDao, transactionCategoryService, categoryDao, transferMatchingService);
        final List<CategorizedTransaction> categorizedTransactions = transactionImportService.categorizeTransactions(ofxExports);
        Assertions.assertEquals(1, categorizedTransactions.size());
        Assertions.assertEquals(fitId, categorizedTransactions.get(0).getTransaction().getFitId());
        Assertions.assertEquals(testAccount, categorizedTransactions.get(0).getAccount());
        Assertions.assertEquals(testCategory, categorizedTransactions.get(0).getCategory());

        // the new transaction was printed to the CLI
        Assertions.assertEquals(1, spyCli.getCapturedTransactions().size());
        Assertions.assertEquals(testTransaction.getTransaction().getFitId(), spyCli.getCapturedTransactions().get(0).getFitId());

        // make sure that the transaction was created as expected
        final CategorizedTransaction actual = categorizedTransactionDao.selectByFitId(fitId).get();
        Assertions.assertNotNull(actual.getId());
        Assertions.assertEquals(testCategory, actual.getCategory());
    }

    @Test
    void categorizeTransactionsDuplicateTransactionIsIgnoredTest() {
        // there is one existing transaction with a known fitId
        final String fitId = UUID.randomUUID().toString();
        final Account testAccount = accountDao.insert(TestUtils.createRandomAccount()).get();
        final Category testCategory = categoryDao.insert(new Category("Test Category")).get();
        final CategorizedTransaction testTransaction = categorizedTransactionDao.insert(
                new CategorizedTransaction(TestUtils.createRandomTransaction(testAccount, fitId), testCategory)
        ).get();

        // try to insert the same transaction
        final OfxAccount ofxAccount = TestUtils.accountToOfxAccount(testAccount);
        final OfxTransaction transaction = TestUtils.transactionToOfxTransaction(testTransaction);
        final List<OfxTransaction> transactions = Collections.singletonList(transaction);
        final List<OfxExport> ofxExports = Collections.singletonList(new OfxExport(ofxAccount, OfxBalance.newBuilder().setAmount(0f).build(), transactions));

        // actually run the test
        final TransactionImportService transactionImportService = new TransactionImportService(null, null, accountDao, transactionCleanerFactory, connection, categorizedTransactionDao, null, categoryDao, transferMatchingService);
        final List<CategorizedTransaction> categorizedTransactions = transactionImportService.categorizeTransactions(ofxExports);
        Assertions.assertTrue(categorizedTransactions.isEmpty());

        // make sure that the original transaction still exists
        // this will throw an SQLException if there are two transactions with the same FitID
        final CategorizedTransaction actual = categorizedTransactionDao.selectByFitId(fitId).get();
        Assertions.assertEquals(testTransaction, actual);
    }

    private static class SpyCli extends CLI {

        private final List<Transaction> capturedTransactions = new ArrayList<>();

        public SpyCli() {
            super(null, null);
        }

        @Override
        public void printFoundNewTransaction(Transaction transaction) {
            capturedTransactions.add(transaction);
        }

        @Override
        public Optional<Category> chooseCategoryOrAddNew(List<Category> categories) {
            return Optional.of(categories.get(0));
        }

        @Override
        public void printTransactionCategorizedAs(final Category category) {
            // no op
        }

        public List<Transaction> getCapturedTransactions() {
            return Collections.unmodifiableList(capturedTransactions);
        }
    }
}