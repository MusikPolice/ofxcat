package ca.jonathanfritz.ofxcat;

import ca.jonathanfritz.ofxcat.cli.CLIModule;
import ca.jonathanfritz.ofxcat.datastore.utils.DatastoreModule;
import ca.jonathanfritz.ofxcat.service.TransactionImportService;
import ca.jonathanfritz.ofxcat.utils.PathUtils;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.webcohesion.ofx4j.OFXException;
import org.apache.commons.cli.*;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * The entrypoint to the application
 * Handles CLI argument parsing and Guice injector setup
 */
public class OfxCat {

    private final TransactionImportService transactionImportService;

    private static final Logger log = LoggerFactory.getLogger(OfxCat.class);

    @Inject
    OfxCat(Flyway flyway, TransactionImportService transactionImportService) {
        this.transactionImportService = transactionImportService;

        // before we can do anything, we have to make sure that the database is up to date
        log.debug("Attempting to migrate database schema...");
        flyway.migrate();
        log.info("Database schema migration complete");
    }

    private void importTransactions(File file) throws OFXException {
        transactionImportService.importTransactions(file);
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
