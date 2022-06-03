package ca.jonathanfritz.ofxcat.service;

import ca.jonathanfritz.ofxcat.AbstractDatabaseTest;
import ca.jonathanfritz.ofxcat.TestUtils;
import ca.jonathanfritz.ofxcat.cli.CLI;
import ca.jonathanfritz.ofxcat.datastore.AccountDao;
import ca.jonathanfritz.ofxcat.datastore.CategoryDao;
import ca.jonathanfritz.ofxcat.datastore.dto.Account;
import ca.jonathanfritz.ofxcat.datastore.dto.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

class ReportingServiceTest extends AbstractDatabaseTest {

    private final AccountDao accountDao;
    private final CategoryDao categoryDao;

    ReportingServiceTest() {
        this.accountDao = injector.getInstance(AccountDao.class);
        this.categoryDao = injector.getInstance(CategoryDao.class);
    }

    @Test
    void reportAccountsTest() {
        // create one test account
        final Account testAccount = accountDao.insert(TestUtils.createRandomAccount()).get();

        // we expect that account to be printed to the CLI
        final SpyCli spyCli = new SpyCli();

        // run the test
        final ReportingService reportingService = new ReportingService(null, accountDao, null, spyCli);
        reportingService.reportAccounts();

        // ensure that the right thing was printed
        final List<String> expectedLines = Arrays.asList(
                "Account Name,Account Number,Bank Id,Account Type",
                String.format("%s,%s,%s,%s", testAccount.getName(), testAccount.getAccountNumber(), testAccount.getBankId(), testAccount.getAccountType())
        );
        final List<String> actualLines = spyCli.getCapturedLines();
        Assertions.assertEquals(expectedLines, actualLines);
    }

    @Test
    void reportCategoriesTest() {
        // create a test category
        final Category testCategory = categoryDao.insert(TestUtils.createRandomCategory()).get();

        // we expect that category to be printed to the CLI
        final SpyCli spyCli = new SpyCli();

        // run the test
        final ReportingService reportingService = new ReportingService(null, null, categoryDao, spyCli);
        reportingService.reportCategories();

        // ensure that the right thing was printed - category name should be uppercase!
        final List<String> expectedLines = Arrays.asList(
                "Category Name",
                testCategory.getName().toUpperCase()
        );
        final List<String> actualLines = spyCli.getCapturedLines();
        Assertions.assertEquals(expectedLines, actualLines);
    }

    @Test
    @Disabled("Reporting has been temporarily changed. See https://github.com/MusikPolice/ofxcat/issues/18")
    void reportTransactionsTest() {
        final Category groceries = new Category("GROCERIES");
        final Category restaurants = new Category("RESTAURANTS");
        final CategorizedTransaction t1 = new CategorizedTransaction(Transaction.newBuilder(UUID.randomUUID().toString()).setDate(LocalDate.now().minusDays(1)).setAmount(10f).build(), groceries);
        final CategorizedTransaction t2 = new CategorizedTransaction(Transaction.newBuilder(UUID.randomUUID().toString()).setDate(LocalDate.now().minusDays(1)).setAmount(5f).build(), groceries);
        final CategorizedTransaction t3 = new CategorizedTransaction(Transaction.newBuilder(UUID.randomUUID().toString()).setDate(LocalDate.now().minusDays(1)).setAmount(7f).build(), restaurants);

        // mock transactions dao will return some categorized transactions
        //final CategorizedTransactionDao mockTransactionsDao = Mockito.mock(CategorizedTransactionDao.class);
        final LocalDate startDate = LocalDate.now().minusDays(3);
        final LocalDate endDate = LocalDate.now();
        /*when(mockTransactionsDao.selectGroupByCategory(startDate, endDate)).thenReturn(Map.of(
                groceries, Arrays.asList(t1, t2),
                restaurants, Collections.singletonList(t3)
        ));*/

        // we expect the summarized transactions to be printed to the CLI
        /*final CLI mockCli = Mockito.mock(CLI.class);
        doNothing().when(mockCli).println(anyListOf(String.class));*/

        // run the test
        //final ReportingService reportingService = new ReportingService(mockTransactionsDao, null, null, mockCli);
        //reportingService.reportTransactions(startDate, endDate);

        // ensure that the right thing was printed - categories should be ordered by amount spent descending
        final List<String> expectedLines = Arrays.asList(
                "Category, Spend",
                String.format("%s,$15.00", groceries.getName()),
                String.format("%s,$7.00", restaurants.getName())
        );
        /*Mockito.verify(mockTransactionsDao, times(1)).selectGroupByCategory(any(LocalDate.class), any(LocalDate.class));
        Mockito.verify(mockCli, times(1)).println(anyString());
        Mockito.verify(mockCli, times(1)).println(expectedLines);
        Mockito.verifyNoMoreInteractions(mockTransactionsDao, mockCli);*/
    }

    private static class SpyCli extends CLI {

        private final List<String> capturedLines = new ArrayList<>();

        public SpyCli() {
            super(null, null);
        }

        @Override
        public void println(List<String> lines) {
            capturedLines.addAll(lines);
        }

        public List<String> getCapturedLines() {
            return Collections.unmodifiableList(capturedLines);
        }
    }
}