package ca.jonathanfritz.ofxcat;

import ca.jonathanfritz.ofxcat.cli.CLI;
import ca.jonathanfritz.ofxcat.cli.CLIModule;
import ca.jonathanfritz.ofxcat.datastore.utils.DatastoreModule;
import ca.jonathanfritz.ofxcat.exception.CliException;
import ca.jonathanfritz.ofxcat.exception.OfxCatException;
import ca.jonathanfritz.ofxcat.service.ReportingService;
import ca.jonathanfritz.ofxcat.service.TransactionImportService;
import ca.jonathanfritz.ofxcat.utils.PathUtils;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.apache.commons.cli.*;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;

/**
 * The entrypoint to the application
 * Handles CLI argument parsing and Guice injector setup
 */
public class OfxCat {

    private final Flyway flyway;
    private final TransactionImportService transactionImportService;
    private final ReportingService reportingService;
    private final PathUtils pathUtils;
    private final CLI cli;

    private static final Logger log = LoggerFactory.getLogger(OfxCat.class);

    @Inject
    OfxCat(Flyway flyway, TransactionImportService transactionImportService, ReportingService reportingService, PathUtils pathUtils, CLI cli) {
        this.flyway = flyway;
        this.transactionImportService = transactionImportService;
        this.reportingService = reportingService;
        this.pathUtils = pathUtils;
        this.cli = cli;
    }

    private void migrateDatabase() {
        log.debug("Attempting to migrate database schema...");
        flyway.migrate();
        log.info("Database schema migration complete");
    }

    private void importTransactions(String path) throws OfxCatException {
        final Path pathToImportFile = pathUtils.expand(path);
        if (!(Files.exists(pathToImportFile) && Files.isReadable(pathToImportFile))) {
            throw new CliException("Import file path either does not exist or cannot be read");
        }
        transactionImportService.importTransactions(pathToImportFile.toFile());
    }

    private void reportTransactions(OfxCatOptions options) {
        reportingService.reportTransactions(options.startDate, options.endDate);
    }

    private void reportAccounts() {
        reportingService.reportAccounts();
    }

    private void reportCategories() {
        reportingService.reportCategories();
    }

    private void printHelp() {
        // TODO: actually print help docs
        cli.println("error", "HELP!");
    }

    private void printError(String message) {
        cli.println("error", message);
    }

    public static void main(String[] args) {
        final PathUtils pathUtils = new PathUtils();
        final OfxCat ofxCat = initializeApplication(pathUtils);

        try {
            // figure out which of the major modes we're in
            switch (getMode(args)) {
                case IMPORT:
                    // if mode is IMPORT, the second argument is the path to the file to import
                    if (args.length != 2) {
                        throw new CliException("Import file path not specified");
                    }
                    ofxCat.importTransactions(args[1]);
                    break;
                case GET:
                    // if mode is GET, determine which concern needs to be got
                    switch (getConcern(args)) {
                        case TRANSACTIONS:
                            ofxCat.reportTransactions(getOptions(args));
                            break;
                        case ACCOUNTS:
                            ofxCat.reportAccounts();
                            break;
                        case CATEGORIES:
                            // TODO: need a way to edit categories and category descriptions
                            ofxCat.reportCategories();
                            break;
                    }
                    break;
                case HELP:
                    ofxCat.printHelp();
                    break;
            }
        } catch (OfxCatException ex) {
            ofxCat.printError(ex.getMessage());
            ofxCat.printHelp();
        }
    }

    private static LocalDate toLocalDate(Date dateToConvert) {
        if (dateToConvert == null) {
            return null;
        }
        return dateToConvert.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
    }

    private static OfxCat initializeApplication(PathUtils pathUtils) {
        final Injector injector = Guice.createInjector(new CLIModule(), new DatastoreModule(pathUtils.getDatabaseConnectionString()));
        final OfxCat ofxCat = injector.getInstance(OfxCat.class);
        ofxCat.migrateDatabase();
        return ofxCat;
    }

    private static Mode getMode(String[] args) throws CliException {
        if (args.length == 0) {
            throw new CliException("Too few arguments specified");
        }
        return Arrays.stream(Mode.values())
                .filter(m -> m.name().equalsIgnoreCase(args[0]))
                .findFirst()
                .orElseThrow(() -> new CliException(String.format("Invalid mode %s specified", args[0])));
    }

    private enum Mode {
        IMPORT,
        GET,
        HELP;
    }

    private static Concern getConcern(String[] args) {
        if (args.length < 2) {
            throw new RuntimeException("Too few arguments specified");
        }
        return Arrays.stream(Concern.values())
                .filter(c -> c.name().equalsIgnoreCase(args[1]))
                .findFirst()
                .orElseThrow(() -> new RuntimeException(String.format("Invalid concern %s specified", args[1])));
    }

    private enum Concern {
        TRANSACTIONS,
        ACCOUNTS,
        CATEGORIES
    }

    private static OfxCatOptions getOptions(String[] args) throws CliException {
        try {
            // TODO: add group-by arg, values are category, day, week, month, year, type
            final Options options = new Options();
            options.addOption(Option.builder()
                    .argName("s")
                    .longOpt("start-date")
                    .desc("Start date (inclusive) in format yyyy-mm-dd")
                    .hasArg(true)
                    .type(LocalDate.class)
                    .required(true)
                    .build());
            options.addOption(Option.builder()
                    .argName("e")
                    .longOpt("end-date")
                    .desc("End date (inclusive) in format yyyy-mm-dd")
                    .hasArg(true)
                    .type(LocalDate.class)
                    .required(false)
                    .build());

            final CommandLineParser commandLineParser = new DefaultParser();
            final CommandLine commandLine = commandLineParser.parse(options, Arrays.copyOfRange(args, 2, args.length));
            final Date startDate = (Date) commandLine.getParsedOptionValue("s");
            final Date endDate = (Date) commandLine.getParsedOptionValue("e");
            return new OfxCatOptions(toLocalDate(startDate), toLocalDate(endDate));
        } catch (ParseException e) {
            throw new CliException("Failed to parse options", e);
        }
    }

    private static class OfxCatOptions {
        final LocalDate startDate;
        final LocalDate endDate;

        private OfxCatOptions(LocalDate startDate, LocalDate endDate) {
            this.startDate = startDate;
            this.endDate = endDate;
        }
    }
}
