package ca.jonathanfritz.ofxcat.service;

import ca.jonathanfritz.ofxcat.cleaner.TransactionCleaner;
import ca.jonathanfritz.ofxcat.cleaner.TransactionCleanerFactory;
import ca.jonathanfritz.ofxcat.cli.CLI;
import ca.jonathanfritz.ofxcat.datastore.AccountDao;
import ca.jonathanfritz.ofxcat.datastore.CategorizedTransactionDao;
import ca.jonathanfritz.ofxcat.datastore.CategoryDao;
import ca.jonathanfritz.ofxcat.datastore.dto.Account;
import ca.jonathanfritz.ofxcat.datastore.dto.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Transfer;
import ca.jonathanfritz.ofxcat.datastore.utils.DatabaseTransaction;
import ca.jonathanfritz.ofxcat.exception.OfxCatException;
import ca.jonathanfritz.ofxcat.io.OfxExport;
import ca.jonathanfritz.ofxcat.io.OfxParser;
import ca.jonathanfritz.ofxcat.io.OfxTransaction;
import ca.jonathanfritz.ofxcat.utils.Accumulator;
import com.webcohesion.ofx4j.io.OFXParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

// TODO: Improve test coverage
public class TransactionImportService {

    private final CLI cli;
    private final OfxParser ofxParser;
    private final AccountDao accountDao;
    private final TransactionCleanerFactory transactionCleanerFactory;
    private final Connection connection;
    private final CategorizedTransactionDao categorizedTransactionDao;
    private final TransactionCategoryService transactionCategoryService;
    private final CategoryDao categoryDao;
    private final TransferMatchingService transferMatchingService;

    private static final Logger logger = LogManager.getLogger(TransactionImportService.class);

    @Inject
    public TransactionImportService(CLI cli, OfxParser ofxParser, AccountDao accountDao, TransactionCleanerFactory transactionCleanerFactory, Connection connection, CategorizedTransactionDao categorizedTransactionDao, TransactionCategoryService transactionCategoryService, CategoryDao categoryDao, TransferMatchingService transferMatchingService) {
        this.cli = cli;
        this.ofxParser = ofxParser;
        this.accountDao = accountDao;
        this.transactionCleanerFactory = transactionCleanerFactory;
        this.connection = connection;
        this.categorizedTransactionDao = categorizedTransactionDao;
        this.transactionCategoryService = transactionCategoryService;
        this.categoryDao = categoryDao;
        this.transferMatchingService = transferMatchingService;
    }

    public void importTransactions(final File inputFile) throws OfxCatException {
        cli.printWelcomeBanner();
        cli.println("Loading transactions from file:");
        cli.println("value", inputFile.toString());

        logger.debug("Attempting to parse file {}", inputFile);
        final List<OfxExport> ofxTransactions;
        try (final FileInputStream inputStream = new FileInputStream(inputFile)) {
            ofxTransactions = ofxParser.parse(inputStream);
        } catch (FileNotFoundException e) {
            throw new OfxCatException("File not found", e);
        } catch (OFXParseException e) {
            throw new OfxCatException("Failed to parse OFX file", e);
        } catch (IOException e) {
            throw new OfxCatException("An unexpected exception occurred", e);
        }

        final List<CategorizedTransaction> categorizedTransactions = categorizeTransactions(ofxTransactions);
        cli.println(String.format("Successfully imported %d transactions", categorizedTransactions.size()));
    }

