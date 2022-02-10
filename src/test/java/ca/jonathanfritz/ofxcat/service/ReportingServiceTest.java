package ca.jonathanfritz.ofxcat.service;

import ca.jonathanfritz.ofxcat.cli.CLI;
import ca.jonathanfritz.ofxcat.datastore.AccountDao;
import ca.jonathanfritz.ofxcat.datastore.CategorizedTransactionDao;
import ca.jonathanfritz.ofxcat.datastore.CategoryDao;
import ca.jonathanfritz.ofxcat.datastore.dto.Account;
import ca.jonathanfritz.ofxcat.datastore.dto.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.*;

import static org.mockito.Mockito.*;

class ReportingServiceTest {

    @Test
    void reportAccountsTest() {
        final String accountName = UUID.randomUUID().toString();
        final String accountId = UUID.randomUUID().toString();
        final String bankId = UUID.randomUUID().toString();
        final String accountType = UUID.randomUUID().toString();

        // mock account dao will return one account
        final AccountDao mockAccountDao = Mockito.mock(AccountDao.class);
        when(mockAccountDao.select()).thenReturn(Collections.singletonList(
                Account.newBuilder()
                        .setName(accountName)
                        .setAccountNumber(accountId)
                        .setBankId(bankId)
                        .setAccountType(accountType)
                        .build()
        ));

        // we expect that account to be printed to the CLI
        final CLI mockCli = Mockito.mock(CLI.class);
        doNothing().when(mockCli).println(anyListOf(String.class));

        // run the test
        final ReportingService reportingService = new ReportingService(null, mockAccountDao, null, mockCli);
        reportingService.reportAccounts();

        // ensure that the right thing was printed
        final List<String> expectedLines = Arrays.asList(
                "Account Name,Account Number,Bank Id,Account Type",
                String.format("%s,%s,%s,%s", accountName, accountId, bankId, accountType)
        );
        Mockito.verify(mockAccountDao, times(1)).select();
        Mockito.verify(mockCli, times(1)).println(expectedLines);
        Mockito.verifyNoMoreInteractions(mockAccountDao, mockCli);
    }

    @Test
    void reportCategoriesTest() {
        final String categoryName = UUID.randomUUID().toString();

        // mock category dao will return one category
        final CategoryDao mockCategoryDao = Mockito.mock(CategoryDao.class);
        when(mockCategoryDao.select()).thenReturn(Collections.singletonList(
                new Category(categoryName)
        ));

        // we expect that category to be printed to the CLI
        final CLI mockCli = Mockito.mock(CLI.class);
        doNothing().when(mockCli).println(anyListOf(String.class));

        // run the test
        final ReportingService reportingService = new ReportingService(null, null, mockCategoryDao, mockCli);
        reportingService.reportCategories();

        // ensure that the right thing was printed - category name should be uppercase!
        final List<String> expectedLines = Arrays.asList(
                "Category Name",
                categoryName.toUpperCase()
        );
        Mockito.verify(mockCategoryDao, times(1)).select();
        Mockito.verify(mockCli, times(1)).println(expectedLines);
        Mockito.verifyNoMoreInteractions(mockCategoryDao, mockCli);
    }

    @Test
    void reportTransactionsTest() {
        final Category groceries = new Category("GROCERIES");
        final Category restaurants = new Category("RESTAURANTS");
        final CategorizedTransaction t1 = new CategorizedTransaction(Transaction.newBuilder(UUID.randomUUID().toString()).setAmount(10f).build(), groceries);
        final CategorizedTransaction t2 = new CategorizedTransaction(Transaction.newBuilder(UUID.randomUUID().toString()).setAmount(5f).build(), groceries);
        final CategorizedTransaction t3 = new CategorizedTransaction(Transaction.newBuilder(UUID.randomUUID().toString()).setAmount(7f).build(), restaurants);

        // mock transactions dao will return some categorized transactions
        final CategorizedTransactionDao mockTransactionsDao = Mockito.mock(CategorizedTransactionDao.class);
        final LocalDate startDate = LocalDate.now().minusDays(3);
        final LocalDate endDate = LocalDate.now();
        when(mockTransactionsDao.selectGroupByCategory(startDate, endDate)).thenReturn(Map.of(
                groceries, Arrays.asList(t1, t2),
                restaurants, Collections.singletonList(t3)
        ));

        // we expect the summarized transactions to be printed to the CLI
        final CLI mockCli = Mockito.mock(CLI.class);
        doNothing().when(mockCli).println(anyListOf(String.class));

        // run the test
        final ReportingService reportingService = new ReportingService(mockTransactionsDao, null, null, mockCli);
        reportingService.reportTransactions(startDate, endDate);

        // ensure that the right thing was printed - categories should be ordered by amount spent descending
        final List<String> expectedLines = Arrays.asList(
                "Category,Amount Spent",
                String.format("%s,$15.00", groceries.getName()),
                String.format("%s,$7.00", restaurants.getName())
        );
        Mockito.verify(mockTransactionsDao, times(1)).selectGroupByCategory(any(LocalDate.class), any(LocalDate.class));
        Mockito.verify(mockCli, times(1)).println(anyString());
        Mockito.verify(mockCli, times(1)).println(expectedLines);
        Mockito.verifyNoMoreInteractions(mockTransactionsDao, mockCli);
    }
}