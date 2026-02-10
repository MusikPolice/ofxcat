package ca.jonathanfritz.ofxcat;

import ca.jonathanfritz.ofxcat.cli.CLI;
import ca.jonathanfritz.ofxcat.cli.CLIModule;
import ca.jonathanfritz.ofxcat.config.AppConfig;
import ca.jonathanfritz.ofxcat.config.AppConfigLoader;
import ca.jonathanfritz.ofxcat.datastore.utils.DatastoreModule;
import ca.jonathanfritz.ofxcat.exception.CliException;
import ca.jonathanfritz.ofxcat.exception.OfxCatException;
import ca.jonathanfritz.ofxcat.matching.MatchingModule;
import ca.jonathanfritz.ofxcat.service.CategoryCombineService;
import ca.jonathanfritz.ofxcat.service.MigrationReport;
import ca.jonathanfritz.ofxcat.service.ReportingService;
import ca.jonathanfritz.ofxcat.service.TokenMigrationService;
import ca.jonathanfritz.ofxcat.service.TransactionImportService;
import ca.jonathanfritz.ofxcat.utils.PathUtils;
import com.google.inject.Guice;
import jakarta.inject.Inject;
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
    private final TokenMigrationService tokenMigrationService;
    private final CategoryCombineService categoryCombineService;
    private final PathUtils pathUtils;
    private final CLI cli;

    private static final Logger logger = LogManager.getLogger(OfxCat.class);

    @Inject
    OfxCat(Flyway flyway, TransactionImportService transactionImportService, ReportingService reportingService,
           TokenMigrationService tokenMigrationService, CategoryCombineService categoryCombineService,
           PathUtils pathUtils, CLI cli) {
        this.flyway = flyway;
        this.transactionImportService = transactionImportService;
        this.reportingService = reportingService;
        this.tokenMigrationService = tokenMigrationService;
        this.categoryCombineService = categoryCombineService;
        this.pathUtils = pathUtils;
        this.cli = cli;
    }

    private void migrateDatabase() {
        logger.debug("Attempting to migrate database schema...");
        flyway.migrate();
        logger.info("Database schema migration complete");
    }

    private void migrateTokens() {
        if (!tokenMigrationService.isMigrationNeeded()) {
            logger.debug("Token migration not needed");
            return;
        }

        cli.println("Migrating existing transactions to use tokens...");

        MigrationReport report = tokenMigrationService.migrateExistingTransactions(
                (current, total) -> cli.updateProgressBar("Migrating", current, total)
        );
        cli.finishProgressBar();

        cli.println(String.format("Token migration complete: %d processed, %d recategorized, %d skipped",
                report.getProcessedCount(), report.getRecategorizedCount(), report.getSkippedCount()));

        if (report.hasRecategorizations()) {
            cli.println("Recategorized transactions:");
            for (MigrationReport.RecategorizationEntry entry : report.getRecategorizations()) {
                cli.println(String.format("  %s : %s -> %s", entry.description(), entry.oldCategory(), entry.newCategory()));
            }
        }
    }

    // Package-private for testing
    void importTransactions(String path) throws OfxCatException {
        final Path pathToImportFile = pathUtils.expand(path);
        if (!(Files.exists(pathToImportFile) && Files.isReadable(pathToImportFile))) {
            throw new CliException("Import file path either does not exist or cannot be read");
        }

        // Create database backup before import
        backupDatabase();

        // TODO: show a progress bar?
        // TODO: retain scrolling list of categorizations on screen
        transactionImportService.importTransactions(pathToImportFile.toFile());

        backupOfxFile(pathToImportFile);
        deleteOfxFile(path, pathToImportFile);

        cli.waitForInput("Press enter to exit");
        cli.exit();
    }

    private void backupDatabase() throws CliException {
        try {
            final Path databasePath = pathUtils.getDatabasePath();
            if (Files.exists(databasePath)) {
                final Path backupDir = pathUtils.getBackupsPath();
                if (!Files.isDirectory(backupDir)) {
                    Files.createDirectories(backupDir);
                }
                final String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
                final Path backupPath = backupDir.resolve("ofxcat-" + today + ".db");
                if (!Files.exists(backupPath)) {
                    Files.copy(databasePath, backupPath);
                    logger.info("Database backed up to {}", backupPath);
                }
            }
        } catch (IOException ex) {
            throw new CliException("Failed to backup database before import", ex);
        }
    }

    private void backupOfxFile(Path pathToImportFile) throws CliException {
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
    }

    private void deleteOfxFile(String path, Path pathToImportFile) throws CliException {
        if (cli.promptYesNo(String.format("Delete import file %s?", path))) {
            try {
                Files.delete(pathToImportFile);
            } catch (IOException ex) {
                throw new CliException("Failed to delete import file", ex);
            }
        }
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

    private void runMigration(MigrateOptions options) {
        if (options.dryRun()) {
            cli.println("Dry run mode: showing what would change without making actual changes\n");
        } else {
            cli.println("Re-running token migration on all transactions...\n");
        }

        MigrationReport report = tokenMigrationService.forceMigration(
                options.dryRun(),
                (current, total) -> cli.updateProgressBar(options.dryRun() ? "Analyzing" : "Migrating", current, total)
        );
        cli.finishProgressBar();

        if (options.dryRun()) {
            cli.println(String.format("\nDry run results: %d would be processed, %d would be recategorized, %d would be skipped",
                    report.getProcessedCount(), report.getRecategorizedCount(), report.getSkippedCount()));
        } else {
            cli.println(String.format("\nMigration complete: %d processed, %d recategorized, %d skipped",
                    report.getProcessedCount(), report.getRecategorizedCount(), report.getSkippedCount()));
        }

        if (report.hasRecategorizations()) {
            cli.println(options.dryRun() ? "\nTransactions that would be recategorized:" : "\nRecategorized transactions:");
            for (MigrationReport.RecategorizationEntry entry : report.getRecategorizations()) {
                cli.println(String.format("  %s : %s -> %s", entry.description(), entry.oldCategory(), entry.newCategory()));
            }
        } else {
            cli.println("\nNo recategorizations " + (options.dryRun() ? "would be" : "were") + " made.");
        }
    }

    private void combineCategories(CombineOptions options) {
        cli.println(String.format("Combining category \"%s\" into \"%s\"...\n", options.source(), options.target()));

        try {
            CategoryCombineService.CombineResult result = categoryCombineService.combine(
                    options.source(),
                    options.target(),
                    (current, total) -> cli.updateProgressBar("Moving", current, total)
            );
            cli.finishProgressBar();

            if (result.targetCreated()) {
                cli.println(String.format("\nCreated category \"%s\"", result.targetName()));
            }
            cli.println(String.format("Complete: %d transactions moved from \"%s\" to \"%s\"",
                    result.transactionsMoved(), result.sourceName(), result.targetName()));
            cli.println(String.format("Category \"%s\" has been deleted.", result.sourceName()));
        } catch (IllegalArgumentException ex) {
            cli.println("Error: " + ex.getMessage());
        }
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
                "   --category-id: Optional. If specified, only transactions that belong",
                "                  to the specified category will be printed.",
                "ofxcat migrate [OPTIONS]",
                "   Re-runs token migration on all transactions, applying current keyword rules.",
                "   Use this after updating keyword-rules.yaml to recategorize existing transactions.",
                "   --dry-run: Optional. Show what would change without making actual changes.",
                "ofxcat combine categories --source=SOURCE --target=TARGET",
                "   Moves all transactions from the source category to the target category,",
                "   then deletes the source category. Useful for merging duplicate categories.",
                "   The target category is created if it doesn't already exist.",
                "   --source: Required. Name of the category to move transactions from.",
                "   --target: Required. Name of the category to move transactions to.",
                "ofxcat rename category --source=SOURCE --target=TARGET",
                "   Alias for 'combine categories'. Renames a category by moving all its",
                "   transactions to the target (created if it doesn't exist) and deleting the source.",
                "ofxcat help",
                "   Displays this help text"
        ));
    }

    // TODO: add a mode that allows reprocessing of transactions from some category
    public static void main(String[] args) {
        final PathUtils pathUtils = new PathUtils();

        // Load configuration, creating default if needed
        final AppConfigLoader configLoader = new AppConfigLoader();
        final AppConfigLoader.LoadResult configResult = configLoader.loadOrCreate(pathUtils.getConfigPath());
        if (configResult.wasCreated()) {
            System.out.println("Created default configuration file at: " + configResult.configPath());
            System.out.println("Edit this file to customize application behavior.");
            System.out.println();
        }

        final OfxCat ofxCat = initializeApplication(pathUtils, configResult.config());

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
                case MIGRATE:
                    ofxCat.runMigration(getMigrateOptions(args));
                    break;
                case COMBINE:
                    ofxCat.combineCategories(getCombineOptions(args));
                    break;
                case RENAME:
                    ofxCat.combineCategories(getRenameOptions(args));
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

    private static OfxCat initializeApplication(PathUtils pathUtils, AppConfig appConfig) {
        final Injector injector = Guice.createInjector(
                new CLIModule(),
                DatastoreModule.onDisk(pathUtils.getDatabaseConnectionString()),
                new MatchingModule(appConfig, pathUtils.getConfigPath())
        );
        final OfxCat ofxCat = injector.getInstance(OfxCat.class);
        ofxCat.migrateDatabase();
        ofxCat.migrateTokens();
        logger.debug("Application initialized with config: keyword_rules_path={}, overlap_threshold={}",
                appConfig.getKeywordRulesPath(),
                appConfig.getTokenMatching().getOverlapThreshold());
        return ofxCat;
    }

    // Package-private for testing
    static Mode getMode(String[] args) throws CliException {
        if (args.length == 0) {
            throw new CliException("Too few arguments specified");
        }
        return Arrays.stream(Mode.values())
                .filter(m -> m.name().equalsIgnoreCase(args[0]))
                .findFirst()
                .orElseThrow(() -> new CliException(String.format("Invalid mode %s specified", args[0])));
    }

    // Package-private for testing
    enum Mode {
        IMPORT,
        GET,
        MIGRATE,
        COMBINE,
        RENAME,
        HELP
    }

    // Package-private for testing
    static Concern getConcern(String[] args) {
        if (args.length < 2) {
            throw new RuntimeException("Too few arguments specified");
        }
        return Arrays.stream(Concern.values())
                .filter(c -> c.name().equalsIgnoreCase(args[1]))
                .findFirst()
                .orElseThrow(() -> new RuntimeException(String.format("Invalid concern %s specified", args[1])));
    }

    // Package-private for testing
    enum Concern {
        TRANSACTIONS,
        ACCOUNTS,
        CATEGORIES
    }

    // Package-private for testing
    static OfxCatOptions getOptions(String[] args) throws CliException {
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

    // Package-private for testing
    static LocalDate toLocalDate(String dateToConvert, LocalDate defaultValue) {
        if (StringUtils.isBlank(dateToConvert)) {
            return defaultValue;
        }
        return toLocalDate(dateToConvert);
    }

    // Package-private for testing
    static LocalDate toLocalDate(String dateToConvert) {
        if (StringUtils.isBlank(dateToConvert)) {
            throw new IllegalArgumentException("dateToConvert is null or blank");
        }
        try {
            return LocalDate.parse(dateToConvert, DateTimeFormatter.ISO_DATE);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Failed to parse date string " + dateToConvert, ex);
        }
    }

    // Package-private for testing
    record OfxCatOptions(LocalDate startDate, LocalDate endDate, Long categoryId) { }

    // Package-private for testing
    static MigrateOptions getMigrateOptions(String[] args) throws CliException {
        try {
            final Options options = new Options();
            options.addOption(Option.builder()
                    .argName("d")
                    .longOpt("dry-run")
                    .desc("Show what would change without making actual changes")
                    .hasArg(false)
                    .required(false)
                    .build());

            final CommandLineParser commandLineParser = new DefaultParser();
            final CommandLine commandLine = commandLineParser.parse(options, Arrays.copyOfRange(args, 1, args.length));
            final boolean dryRun = commandLine.hasOption("dry-run");
            return new MigrateOptions(dryRun);
        } catch (ParseException e) {
            throw new CliException("Failed to parse options", e);
        }
    }

    // Package-private for testing
    record MigrateOptions(boolean dryRun) { }

    // Package-private for testing
    static CombineOptions getCombineOptions(String[] args) throws CliException {
        if (args.length < 2 || !"categories".equalsIgnoreCase(args[1])) {
            throw new CliException("Usage: ofxcat combine categories --source=SOURCE --target=TARGET");
        }
        return parseSourceTargetOptions(args);
    }

    // Package-private for testing
    static CombineOptions getRenameOptions(String[] args) throws CliException {
        if (args.length < 2 || !"category".equalsIgnoreCase(args[1])) {
            throw new CliException("Usage: ofxcat rename category --source=SOURCE --target=TARGET");
        }
        return parseSourceTargetOptions(args);
    }

    private static CombineOptions parseSourceTargetOptions(String[] args) throws CliException {
        try {
            final Options options = new Options();
            options.addOption(Option.builder()
                    .argName("s")
                    .longOpt("source")
                    .desc("Name of the source category")
                    .hasArg(true)
                    .required(true)
                    .build());
            options.addOption(Option.builder()
                    .argName("t")
                    .longOpt("target")
                    .desc("Name of the target category")
                    .hasArg(true)
                    .required(true)
                    .build());

            final CommandLineParser commandLineParser = new DefaultParser();
            final CommandLine commandLine = commandLineParser.parse(options, Arrays.copyOfRange(args, 2, args.length));
            final String source = commandLine.getOptionValue("source");
            final String target = commandLine.getOptionValue("target");
            return new CombineOptions(source, target);
        } catch (ParseException e) {
            throw new CliException("Failed to parse options", e);
        }
    }

    // Package-private for testing
    record CombineOptions(String source, String target) { }
}
