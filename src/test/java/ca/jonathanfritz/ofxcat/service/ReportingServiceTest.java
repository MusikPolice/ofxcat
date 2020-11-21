package ca.jonathanfritz.ofxcat.service;

import ca.jonathanfritz.ofxcat.cli.CLI;
import ca.jonathanfritz.ofxcat.datastore.AccountDao;
import ca.jonathanfritz.ofxcat.datastore.CategoryDao;
import ca.jonathanfritz.ofxcat.datastore.dto.Account;
import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.Matchers.anyListOf;
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
                        .setAccountId(accountId)
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
}