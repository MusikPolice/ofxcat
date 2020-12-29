package ca.jonathanfritz.ofxcat.cli;

import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import ca.jonathanfritz.ofxcat.service.TransactionCategoryService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

class CLITest {

    @Test
    void categorizeTransactionFuzzySingleMatchTest() {
        // one fuzzy match is returned, user is prompted to verify association
        final TransactionCategoryService transactionCategoryService = Mockito.mock(TransactionCategoryService.class);
        final Category fooCategory = new Category("foo");
        when(transactionCategoryService.getCategoryFuzzy(any(Transaction.class), anyInt()))
                .thenReturn(Collections.singletonList(fooCategory));

        // when prompted, the user confirms
        final TextIOWrapper textIOWrapper = Mockito.mock(TextIOWrapper.class);
        when(textIOWrapper.promptYesNo(anyString()))
                .thenReturn(true);

        final CLI cli = new CLI(null, textIOWrapper, transactionCategoryService);
        cli.categorizeTransactionFuzzy(Transaction.newBuilder().build());

        // the transaction will be categorized appropriately
        verify(transactionCategoryService, times(1)).put(any(Transaction.class), eq(fooCategory));
    }

    @Test
    void categorizeTransactionFuzzyMultipleMatchesTest() {
        // two fuzzy matches are returned, forcing us to prompt the user to choose one
        final TransactionCategoryService transactionCategoryService = Mockito.mock(TransactionCategoryService.class);
        final Category fooCategory = new Category("foo");
        when(transactionCategoryService.getCategoryFuzzy(any(Transaction.class), anyInt()))
                .thenReturn(Arrays.asList(fooCategory, new Category("bar")));

        // when prompted, the user chooses "foo"
        final TextIOWrapper textIOWrapper = Mockito.mock(TextIOWrapper.class);
        when(textIOWrapper.promptChooseString(anyString(), anyListOf(String.class)))
                .thenReturn("foo");

        final CLI cli = new CLI(null, textIOWrapper, transactionCategoryService);
        cli.categorizeTransactionFuzzy(Transaction.newBuilder().build());

        // the transaction will be categorized appropriately
        verify(transactionCategoryService, times(1)).put(any(Transaction.class), eq(fooCategory));
    }

}