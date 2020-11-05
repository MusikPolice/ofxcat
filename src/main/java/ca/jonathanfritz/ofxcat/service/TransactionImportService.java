package ca.jonathanfritz.ofxcat.service;

import ca.jonathanfritz.ofxcat.cleaner.TransactionCleaner;
import ca.jonathanfritz.ofxcat.cleaner.TransactionCleanerFactory;
import ca.jonathanfritz.ofxcat.cli.CLI;
import ca.jonathanfritz.ofxcat.datastore.AccountDao;
import ca.jonathanfritz.ofxcat.datastore.CategorizedTransactionDao;
import ca.jonathanfritz.ofxcat.datastore.utils.DatabaseTransaction;
import ca.jonathanfritz.ofxcat.io.OfxExport;
import ca.jonathanfritz.ofxcat.io.OfxParser;
import ca.jonathanfritz.ofxcat.io.OfxTransaction;
import ca.jonathanfritz.ofxcat.transactions.Account;
import ca.jonathanfritz.ofxcat.transactions.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.transactions.Transaction;
import ca.jonathanfritz.ofxcat.utils.Accumulator;
import com.webcohesion.ofx4j.OFXException;
import com.webcohesion.ofx4j.io.OFXParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

public class TransactionImportService {

    private final CLI cli;
    private final OfxParser ofxParser;
    private final AccountDao accountDao;
    private final TransactionCleanerFactory transactionCleanerFactory;
    private final Connection connection;
    private final CategorizedTransactionDao categorizedTransactionDao;

    private static final Logger logger = LoggerFactory.getLogger(TransactionImportService.class);

    @Inject
    public TransactionImportService(CLI cli, OfxParser ofxParser, AccountDao accountDao, TransactionCleanerFactory transactionCleanerFactory, Connection connection, CategorizedTransactionDao categorizedTransactionDao) {
        this.cli = cli;
        this.ofxParser = ofxParser;
        this.accountDao = accountDao;
        this.transactionCleanerFactory = transactionCleanerFactory;
        this.connection = connection;
        this.categorizedTransactionDao = categorizedTransactionDao;
    }

    public void importTransactions(final File inputFile) throws OFXException {
        cli.printWelcomeBanner();
        cli.println("Loading transactions from file:");
        cli.println("value", inputFile.toString());

        logger.debug("Attempting to parse file {}", inputFile.toString());
        final List<OfxExport> ofxTransactions;
        try (final FileInputStream inputStream = new FileInputStream(inputFile)) {
            ofxTransactions = ofxParser.parse(inputStream);
        } catch (FileNotFoundException e) {
            throw new OFXException("File not found", e);
        } catch (OFXParseException e) {
            throw new OFXException("Failed to parse OFX file", e);
        } catch (IOException e) {
            throw new OFXException("An unexpected exception occurred", e);
        }

        final List<CategorizedTransaction> categorizedTransactions = categorizeTransactions(ofxTransactions);

        // present the results in a pleasing manner
        // TODO: instead, show stats about what was imported
        cli.displayResults(categorizedTransactions);

        System.out.printf("Finished processing %s%n", inputFile.toString());
    }

    List<CategorizedTransaction> categorizeTransactions(final List<OfxExport> ofxExports) {
        final List<CategorizedTransaction> categorizedTransactions = new ArrayList<>();
        for (OfxExport ofxExport : ofxExports) {
            // figure out which account this transaction belongs to
            final Account account = accountDao.selectByAccountNumber(ofxExport.getAccount().getAccountId())
                    .or(() -> accountDao.insert(cli.assignAccountName(ofxExport.getAccount())))
                    .orElseThrow(() -> new RuntimeException(String.format("Failed to find or create account %s", ofxExport)));

            // clean up the transaction object and associate it with the account
            logger.info("Processing transactions for Account {}", account);
            final float totalTransactionAmount = ofxExport.getTransactions().values().stream()
                    .flatMap((Function<List<OfxTransaction>, Stream<OfxTransaction>>) Collection::stream)
                    .map(OfxTransaction::getAmount)
                    .reduce(0F, Float::sum, Float::sum);
            final Float initialBalance = ofxExport.getBalance().getAmount() - totalTransactionAmount;
            logger.debug("Initial balance for Account {} was {}", account.getAccountId(), initialBalance);

            // sorts transactions by date, transforms them into our internal representation, sets the resulting account
            // balance on each, and associates each with an account
            final TransactionCleaner transactionCleaner = transactionCleanerFactory.findByBankId(account.getBankId());
            final Accumulator<Float> currentBalance = new Accumulator<>(initialBalance, Float::sum);
            final Stream<Transaction> transactionStream = ofxExport.getTransactions().entrySet().stream()
                    .flatMap((Function<Map.Entry<LocalDate, List<OfxTransaction>>, Stream<OfxTransaction>>) entry -> entry.getValue().stream())
                    .sorted(Comparator.comparing(OfxTransaction::getDate))
                    .map(transactionCleaner::clean)
                    .map(builder -> builder.setBalance(currentBalance.add(builder.getAmount())))
                    .map(builder -> builder.setAccount(account).build());
            logger.debug("Final balance for Account {} was {}", account.getAccountId(), currentBalance.getCurrentValue());

            // filter out duplicates, categorize transactions, and insert them into the database
            transactionStream.forEach(transaction -> {
                try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
                    if (categorizedTransactionDao.isDuplicate(t, transaction)) {
                        logger.info("Ignored duplicate Transaction {}", transaction);
                        return;
                    }

                    final CategorizedTransaction categorizedTransaction = cli.categorizeTransaction(transaction);
                    logger.info("Categorized Transaction {} as {}", transaction, categorizedTransaction.getCategory());
                    categorizedTransactionDao.insert(t, categorizedTransaction)
                            .ifPresent(categorizedTransactions::add);

                } catch (SQLException e) {
                    logger.error("Failed to import transaction {}", transaction, e);
                }
            });
        }

        return categorizedTransactions;
    }
}
