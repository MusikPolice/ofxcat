package ca.jonathanfritz.ofxcat;

import ca.jonathanfritz.ofxcat.cleaner.TransactionCleanerFactory;
import ca.jonathanfritz.ofxcat.cli.CLI;
import ca.jonathanfritz.ofxcat.datastore.AccountDao;
import ca.jonathanfritz.ofxcat.io.OfxAccount;
import ca.jonathanfritz.ofxcat.io.OfxTransaction;
import ca.jonathanfritz.ofxcat.transactions.Account;
import ca.jonathanfritz.ofxcat.transactions.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.transactions.Category;
import ca.jonathanfritz.ofxcat.transactions.Transaction;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.time.LocalDate;
import java.util.*;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class OfxCatTest {

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
        when(mockCli.categorizeTransaction(any(Transaction.class))).thenAnswer((Answer<CategorizedTransaction>) invocation -> {
            final Transaction t = (Transaction) invocation.getArguments()[0];
            Assertions.assertEquals(transaction.getName().toUpperCase(), t.getDescription()); // default cleaner capitalizes this field
            Assertions.assertEquals(transaction.getDate(), t.getDate());
            Assertions.assertEquals(transaction.getAmount(), t.getAmount());
            Assertions.assertEquals(transaction.getAccount(), ofxAccount);
            return new CategorizedTransaction(t, category);
        });

        // there is only one transaction, and it belongs to a known account
        final Set<OfxTransaction> transactions = new HashSet<>(Collections.singletonList(transaction));
        final Map<OfxAccount, Set<OfxTransaction>> transactionMap = Map.of(ofxAccount, transactions);
        final AccountDao mockAccountDao = Mockito.mock(AccountDao.class);
        when(mockAccountDao.selectByAccountNumber(accountId)).thenReturn(Optional.of(Account.newBuilder()
                .setId(1L)
                .setAccountId(accountId)
                .setBankId(bankId)
                .setAccountType("Savings")
                .setName("Savings")
                .build()));

        // flyway will migrate the database schema
        final Flyway mockFlyway = Mockito.mock(Flyway.class);
        when(mockFlyway.migrate()).thenReturn(1);

        // actually run the test
        final OfxCat ofxCat = new OfxCat(transactionCleanerFactory, mockCli, null, mockFlyway, mockAccountDao);
        final Set<CategorizedTransaction> categorizedTransactions = ofxCat.categorizeTransactions(transactionMap);

        Assertions.assertEquals(1, categorizedTransactions.size());
        final CategorizedTransaction categorizedTransaction = categorizedTransactions.stream().findFirst().get();
        Assertions.assertEquals(category, categorizedTransaction.getCategory());
        Assertions.assertEquals(transaction.getName().toUpperCase(), categorizedTransaction.getDescription()); // default cleaner capitalizes this field
        Assertions.assertEquals(transaction.getDate(), categorizedTransaction.getDate());
        Assertions.assertEquals(transaction.getAmount(), categorizedTransaction.getAmount());
        Assertions.assertEquals(transaction.getAccount(), ofxAccount);

        Mockito.verify(mockCli, times(1)).categorizeTransaction(any(Transaction.class));
        Mockito.verify(mockFlyway, times(1)).migrate();
        Mockito.verify(mockAccountDao, times(1)).selectByAccountNumber(anyString());
        Mockito.verifyNoMoreInteractions(mockFlyway, mockAccountDao);
    }

    @Test
    void categorizeTransactionsCreatesNewAccountInDatabaseTest() {
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
        when(mockCli.categorizeTransaction(any(Transaction.class))).thenAnswer((Answer<CategorizedTransaction>) invocation -> {
            final Transaction t = (Transaction) invocation.getArguments()[0];
            Assertions.assertEquals(transaction.getName().toUpperCase(), t.getDescription()); // default cleaner capitalizes this field
            Assertions.assertEquals(transaction.getDate(), t.getDate());
            Assertions.assertEquals(transaction.getAmount(), t.getAmount());
            Assertions.assertEquals(transaction.getAccount(), ofxAccount);
            return new CategorizedTransaction(t, category);
        });

        // the cli will return a known name for the account that the transaction belongs to
        final String accountName = UUID.randomUUID().toString();
        when(mockCli.assignAccountName(ofxAccount)).thenReturn(Account.newBuilder()
                .setName(accountName)
                .setBankId(bankId)
                .setAccountId(accountId)
                .setAccountType(ofxAccount.getAccountType())
                .build());

        // there is only one transaction, and it belongs to an unknown account
        final Set<OfxTransaction> transactions = new HashSet<>(Collections.singletonList(transaction));
        final Map<OfxAccount, Set<OfxTransaction>> transactionMap = Map.of(ofxAccount, transactions);
        final AccountDao mockAccountDao = Mockito.mock(AccountDao.class);
        when(mockAccountDao.selectByAccountNumber(accountId)).thenReturn(Optional.empty());
        when(mockAccountDao.insert(any(Account.class))).thenAnswer((Answer<Optional<Account>>) invocation -> {
            final Account account = invocation.getArgumentAt(0, Account.class);
            Assertions.assertEquals(accountId, account.getAccountId());
            Assertions.assertEquals(bankId, account.getBankId());
            Assertions.assertEquals(ofxAccount.getAccountType(), account.getAccountType());
            Assertions.assertEquals(accountName, account.getName());
            return Optional.of(Account.newBuilder(account)
                    .setId(1L)
                    .build());
        });

        // flyway will migrate the database schema
        final Flyway mockFlyway = Mockito.mock(Flyway.class);
        when(mockFlyway.migrate()).thenReturn(1);

        // actually run the test
        final OfxCat ofxCat = new OfxCat(transactionCleanerFactory, mockCli, null, mockFlyway, mockAccountDao);
        final Set<CategorizedTransaction> categorizedTransactions = ofxCat.categorizeTransactions(transactionMap);

        Assertions.assertEquals(1, categorizedTransactions.size());
        final CategorizedTransaction categorizedTransaction = categorizedTransactions.stream().findFirst().get();
        Assertions.assertEquals(category, categorizedTransaction.getCategory());
        Assertions.assertEquals(transaction.getName().toUpperCase(), categorizedTransaction.getDescription()); // default cleaner capitalizes this field
        Assertions.assertEquals(transaction.getDate(), categorizedTransaction.getDate());
        Assertions.assertEquals(transaction.getAmount(), categorizedTransaction.getAmount());
        Assertions.assertEquals(transaction.getAccount(), ofxAccount);

        Mockito.verify(mockCli, times(1)).categorizeTransaction(any(Transaction.class));
        Mockito.verify(mockCli, times(1)).assignAccountName(any(OfxAccount.class));
        Mockito.verify(mockFlyway, times(1)).migrate();
        Mockito.verify(mockAccountDao, times(1)).selectByAccountNumber(anyString());
        Mockito.verify(mockAccountDao, times(1)).insert(any(Account.class));
        Mockito.verifyNoMoreInteractions(mockFlyway, mockAccountDao);
    }
}