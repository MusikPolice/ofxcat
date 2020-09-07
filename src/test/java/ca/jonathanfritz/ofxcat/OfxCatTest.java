package ca.jonathanfritz.ofxcat;

import ca.jonathanfritz.ofxcat.cleaner.TransactionCleanerFactory;
import ca.jonathanfritz.ofxcat.cli.CLI;
import ca.jonathanfritz.ofxcat.data.TransactionCategoryStore;
import ca.jonathanfritz.ofxcat.io.OfxAccount;
import ca.jonathanfritz.ofxcat.io.OfxTransaction;
import ca.jonathanfritz.ofxcat.transactions.Account;
import ca.jonathanfritz.ofxcat.transactions.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.transactions.Category;
import ca.jonathanfritz.ofxcat.transactions.Transaction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.time.LocalDate;
import java.util.*;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

class OfxCatTest {

    /**
     * Ensures that the {@link ca.jonathanfritz.ofxcat.data.TransactionCategoryStore} is saved to disk every time
     * new transactions are processed using {@link OfxCat#categorizeTransactions(Map, Set)}
     */
    @Test
    void categorizeTransactionsSavesTransactionCategoryStoreTest() {
        final TransactionCleanerFactory transactionCleanerFactory = new TransactionCleanerFactory();
        final String bankId = UUID.randomUUID().toString();
        final String accountId = UUID.randomUUID().toString();

        final OfxAccount ofxAccount = OfxAccount.newBuilder()
                .setBankId(bankId)
                .setAccountId(accountId)
                .build();
        final OfxTransaction transaction = OfxTransaction.newBuilder()
                .setDate(LocalDate.now())
                .setName("Some Vendor")
                .setAmount(10.00f)
                .setAccount(ofxAccount)
                .build();

        // every transaction is sorted into the same category
        final Category category = new Category(UUID.randomUUID().toString());
        final CLI mockCli = Mockito.mock(CLI.class);
        Mockito.when(mockCli.categorizeTransaction(any(Transaction.class))).thenAnswer((Answer<CategorizedTransaction>) invocation -> {
            final Transaction t = (Transaction) invocation.getArguments()[0];
            Assertions.assertEquals(transaction.getName().toUpperCase(), t.getDescription()); // default cleaner capitalizes this field
            Assertions.assertEquals(transaction.getDate(), t.getDate());
            Assertions.assertEquals(transaction.getAmount(), t.getAmount());
            Assertions.assertEquals(transaction.getAccount(), ofxAccount);
            return new CategorizedTransaction(t, category);
        });

        // we expect the transaction category store to be saved when we're done
        final TransactionCategoryStore mockTransactionCategoryStore = Mockito.mock(TransactionCategoryStore.class);
        doNothing().when(mockTransactionCategoryStore).save();

        // there is only one transaction, and it belongs to a known account
        final Set<OfxTransaction> transactions = new HashSet<>(Collections.singletonList(transaction));
        final Map<OfxAccount, Set<OfxTransaction>> transactionMap = Map.of(ofxAccount, transactions);
        final Set<Account> accounts = Set.of(Account.newBuilder()
                .setBankId(bankId)
                .setAccountId(accountId)
                .build());

        // actually run the test
        final OfxCat ofxCat = new OfxCat(transactionCleanerFactory, mockCli, null, mockTransactionCategoryStore);
        final Set<CategorizedTransaction> categorizedTransactions = ofxCat.categorizeTransactions(transactionMap, accounts);

        Assertions.assertEquals(1, categorizedTransactions.size());
        final CategorizedTransaction categorizedTransaction = categorizedTransactions.stream().findFirst().get();
        Assertions.assertEquals(category, categorizedTransaction.getCategory());
        Assertions.assertEquals(transaction.getName().toUpperCase(), categorizedTransaction.getDescription()); // default cleaner capitalizes this field
        Assertions.assertEquals(transaction.getDate(), categorizedTransaction.getDate());
        Assertions.assertEquals(transaction.getAmount(), categorizedTransaction.getAmount());
        Assertions.assertEquals(transaction.getAccount(), ofxAccount);

        Mockito.verify(mockCli, times(1)).categorizeTransaction(any(Transaction.class));
        Mockito.verify(mockTransactionCategoryStore, times(1)).save();
        Mockito.verifyNoMoreInteractions(mockCli, mockTransactionCategoryStore);
    }

}