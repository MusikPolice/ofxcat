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
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flywaydb.core.Flyway;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;

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

    private static final Logger logger = LogManager.getLogger(OfxCat.class);

    @Inject
    OfxCat(Flyway flyway, TransactionImportService transactionImportService, ReportingService reportingService, PathUtils pathUtils, CLI cli) {
        this.flyway = flyway;
        this.transactionImportService = transactionImportService;
        this.reportingService = reportingService;
        this.pathUtils = pathUtils;
        this.cli = cli;
    }

    private void migrateDatabase() {
        logger.debug("Attempting to migrate database schema...");
        flyway.migrate();
        logger.info("Database schema migration complete");
    }

    private void importTransactions(String path) throws OfxCatException {
        final Path pathToImportFile = pathUtils.expand(path);
        if (!(Files.exists(pathToImportFile) && Files.isReadable(pathToImportFile))) {
            throw new CliException("Import file path either does not exist or cannot be read");
        }

        // TODO: show a progress bar?
        // TODO: retain scrolling list of categorizations on screen
        transactionImportService.importTransactions(pathToImportFile.toFile());

        try {
            // copy the imported file to the data directory so that we have a record of everything that has been imported
            final Path backupPath = pathUtils.getImportedFilesPath().resolve(pathToImportFile.getFileName());
            if (!Files.isDirectory(backupPath.getParent())) {
                Files.createDirectories(backupPath.getParent());
            }
            if (!Files.exists(backupPath)) {
                Files.copy(pathToImportFile, backupPath);
            }
        } catch (IOException ex) {
            throw new CliException("Failed to copy imported file to data directory", ex);
        }

        if (cli.promptYesNo(String.format("Delete import file %s?", path))) {
            try {
                Files.delete(pathToImportFile);
            } catch (IOException ex) {
                throw new CliException("Failed to delete import file", ex);
            }
        }

        cli.waitForInput("Press enter to exit");
        cli.exit();
    }

    private void reportTransactions(OfxCatOptions options) {
        if (options.categoryId != null) {
            reportingService.reportTransactionsInCategory(options.categoryId, options.startDate, options.endDate);
        } else {
            reportingService.reportTransactionsMonthly(options.startDate, options.endDate);
        }
    }

    private void reportAccounts() {
        reportingService.reportAccounts();
    }

    private void reportCategories() {
        reportingService.reportCategories();
    }

    private void printHelp() {
        cli.println(Arrays.asList(
                "ofxcat import [FILENAME]",
                "   Imports the transactions in the specified *.ofx file.",
                "ofxcat get accounts",
                "   Prints a list of known accounts in CSV format.",
                "ofxcat get categories",
                "   Prints a list of known transaction categories in CSV format.",
                "ofxcat get transactions --start-date=[START DATE] [OPTIONS]",
                "   Prints the amount spent in each known transaction category.",
                "   --start-date: Required. Start date inclusive in format yyyy-mm-dd",
                "   --end-date: Optional. End date inclusive in format yyyy-mm-dd",
                "               Defaults to today if not specified.",
                "   --category-id: Optional. If specified, only transactions that belong" +
                " to the specified category will be printed." +
                "ofxcat help",
                "   Displays this help text"
        ));
    }

    // TODO: add a mode that allows reprocessing of transactions from some category
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
                        case TRANSACTIONS ->
                                // TODO: add a way to export actual transactions, not just category sums
                                ofxCat.reportTransactions(getOptions(args));
                        case ACCOUNTS -> ofxCat.reportAccounts();
                        case CATEGORIES ->
                                // TODO: need a way to edit categories and category descriptions
                                ofxCat.reportCategories();
                    }
                    break;
                case HELP:
                    ofxCat.printHelp();
                    break;
            }
        } catch (OfxCatException ex) {
            logger.error("Caught unhandled exception", ex);
            ofxCat.printHelp();
        }
    }

    private static OfxCat initializeApplication(PathUtils pathUtils) {
        final Injector injector = Guice.createInjector(
                new CLIModule(),
                DatastoreModule.onDisk(pathUtils.getDatabaseConnectionString())
        );
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
        HELP

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
                    .required(true)
                    .build());
            options.addOption(Option.builder()
                    .argName("e")
                    .longOpt("end-date")
                    .desc("End date (inclusive) in format yyyy-mm-dd")
                    .hasArg(true)
                    .required(false)
                    .build());
            options.addOption(Option.builder()
                    .argName("c")
                    .longOpt("category-id")
                    .desc("Unique id of the Category with which to filter results")
                    .hasArg(true)
                    .required(false)
                    .build());

            final CommandLineParser commandLineParser = new DefaultParser();
            final CommandLine commandLine = commandLineParser.parse(options, Arrays.copyOfRange(args, 2, args.length));
            final LocalDate startDate = toLocalDate(commandLine.getOptionValue("start-date"));
            final LocalDate endDate = toLocalDate(commandLine.getOptionValue("end-date"), LocalDate.now());
            final String categoryIdOptionValue = commandLine.getOptionValue("category-id");
            final Long categoryId = categoryIdOptionValue != null ? Long.parseLong(categoryIdOptionValue) : null;
            return new OfxCatOptions(startDate, endDate, categoryId);
        } catch (ParseException e) {
            throw new CliException("Failed to parse options", e);
        }
    }

    private static LocalDate toLocalDate(String dateToConvert, LocalDate defaultValue) {
        if (StringUtils.isBlank(dateToConvert)) {
            return defaultValue;
        }
        return toLocalDate(dateToConvert);
    }

    private static LocalDate toLocalDate(String dateToConvert) {
        if (StringUtils.isBlank(dateToConvert)) {
            throw new IllegalArgumentException("dateToConvert is null or blank");
        }
        try {
            return LocalDate.parse(dateToConvert, DateTimeFormatter.ISO_DATE);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Failed to parse date string " + dateToConvert, ex);
        }
    }

    private record OfxCatOptions(LocalDate startDate, LocalDate endDate, Long categoryId) { }
}
