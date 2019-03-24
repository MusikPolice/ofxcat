package ca.jonathanfritz.ofxcat;

import ca.jonathanfritz.ofxcat.transactions.TransactionCategoryStore;
import ca.jonathanfritz.ofxcat.transactions.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.transactions.Transaction;
import ca.jonathanfritz.ofxcat.io.OfxParser;
import com.webcohesion.ofx4j.OFXException;
import com.webcohesion.ofx4j.io.OFXParseException;
import org.apache.commons.cli.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Set;

public class OfxCat {

    private final TransactionCategoryStore transactionCategoryStore;

    public OfxCat(TransactionCategoryStore transactionCategoryStore) {
        this.transactionCategoryStore = transactionCategoryStore;
    }

    private Set<Transaction> parseOfxFile(final File inputFile) throws OFXException {
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
        // TODO - use the transaction store to categorize transactions
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
                final OfxCat ofxCat = new OfxCat(transactionCategoryStore);
                final Set<Transaction> transactions = ofxCat.parseOfxFile(file);
                final Set<CategorizedTransaction> categorizedTransactions = ofxCat.categorizeTransactions(transactions);

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
