package ca.jonathanfritz.ofxcat;

import ca.jonathanfritz.ofxcat.cleaner.RbcTransactionCleaner;
import ca.jonathanfritz.ofxcat.cleaner.TransactionCleaner;
import ca.jonathanfritz.ofxcat.io.OfxParser;
import ca.jonathanfritz.ofxcat.io.OfxTransaction;
import ca.jonathanfritz.ofxcat.transactions.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.transactions.Transaction;
import ca.jonathanfritz.ofxcat.transactions.TransactionCategoryStore;
import com.webcohesion.ofx4j.OFXException;
import com.webcohesion.ofx4j.io.OFXParseException;
import org.apache.commons.cli.*;
import org.beryx.textio.TextIoFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

public class OfxCat {

    private final TransactionCategoryStore transactionCategoryStore;
    private final CLI cli;

    public OfxCat(TransactionCategoryStore transactionCategoryStore, CLI cli) {
        this.transactionCategoryStore = transactionCategoryStore;
        this.cli = cli;
    }

    private Set<OfxTransaction> parseOfxFile(final File inputFile) throws OFXException {
        try (final FileInputStream inputStream = new FileInputStream(inputFile)) {

            final OfxParser ofxParser = new OfxParser();
            return ofxParser.parse(inputStream);

        } catch (FileNotFoundException e) {
            throw new OFXException("File not found", e);
        } catch (OFXParseException e) {
            throw new OFXException("Failed to parse OFX file", e);
        } catch (IOException e) {
            throw new OFXException("An unexpected exception occurred", e);
        }
    }

    private Set<CategorizedTransaction> categorizeTransactions(Set<Transaction> transactions) {
        return cli.categorizeTransactions(transactions);
    }

    public static void main(String[] args) {
        final Options options = new Options();
        options.addOption("f", "file", true, "the ofx file to parse");

        try {
            final CommandLineParser commandLineParser = new DefaultParser();
            final CommandLine commandLine = commandLineParser.parse(options, args);

            if (commandLine.hasOption("f")) {
                final File file  = new File(commandLine.getOptionValue("f"));
                final TransactionCategoryStore transactionCategoryStore = new TransactionCategoryStore(); // TODO: load categorizations from previous runs here
                final CLI cli = new CLI(TextIoFactory.getTextIO(), transactionCategoryStore);
                final OfxCat ofxCat = new OfxCat(transactionCategoryStore, cli);

                // TODO: can this return a Stream<OfxTransaction> that we can run through transformations?
                final Set<OfxTransaction> ofxTransactions = ofxCat.parseOfxFile(file);

                // TODO: auto-discover the institution so that we can use the appropriate transaction cleaner
                final TransactionCleaner transactionCleaner = new RbcTransactionCleaner();
                final Set<Transaction> cleanedTransactions = ofxTransactions
                        .parallelStream()
                        .map(transactionCleaner::clean)
                        .collect(Collectors.toSet());
                final Set<CategorizedTransaction> categorizedTransactions = ofxCat.categorizeTransactions(cleanedTransactions);

                // TODO: present the results in a pleasing manner
                // TODO: save state of transaction store to disk

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
