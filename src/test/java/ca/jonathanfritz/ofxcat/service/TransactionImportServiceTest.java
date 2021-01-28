package ca.jonathanfritz.ofxcat.service;

import ca.jonathanfritz.ofxcat.cleaner.TransactionCleanerFactory;
import ca.jonathanfritz.ofxcat.cli.CLI;
import ca.jonathanfritz.ofxcat.datastore.AccountDao;
import ca.jonathanfritz.ofxcat.datastore.CategorizedTransactionDao;
import ca.jonathanfritz.ofxcat.datastore.utils.DatabaseTransaction;
import ca.jonathanfritz.ofxcat.io.OfxAccount;
import ca.jonathanfritz.ofxcat.io.OfxBalance;
import ca.jonathanfritz.ofxcat.io.OfxExport;
import ca.jonathanfritz.ofxcat.io.OfxTransaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Account;
import ca.jonathanfritz.ofxcat.datastore.dto.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class TransactionImportServiceTest {

    @Test
    void categorizeTransactionsUnknownAccountSuccessTest() throws SQLException {
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

        // this is the final expected balance for the account after all transactions have been processed
        // because there is only one transaction, its balance will be set to this value
        final OfxBalance accountBalance = OfxBalance.newBuilder().setAmount(15.00f).build();

        // every transaction is sorted into the same category
        final Category category = new Category(UUID.randomUUID().toString());
        final CLI mockCli = Mockito.mock(CLI.class);
        when(mockCli.categorizeTransaction(any(Transaction.class))).thenAnswer((Answer<CategorizedTransaction>) invocation -> {
            final Transaction t = (Transaction) invocation.getArguments()[0];
            Assertions.assertEquals(transaction.getName().toUpperCase(), t.getDescription()); // default cleaner capitalizes this field
            Assertions.assertEquals(transaction.getDate(), t.getDate());
            Assertions.assertEquals(transaction.getAmount(), t.getAmount());
            Assertions.assertEquals(transaction.getAccount(), ofxAccount);
            Assertions.assertEquals(accountBalance.getAmount(), t.getBalance());
            return new CategorizedTransaction(t, category);
        });

        // there is only one transaction, but it belongs to an unknown account
        final List<OfxTransaction> transactions = Collections.singletonList(transaction);
        final List<OfxExport> ofxExports = Collections.singletonList(new OfxExport(ofxAccount, accountBalance, transactions));
        final AccountDao mockAccountDao = Mockito.mock(AccountDao.class);
        when(mockAccountDao.selectByAccountNumber(accountId)).thenReturn(Optional.empty());

        // the CLI will be queried for the account name and the account will be inserted into the db
        final String accountName = "Some Account";
        final Account account = Account.newBuilder()
                .setAccountType(ofxAccount.getAccountType())
                .setAccountNumber(ofxAccount.getAccountId())
                .setBankId(ofxAccount.getBankId())
                .setName(accountName)
                .build();
        when(mockCli.assignAccountName(ofxAccount)).thenReturn(account);
        when(mockAccountDao.insert(account)).thenReturn(Optional.of(account));

        final CategorizedTransactionDao mockCategorizedTransactionDao = Mockito.mock(CategorizedTransactionDao.class);
        when(mockCategorizedTransactionDao.isDuplicate(any(DatabaseTransaction.class), any(Transaction.class))).thenAnswer((Answer<Boolean>) invocation -> {
            final Transaction t = (Transaction) invocation.getArguments()[1];
            Assertions.assertEquals(transaction.getName().toUpperCase(), t.getDescription()); // default cleaner capitalizes this field
            Assertions.assertEquals(transaction.getDate(), t.getDate());
            Assertions.assertEquals(transaction.getAmount(), t.getAmount());
            Assertions.assertEquals(account, t.getAccount());
            Assertions.assertEquals(accountBalance.getAmount(), t.getBalance());
            return false;
        });
        when(mockCategorizedTransactionDao.insert(any(DatabaseTransaction.class), any(CategorizedTransaction.class))).thenAnswer((Answer<Optional<CategorizedTransaction>>) invocation -> {
            final CategorizedTransaction t = (CategorizedTransaction) invocation.getArguments()[1];
            Assertions.assertEquals(transaction.getName().toUpperCase(), t.getDescription()); // default cleaner capitalizes this field
            Assertions.assertEquals(transaction.getDate(), t.getDate());
            Assertions.assertEquals(transaction.getAmount(), t.getAmount());
            Assertions.assertEquals(account, t.getAccount());
            Assertions.assertEquals(category, t.getCategory());
            Assertions.assertEquals(accountBalance.getAmount(), t.getBalance());
            return Optional.of(t);
        });

        // actually run the test
        final Connection mockConnection = Mockito.mock(Connection.class);
        final TransactionImportService transactionImportService = new TransactionImportService(mockCli, null, mockAccountDao, transactionCleanerFactory, mockConnection, mockCategorizedTransactionDao);
        final List<CategorizedTransaction> categorizedTransactions = transactionImportService.categorizeTransactions(ofxExports);

        Assertions.assertEquals(1, categorizedTransactions.size());
        final CategorizedTransaction categorizedTransaction = categorizedTransactions.stream().findFirst().get();
        Assertions.assertEquals(category, categorizedTransaction.getCategory());
        Assertions.assertEquals(transaction.getName().toUpperCase(), categorizedTransaction.getDescription()); // default cleaner capitalizes this field
        Assertions.assertEquals(transaction.getDate(), categorizedTransaction.getDate());
        Assertions.assertEquals(transaction.getAmount(), categorizedTransaction.getAmount());
        Assertions.assertEquals(transaction.getAccount(), ofxAccount);
        Assertions.assertEquals(accountBalance.getAmount(), categorizedTransaction.getBalance());

        Mockito.verify(mockCli, times(1)).categorizeTransaction(any(Transaction.class));
        Mockito.verify(mockCli, times(1)).assignAccountName(any(OfxAccount.class));
        Mockito.verify(mockAccountDao, times(1)).selectByAccountNumber(anyString());
        Mockito.verify(mockAccountDao, times(1)).insert(any(Account.class));
        Mockito.verify(mockCategorizedTransactionDao, times(1)).isDuplicate(any(DatabaseTransaction.class), any(CategorizedTransaction.class));
        Mockito.verify(mockCategorizedTransactionDao, times(1)).insert(any(DatabaseTransaction.class), any(CategorizedTransaction.class));
        Mockito.verifyNoMoreInteractions(mockCli, mockAccountDao, mockCategorizedTransactionDao);
    }

    @Test
    void categorizeTransactionsUnknownAccountInsertFailsTest() {
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

        // there is only one transaction, but it belongs to an unknown account
        final List<OfxTransaction> transactions = Collections.singletonList(transaction);
        final List<OfxExport> ofxExports = Collections.singletonList(new OfxExport(ofxAccount, null, transactions));
        final AccountDao mockAccountDao = Mockito.mock(AccountDao.class);
        when(mockAccountDao.selectByAccountNumber(accountId)).thenReturn(Optional.empty());

        // the CLI will be queried for the account name and the account will be inserted into the db
        // the insert operation will fail and return an empty optional
        final String accountName = "Some Account";
        final Account account = Account.newBuilder()
                .setAccountType(ofxAccount.getAccountType())
                .setAccountNumber(ofxAccount.getAccountId())
                .setBankId(ofxAccount.getBankId())
                .setName(accountName)
                .build();
        final CLI mockCli = Mockito.mock(CLI.class);
        when(mockCli.assignAccountName(ofxAccount)).thenReturn(account);
        when(mockAccountDao.insert(account)).thenReturn(Optional.empty());

        try {
            // actually run the test
            final TransactionImportService transactionImportService = new TransactionImportService(mockCli, null, mockAccountDao, null, null, null);
            transactionImportService.categorizeTransactions(ofxExports);
            Assertions.fail("Expected a RuntimeException to be thrown");
        } catch (RuntimeException ex) {
            Mockito.verify(mockCli, times(1)).assignAccountName(any(OfxAccount.class));
            Mockito.verify(mockAccountDao, times(1)).selectByAccountNumber(anyString());
            Mockito.verify(mockAccountDao, times(1)).insert(any(Account.class));
            Mockito.verifyNoMoreInteractions(mockCli, mockAccountDao);
        }
    }

    @Test
    void categorizeTransactionsKnownAccountSuccessTest() throws SQLException {
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

        // this is the final expected balance for the account after all transactions have been processed
        // because there is only one transaction, its balance will be set to this value
        final OfxBalance accountBalance = OfxBalance.newBuilder().setAmount(15.00f).build();

        // every transaction is sorted into the same category
        final Category category = new Category(UUID.randomUUID().toString());
        final CLI mockCli = Mockito.mock(CLI.class);
        when(mockCli.categorizeTransaction(any(Transaction.class))).thenAnswer((Answer<CategorizedTransaction>) invocation -> {
            final Transaction t = (Transaction) invocation.getArguments()[0];
            Assertions.assertEquals(transaction.getName().toUpperCase(), t.getDescription()); // default cleaner capitalizes this field
            Assertions.assertEquals(transaction.getDate(), t.getDate());
            Assertions.assertEquals(transaction.getAmount(), t.getAmount());
            Assertions.assertEquals(transaction.getAccount(), ofxAccount);
            Assertions.assertEquals(accountBalance.getAmount(), t.getBalance());
            return new CategorizedTransaction(t, category);
        });

        // there is only one transaction, and it belongs to a known account
        final List<OfxTransaction> transactions = Collections.singletonList(transaction);
        final List<OfxExport> ofxExports = Collections.singletonList(new OfxExport(ofxAccount, accountBalance, transactions));
        final AccountDao mockAccountDao = Mockito.mock(AccountDao.class);
        when(mockAccountDao.selectByAccountNumber(accountId)).thenReturn(Optional.of(Account.newBuilder()
                .setId(1L)
                .setAccountNumber(accountId)
                .setBankId(bankId)
                .setAccountType("Savings")
                .setName("Savings")
                .build()));

        final CategorizedTransactionDao mockCategorizedTransactionDao = Mockito.mock(CategorizedTransactionDao.class);
        when(mockCategorizedTransactionDao.isDuplicate(any(DatabaseTransaction.class), any(Transaction.class))).thenAnswer((Answer<Boolean>) invocation -> {
            final Transaction t = (Transaction) invocation.getArguments()[1];
            Assertions.assertEquals(transaction.getName().toUpperCase(), t.getDescription()); // default cleaner capitalizes this field
            Assertions.assertEquals(transaction.getDate(), t.getDate());
            Assertions.assertEquals(transaction.getAmount(), t.getAmount());
            Assertions.assertEquals(transaction.getAccount(), ofxAccount);
            Assertions.assertEquals(accountBalance.getAmount(), t.getBalance());
            return false;
        });
        when(mockCategorizedTransactionDao.insert(any(DatabaseTransaction.class), any(CategorizedTransaction.class))).thenAnswer((Answer<Optional<CategorizedTransaction>>) invocation -> {
            final CategorizedTransaction t = (CategorizedTransaction) invocation.getArguments()[1];
            Assertions.assertEquals(transaction.getName().toUpperCase(), t.getDescription()); // default cleaner capitalizes this field
            Assertions.assertEquals(transaction.getDate(), t.getDate());
            Assertions.assertEquals(transaction.getAmount(), t.getAmount());
            Assertions.assertEquals(transaction.getAccount(), ofxAccount);
            Assertions.assertEquals(category, t.getCategory());
            Assertions.assertEquals(accountBalance.getAmount(), t.getBalance());
            return Optional.of(t);
        });

        // actually run the test
        final Connection mockConnection = Mockito.mock(Connection.class);
        final TransactionImportService transactionImportService = new TransactionImportService(mockCli, null, mockAccountDao, transactionCleanerFactory, mockConnection, mockCategorizedTransactionDao);
        final List<CategorizedTransaction> categorizedTransactions = transactionImportService.categorizeTransactions(ofxExports);

        Assertions.assertEquals(1, categorizedTransactions.size());
        final CategorizedTransaction categorizedTransaction = categorizedTransactions.stream().findFirst().get();
        assertEquals(transaction, category, accountBalance.getAmount(), categorizedTransaction);
        Assertions.assertEquals(transaction.getAccount(), ofxAccount);

        Mockito.verify(mockCli, times(1)).categorizeTransaction(any(Transaction.class));
        Mockito.verify(mockAccountDao, times(1)).selectByAccountNumber(anyString());
        Mockito.verify(mockCategorizedTransactionDao, times(1)).isDuplicate(any(DatabaseTransaction.class), any(CategorizedTransaction.class));
        Mockito.verify(mockCategorizedTransactionDao, times(1)).insert(any(DatabaseTransaction.class), any(CategorizedTransaction.class));
        Mockito.verifyNoMoreInteractions(mockCli, mockAccountDao, mockCategorizedTransactionDao);
    }

    private void assertEquals(OfxTransaction ofxTransaction, Category expectedCategory, float expectedBalance, CategorizedTransaction categorizedTransaction) {
        Assertions.assertEquals(expectedCategory, categorizedTransaction.getCategory());
        Assertions.assertEquals(ofxTransaction.getName().toUpperCase(), categorizedTransaction.getDescription()); // default cleaner capitalizes this field
        Assertions.assertEquals(ofxTransaction.getDate(), categorizedTransaction.getDate());
        Assertions.assertEquals(ofxTransaction.getAmount(), categorizedTransaction.getAmount());
        Assertions.assertEquals(expectedBalance, categorizedTransaction.getBalance());
    }

    @Test
    void categorizeTransactionsDuplicateTransactionIsIgnoredTest() throws SQLException {
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

        // there is only one transaction, and it belongs to a known account
        final List<OfxTransaction> transactions = Collections.singletonList(transaction);
        final List<OfxExport> ofxExports = Collections.singletonList(new OfxExport(ofxAccount, OfxBalance.newBuilder().setAmount(0f).build(), transactions));
        final AccountDao mockAccountDao = Mockito.mock(AccountDao.class);
        when(mockAccountDao.selectByAccountNumber(accountId)).thenReturn(Optional.of(Account.newBuilder()
                .setId(1L)
                .setAccountNumber(accountId)
                .setBankId(bankId)
                .setAccountType("Savings")
                .setName("Savings")
                .build()));

        // it also happens to be a duplicate, so it will not be inserted
        final CategorizedTransactionDao mockCategorizedTransactionDao = Mockito.mock(CategorizedTransactionDao.class);
        when(mockCategorizedTransactionDao.isDuplicate(any(DatabaseTransaction.class), any(Transaction.class))).thenReturn(true);

        // actually run the test
        final Connection mockConnection = Mockito.mock(Connection.class);
        final TransactionImportService transactionImportService = new TransactionImportService(null, null, mockAccountDao, transactionCleanerFactory, mockConnection, mockCategorizedTransactionDao);
        final List<CategorizedTransaction> categorizedTransactions = transactionImportService.categorizeTransactions(ofxExports);
        Assertions.assertTrue(categorizedTransactions.isEmpty());

        Mockito.verify(mockAccountDao, times(1)).selectByAccountNumber(anyString());
        Mockito.verify(mockCategorizedTransactionDao, times(1)).isDuplicate(any(DatabaseTransaction.class), any(CategorizedTransaction.class));
        Mockito.verifyNoMoreInteractions(mockAccountDao, mockCategorizedTransactionDao);
    }

    @Test
    void categorizeTransactionsTransactionInsertFailsTest() throws SQLException {
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
        final List<OfxTransaction> transactions = Collections.singletonList(transaction);
        final List<OfxExport> ofxExports = Collections.singletonList(new OfxExport(ofxAccount, OfxBalance.newBuilder().setAmount(0f).build(), transactions));
        final AccountDao mockAccountDao = Mockito.mock(AccountDao.class);
        when(mockAccountDao.selectByAccountNumber(accountId)).thenReturn(Optional.of(Account.newBuilder()
                .setId(1L)
                .setAccountNumber(accountId)
                .setBankId(bankId)
                .setAccountType("Savings")
                .setName("Savings")
                .build()));

        final CategorizedTransactionDao mockCategorizedTransactionDao = Mockito.mock(CategorizedTransactionDao.class);
        when(mockCategorizedTransactionDao.isDuplicate(any(DatabaseTransaction.class), any(Transaction.class))).thenAnswer((Answer<Boolean>) invocation -> {
            final Transaction t = (Transaction) invocation.getArguments()[1];
            Assertions.assertEquals(transaction.getName().toUpperCase(), t.getDescription()); // default cleaner capitalizes this field
            Assertions.assertEquals(transaction.getDate(), t.getDate());
            Assertions.assertEquals(transaction.getAmount(), t.getAmount());
            Assertions.assertEquals(transaction.getAccount(), ofxAccount);
            return false;
        });
        when(mockCategorizedTransactionDao.insert(any(DatabaseTransaction.class), any(CategorizedTransaction.class))).thenThrow(new SQLException());

        // actually run the test
        final Connection mockConnection = Mockito.mock(Connection.class);
        final TransactionImportService transactionImportService = new TransactionImportService(mockCli, null, mockAccountDao, transactionCleanerFactory, mockConnection, mockCategorizedTransactionDao);
        final List<CategorizedTransaction> categorizedTransactions = transactionImportService.categorizeTransactions(ofxExports);
        Assertions.assertTrue(categorizedTransactions.isEmpty());

        Mockito.verify(mockCli, times(1)).categorizeTransaction(any(Transaction.class));
        Mockito.verify(mockAccountDao, times(1)).selectByAccountNumber(anyString());
        Mockito.verify(mockCategorizedTransactionDao, times(1)).isDuplicate(any(DatabaseTransaction.class), any(CategorizedTransaction.class));
        Mockito.verify(mockCategorizedTransactionDao, times(1)).insert(any(DatabaseTransaction.class), any(CategorizedTransaction.class));
        Mockito.verifyNoMoreInteractions(mockCli, mockAccountDao, mockCategorizedTransactionDao);
    }

    @Test
    void categorizeTransactionsMultipleTransactionsBalanceAccumulatedTest() throws SQLException {
        final TransactionCleanerFactory transactionCleanerFactory = new TransactionCleanerFactory();
        final String bankId = UUID.randomUUID().toString();
        final String accountId = UUID.randomUUID().toString();

        final OfxAccount ofxAccount = OfxAccount.newBuilder()
                .setBankId(bankId)
                .setAccountId(accountId)
                .build();
        final LocalDate now = LocalDate.now();
        final List<OfxTransaction> transactions = Arrays.asList(
            OfxTransaction.newBuilder()
                    .setDate(now.minus(5, ChronoUnit.DAYS))
                    .setName("Some Vendor")
                    .setAmount(-10.00f)
                    .setAccount(ofxAccount)
                    .build(), // balance is $10
            OfxTransaction.newBuilder()
                    .setDate(now.minus(4, ChronoUnit.DAYS))
                    .setName("Some Other Vendor")
                    .setAmount(-5.00f)
                    .setAccount(ofxAccount)
                    .build(), // balance is $5
            OfxTransaction.newBuilder()
                    .setDate(now.minus(3, ChronoUnit.DAYS))
                    .setName("Some Other Other Vendor")
                    .setAmount(15.00f)
                    .setAccount(ofxAccount)
                    .build() // balance is $20
        );

        // this is the final expected balance for the account after all transactions have been processed
        final OfxBalance accountBalance = OfxBalance.newBuilder().setAmount(20.00f).build();

        // every transaction is sorted into the same category
        final Category category = new Category(UUID.randomUUID().toString());
        final CLI mockCli = Mockito.mock(CLI.class);
        when(mockCli.categorizeTransaction(any(Transaction.class))).thenAnswer((Answer<CategorizedTransaction>) invocation -> {
            final Transaction t = (Transaction) invocation.getArguments()[0];
            return new CategorizedTransaction(t, category);
        });

        // all transactions belong to the same account
        final List<OfxExport> ofxExports = Collections.singletonList(new OfxExport(ofxAccount, accountBalance, transactions));
        final AccountDao mockAccountDao = Mockito.mock(AccountDao.class);
        when(mockAccountDao.selectByAccountNumber(accountId)).thenReturn(Optional.of(Account.newBuilder()
                .setId(1L)
                .setAccountNumber(accountId)
                .setBankId(bankId)
                .setAccountType("Savings")
                .setName("Savings")
                .build()));

        // there are no duplicates, and all transactions are inserted successfully
        final CategorizedTransactionDao mockCategorizedTransactionDao = Mockito.mock(CategorizedTransactionDao.class);
        when(mockCategorizedTransactionDao.isDuplicate(any(DatabaseTransaction.class), any(Transaction.class))).thenReturn(false);
        when(mockCategorizedTransactionDao.insert(any(DatabaseTransaction.class), any(CategorizedTransaction.class)))
                .thenAnswer((Answer<Optional<CategorizedTransaction>>) invocation -> Optional.of((CategorizedTransaction) invocation.getArguments()[1]));

        // actually run the test
        final Connection mockConnection = Mockito.mock(Connection.class);
        final TransactionImportService transactionImportService = new TransactionImportService(mockCli, null, mockAccountDao, transactionCleanerFactory, mockConnection, mockCategorizedTransactionDao);
        final List<CategorizedTransaction> categorizedTransactions = transactionImportService.categorizeTransactions(ofxExports);

        // balances add up and transactions are ordered ascending by date (ie. least recent to most recent)
        Assertions.assertEquals(3, categorizedTransactions.size());
        float expectedBalance = accountBalance.getAmount(); // all transaction amounts add to zero
        LocalDate previousDate = LocalDate.MIN;
        for (int i = 0; i < transactions.size(); i++) {
            expectedBalance += categorizedTransactions.get(i).getAmount();
            assertEquals(transactions.get(i), category, expectedBalance, categorizedTransactions.get(i));

            Assertions.assertTrue(previousDate.isBefore(categorizedTransactions.get(i).getDate()));
            previousDate = categorizedTransactions.get(i).getDate();
        }
        Assertions.assertEquals(accountBalance.getAmount(), expectedBalance);

        Mockito.verify(mockCli, times(3)).categorizeTransaction(any(Transaction.class));
        Mockito.verify(mockAccountDao, times(1)).selectByAccountNumber(anyString());
        Mockito.verify(mockCategorizedTransactionDao, times(3)).isDuplicate(any(DatabaseTransaction.class), any(CategorizedTransaction.class));
        Mockito.verify(mockCategorizedTransactionDao, times(3)).insert(any(DatabaseTransaction.class), any(CategorizedTransaction.class));
        Mockito.verifyNoMoreInteractions(mockCli, mockAccountDao, mockCategorizedTransactionDao);
    }

    // TODO: tests for importTransactions(...) method
}