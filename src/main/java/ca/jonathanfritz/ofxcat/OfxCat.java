package ca.jonathanfritz.ofxcat;

import ca.jonathanfritz.ofxcat.cleaner.TransactionCleaner;
import ca.jonathanfritz.ofxcat.cleaner.TransactionCleanerFactory;
import ca.jonathanfritz.ofxcat.cli.CLI;
import ca.jonathanfritz.ofxcat.cli.CLIModule;
import ca.jonathanfritz.ofxcat.data.TransactionCategoryStore;
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
    private final TransactionCategoryStore transactionCategoryStore;

    private static final Logger log = LoggerFactory.getLogger(OfxCat.class);

    @Inject
    public OfxCat(TransactionCleanerFactory transactionCleanerFactory, CLI cli, OfxParser ofxParser, TransactionCategoryStore transactionCategoryStore) {
        this.transactionCleanerFactory = transactionCleanerFactory;
        this.cli = cli;
        this.ofxParser = ofxParser;
        this.transactionCategoryStore = transactionCategoryStore;
    }

    private Map<OfxAccount, Set<OfxTransaction>> parseOfxFile(final File inputFile) throws OFXException {
        log.debug("Attempting to parse file {}", inputFile.toString());
        try (final FileInputStream inputStream = new FileInputStream(inputFile)) {
            return ofxParser.parse(inputStream);

        } catch (FileNotFoundException e) {
            throw new OFXException("File not found", e);
        } catch (OFXParseException e) {
            throw new OFXException("Failed to parse OFX file", e);
        } catch (IOException e) {
            throw new OFXException("An unexpected exception occurred", e);
        }
    }

    protected Set<CategorizedTransaction> categorizeTransactions(final Map<OfxAccount, Set<OfxTransaction>> ofxTransactions, final Set<Account> knownAccounts) {
        final Set<CategorizedTransaction> categorizedTransactions = new HashSet<>();
        for (OfxAccount ofxAccount : ofxTransactions.keySet()) {
            // identify the account by name
            final Account account  = knownAccounts.stream()
                    .filter(a -> a.getBankId().equalsIgnoreCase(ofxAccount.getBankId()))
                    .filter(a -> a.getAccountId().equalsIgnoreCase(ofxAccount.getAccountId()))
                    .findFirst()
                    .orElseGet(() -> cli.assignAccountName(ofxAccount));
            log.info("Processing transactions for Account {}", account);

            // clean up any garbage data that may be in the export file, associate the transaction with the account,
            // prompt the user to associate it with a category, and add it to the set of categorized transactions
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

        // save the transaction categorizations for later use
        transactionCategoryStore.save();

        return categorizedTransactions;
    }

    public static void main(String[] args) {
        final Options options = new Options();
        options.addOption("f", "file", true, "the ofx file to parse");

        try {
            final CommandLineParser commandLineParser = new DefaultParser();
            final CommandLine commandLine = commandLineParser.parse(options, args);

            if (commandLine.hasOption("f")) {
                final Injector injector = Guice.createInjector(new CLIModule());
                final OfxCat ofxCat = injector.getInstance(OfxCat.class);

                // TODO: load known accounts from file?
                final Set<Account> knownAccounts = new HashSet<>();

                // parse and categorize the transactions
                final PathUtils pathUtils = injector.getInstance(PathUtils.class);
                final File file = pathUtils.expand(commandLine.getOptionValue("f")).toFile();
                final Map<OfxAccount, Set<OfxTransaction>> ofxTransactions = ofxCat.parseOfxFile(file);
                final Set<CategorizedTransaction> categorizedTransactions = ofxCat.categorizeTransactions(ofxTransactions, knownAccounts);

                // TODO: present the results in a pleasing manner

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
