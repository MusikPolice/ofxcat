package ca.jonathanfritz.ofxcat;

import ca.jonathanfritz.ofxcat.cleaner.TransactionCleaner;
import ca.jonathanfritz.ofxcat.cleaner.TransactionCleanerFactory;
import ca.jonathanfritz.ofxcat.cli.CLI;
import ca.jonathanfritz.ofxcat.cli.CLIModule;
import ca.jonathanfritz.ofxcat.datastore.utils.DatastoreModule;
import ca.jonathanfritz.ofxcat.datastore.AccountDao;
import ca.jonathanfritz.ofxcat.io.OfxAccount;
import ca.jonathanfritz.ofxcat.io.OfxParser;
import ca.jonathanfritz.ofxcat.io.OfxTransaction;
import ca.jonathanfritz.ofxcat.transactions.Account;
import ca.jonathanfritz.ofxcat.transactions.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.utils.PathUtils;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.webcohesion.ofx4j.OFXException;
import com.webcohesion.ofx4j.io.OFXParseException;
import org.apache.commons.cli.*;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class OfxCat {

    private final TransactionCleanerFactory transactionCleanerFactory;
    private final CLI cli;
    private final OfxParser ofxParser;
    private final AccountDao accountDao;

    private static final Logger log = LoggerFactory.getLogger(OfxCat.class);

    @Inject
    OfxCat(TransactionCleanerFactory transactionCleanerFactory, CLI cli, OfxParser ofxParser, Flyway flyway, AccountDao accountDao) {
        this.transactionCleanerFactory = transactionCleanerFactory;
        this.cli = cli;
        this.ofxParser = ofxParser;
        this.accountDao = accountDao;

        // before we can do anything, we have to make sure that the database is up to date
        log.debug("Attempting to migrate database schema...");
        flyway.migrate();
        log.info("Database schema migration complete");
    }

    private void importTransactions(final File inputFile) throws OFXException {
        cli.printWelcomeBanner();
        cli.println("Loading transactions from file:");
        cli.println("value", inputFile.toString());

        log.debug("Attempting to parse file {}", inputFile.toString());
        final Map<OfxAccount, Set<OfxTransaction>> ofxTransactions;
        try (final FileInputStream inputStream = new FileInputStream(inputFile)) {
            ofxTransactions = ofxParser.parse(inputStream);
        } catch (FileNotFoundException e) {
            throw new OFXException("File not found", e);
        } catch (OFXParseException e) {
            throw new OFXException("Failed to parse OFX file", e);
        } catch (IOException e) {
            throw new OFXException("An unexpected exception occurred", e);
        }

        final Set<CategorizedTransaction> categorizedTransactions = categorizeTransactions(ofxTransactions);

        // present the results in a pleasing manner
        cli.displayResults(categorizedTransactions);

        // TODO: persist transactions to disk

        System.out.printf("Finished processing %s%n", inputFile.toString());
    }

    Set<CategorizedTransaction> categorizeTransactions(final Map<OfxAccount, Set<OfxTransaction>> ofxTransactions) {
        final Set<CategorizedTransaction> categorizedTransactions = new HashSet<>();
        for (OfxAccount ofxAccount : ofxTransactions.keySet()) {
            // figure out which account this transaction belongs to
            final Account account = accountDao.selectByAccountNumber(ofxAccount.getAccountId())
                    .or(() -> accountDao.insert(cli.assignAccountName(ofxAccount)))
                    .orElseThrow(() -> new RuntimeException(String.format("Failed to find or create account %s", ofxAccount)));

            // clean up any garbage data that may be in the export file, associate the transaction with the account,
            // prompt the user to associate it with a category, and add it to the set of categorized transactions
            log.info("Processing transactions for Account {}", account);
            final TransactionCleaner transactionCleaner = transactionCleanerFactory.findByBankId(ofxAccount.getBankId());
            ofxTransactions.get(ofxAccount)
                    .stream()
                    .map(transactionCleaner::clean)
                    .map(builder -> {
                        // associate the transaction with the account
                        return builder
                                .setAccount(account)
                                .build();
                    })
                    .map(cli::categorizeTransaction)
                    .forEach(categorizedTransactions::add);
        }

        return categorizedTransactions;
    }

    public static void main(String[] args) {
        final Options options = new Options();
        options.addOption("f", "file", true, "the ofx file to parse");

        final PathUtils pathUtils = new PathUtils();
        final Injector injector = Guice.createInjector(new CLIModule(), new DatastoreModule(pathUtils.getDatabaseConnectionString()));
        final OfxCat ofxCat = injector.getInstance(OfxCat.class);

        try {
            final CommandLineParser commandLineParser = new DefaultParser();
            final CommandLine commandLine = commandLineParser.parse(options, args);

            if (commandLine.hasOption("f")) {
                // parse and categorize the transactions
                final File file = pathUtils.expand(commandLine.getOptionValue("f")).toFile();
                ofxCat.importTransactions(file);
            } else {
                System.err.println("Use the -f or --file parameter to specify a valid *.ofx file to parse");
            }
        } catch (ParseException e) {
            System.err.println("Failed to parse command line parameters");
        } catch (OFXException e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
    }
}