    // TODO: return number of ignored duplicate transactions so that this info can be displayed in UI
    List<CategorizedTransaction> categorizeTransactions(final List<OfxExport> ofxExports) {
        final List<CategorizedTransaction> categorizedTransactions = new ArrayList<>();

        final Map<Account, List<Transaction>> accountTransactions = new HashMap<>();
        for (OfxExport ofxExport : ofxExports) {
            // figure out which account these transactions belong to
            final Account account = accountDao.selectByAccountNumber(ofxExport.getAccount().getAccountId())
                    .or(() -> accountDao.insert(cli.assignAccountName(ofxExport.getAccount())))
                    .orElseThrow(() -> new RuntimeException(String.format("Failed to find or create account %s", ofxExport)));
            logger.info("Processing transactions for Account {}", account);

            // an ofx file contains the account balance after all included transactions were processed, but does not
            // include the initial account balance or the account balance after each individual transaction was processed.
            // we can determine the initial account balance by summing up the amount of all transactions and subtracting
            // that value from the final account balance. This can then be used to determine the account balance after
            // each transaction was applied.
            final float totalTransactionAmount = ofxExport.getTransactions().values().stream()
                    .flatMap((Function<List<OfxTransaction>, Stream<OfxTransaction>>) Collection::stream)
                    .map(OfxTransaction::getAmount)
                    .reduce(0F, Float::sum, Float::sum);
            final Float initialBalance = ofxExport.getBalance().getAmount() - totalTransactionAmount;
            logger.debug("Initial balance for Account {} was {}", account.getAccountNumber(), initialBalance);

            // sorts transactions by date, transforms them into our internal representation, sets the resulting account
            // balance on each, and associates each with an account
            final TransactionCleaner transactionCleaner = transactionCleanerFactory.findByBankId(account.getBankId());
            final Accumulator<Float> balanceAccumulator = new Accumulator<>(initialBalance, Float::sum);
            accountTransactions.put(account, ofxExport.getTransactions().entrySet().stream()
                    .flatMap((Function<Map.Entry<LocalDate, List<OfxTransaction>>, Stream<OfxTransaction>>) entry -> entry.getValue().stream())
                    .sorted(Comparator.comparing(OfxTransaction::getDate))
                    .map(transactionCleaner::clean)
                    .map(builder -> builder.setBalance(balanceAccumulator.add(builder.getAmount())))
                    .map(builder -> builder.setAccount(account).build())
                    .toList());
            logger.debug("Final balance for Account {} was {}", account.getAccountNumber(), balanceAccumulator.getCurrentValue());
        }

        // all of our transactions have been cleaned up and enriched with account and balance information
        // at this point, we can attempt to identify inter-account transfers
        final Set<Transfer> transfers = transferMatchingService.match(accountTransactions);
        // TODO: insert transfers (and associated transactions) into the database
        //       transactions that are a part of transfers need implicit TRANSFER categorization

        for (Map.Entry<Account, List<Transaction>> entry: accountTransactions.entrySet()) {
            // TODO: this can probably be cleaned up too
            // filter out duplicates, categorize transactions, and insert them into the database
            final List<Transaction> transactionStream = entry.getValue();
            transactionStream.forEach(transaction -> {
                try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
                    // TODO: strip dupes out way earlier?
                    if (categorizedTransactionDao.isDuplicate(t, transaction)) {
                        logger.info("Ignored duplicate Transaction {}", transaction);
                        return;
                    }

                    // try to automatically categorize the transaction, prompting the user for a category if necessary
                    cli.printFoundNewTransaction(transaction);
                    CategorizedTransaction categorizedTransaction = transactionCategoryService.categorizeTransaction(t, transaction);
                    if (categorizedTransaction.getCategory().getId() == null) {
                        // this is a new category, so we have to insert it before inserting the categorized transaction
                        final Transaction newTransaction = categorizedTransaction.getTransaction();
                        final String newCategoryName = categorizedTransaction.getCategory().getName();
                        categorizedTransaction = categoryDao.insert(t, categorizedTransaction.getCategory())
                            .map(newCategory ->
                                    new CategorizedTransaction(newTransaction, newCategory)
                            ).orElseThrow(() ->
                                    new OfxCatException(String.format("Failed to insert new Category %s", newCategoryName))
                            );
                    }
                    categorizedTransactionDao.insert(t, categorizedTransaction)
                            .ifPresent(categorizedTransactions::add);

                    cli.printTransactionCategorizedAs(categorizedTransaction.getCategory());
                    logger.info("Categorized Transaction {} as {}", transaction, categorizedTransaction.getCategory());

                } catch (SQLException | OfxCatException e) {
                    logger.error("Failed to import transaction {}", transaction, e);
                }
            });
        }

        return categorizedTransactions;
    }
}
