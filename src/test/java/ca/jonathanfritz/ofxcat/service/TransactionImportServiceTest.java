package ca.jonathanfritz.ofxcat.service;

import ca.jonathanfritz.ofxcat.cleaner.TransactionCleanerFactory;
import ca.jonathanfritz.ofxcat.cli.CLI;
import ca.jonathanfritz.ofxcat.datastore.AccountDao;
import ca.jonathanfritz.ofxcat.datastore.CategorizedTransactionDao;
import ca.jonathanfritz.ofxcat.datastore.dto.Account;
import ca.jonathanfritz.ofxcat.datastore.dto.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import ca.jonathanfritz.ofxcat.datastore.utils.DatabaseTransaction;
import ca.jonathanfritz.ofxcat.io.OfxAccount;
import ca.jonathanfritz.ofxcat.io.OfxBalance;
import ca.jonathanfritz.ofxcat.io.OfxExport;
import ca.jonathanfritz.ofxcat.io.OfxTransaction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class TransactionImportServiceTest {

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
            final TransactionImportService transactionImportService = new TransactionImportService(mockCli, null, mockAccountDao, null, null, null, null);
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
        final TransactionImportService transactionImportService = new TransactionImportService(null, null, mockAccountDao, transactionCleanerFactory, mockConnection, mockCategorizedTransactionDao, null);
        final List<CategorizedTransaction> categorizedTransactions = transactionImportService.categorizeTransactions(ofxExports);
        Assertions.assertTrue(categorizedTransactions.isEmpty());

        Mockito.verify(mockAccountDao, times(1)).selectByAccountNumber(anyString());
        Mockito.verify(mockCategorizedTransactionDao, times(1)).isDuplicate(any(DatabaseTransaction.class), any(CategorizedTransaction.class));
        Mockito.verifyNoMoreInteractions(mockAccountDao, mockCategorizedTransactionDao);
    }

    // TODO
}