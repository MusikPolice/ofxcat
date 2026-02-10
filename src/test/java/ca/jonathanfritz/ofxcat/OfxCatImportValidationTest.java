package ca.jonathanfritz.ofxcat;

import ca.jonathanfritz.ofxcat.cli.CLI;
import ca.jonathanfritz.ofxcat.config.AppConfig;
import ca.jonathanfritz.ofxcat.exception.CliException;
import ca.jonathanfritz.ofxcat.matching.KeywordRulesConfig;
import ca.jonathanfritz.ofxcat.service.CategoryCombineService;
import ca.jonathanfritz.ofxcat.service.MigrationReport;
import ca.jonathanfritz.ofxcat.service.ReportingService;
import ca.jonathanfritz.ofxcat.service.TokenMigrationService;
import ca.jonathanfritz.ofxcat.service.TransactionImportService;
import ca.jonathanfritz.ofxcat.utils.PathUtils;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for OfxCat.importTransactions file validation.
 * Verifies that the import command properly validates file paths
 * and provides clear error messages.
 */
class OfxCatImportValidationTest {

    @TempDir
    Path tempDir;

    private OfxCat ofxCat;
    private TestPathUtils testPathUtils;
    private boolean importServiceCalled;
    private File importedFile;

    @BeforeEach
    void setUp() {
        importServiceCalled = false;
        importedFile = null;
        testPathUtils = new TestPathUtils(tempDir);

        // Create OfxCat with test doubles
        ofxCat = new OfxCat(
                new StubFlyway(),
                new StubTransactionImportService(),
                new StubReportingService(),
                new StubTokenMigrationService(),
                new StubCategoryCombineService(),
                testPathUtils,
                new StubCLI(),
                KeywordRulesConfig.empty(),
                AppConfig.defaults()
        );
    }

    @Test
    void importWithNonExistentFile() {
        // Setup: Path to file that doesn't exist
        String nonExistentPath = tempDir.resolve("does-not-exist.ofx").toString();

        // Execute & Verify: Should throw CliException
        CliException exception = assertThrows(CliException.class, () -> {
            ofxCat.importTransactions(nonExistentPath);
        });

        assertTrue(exception.getMessage().contains("does not exist") ||
                   exception.getMessage().contains("cannot be read"),
                "Error message should indicate file doesn't exist, got: " + exception.getMessage());
        assertFalse(importServiceCalled, "Import service should not be called for non-existent file");
    }

    @Test
    void importWithDirectoryPassesValidation() throws IOException {
        // Setup: Create a directory instead of a file
        Path directory = tempDir.resolve("some-directory");
        Files.createDirectory(directory);

        // Note: The current validation only checks Files.exists() && Files.isReadable()
        // Directories pass this check, so validation succeeds.
        // The error will occur later when trying to parse the directory as an OFX file.
        // This test documents the current behavior.

        // Execute: Validation passes, import service is called
        // (The import service stub doesn't actually parse, so no error)
        assertDoesNotThrow(() -> {
            ofxCat.importTransactions(directory.toString());
        });

        // The import service was called (it would fail in real usage when parsing)
        assertTrue(importServiceCalled,
                "Import service is called even for directories (validation doesn't check isFile)");
    }

    @Test
    void importWithValidFile() throws Exception {
        // Setup: Create a valid file
        Path validFile = tempDir.resolve("valid.ofx");
        Files.writeString(validFile, "<?xml version=\"1.0\"?><OFX></OFX>");

        // Execute: Should not throw, should call import service
        assertDoesNotThrow(() -> {
            ofxCat.importTransactions(validFile.toString());
        });

        assertTrue(importServiceCalled, "Import service should be called for valid file");
        assertEquals(validFile.toFile(), importedFile, "Import service should receive the correct file");
    }

    @Test
    void importWithTildeExpansion() throws Exception {
        // Setup: Create a file in a "home" directory simulation
        // The TestPathUtils will expand ~ to tempDir
        Path homeFile = tempDir.resolve("test-file.ofx");
        Files.writeString(homeFile, "<?xml version=\"1.0\"?><OFX></OFX>");

        // Execute with ~ path (TestPathUtils expands ~ to tempDir)
        String tildePathInput = "~/test-file.ofx";

        assertDoesNotThrow(() -> {
            ofxCat.importTransactions(tildePathInput);
        });

        assertTrue(importServiceCalled, "Import service should be called after tilde expansion");
    }

    @Test
    void importWithEmptyPathUsesCurrentDirectory() {
        // Setup: Empty string path
        String emptyPath = "";

        // Note: Path.of("") returns the current working directory, which exists and is readable.
        // This means empty path passes validation and import service is called.
        // This test documents the current behavior.

        // Execute: Validation passes because "" resolves to current directory
        assertDoesNotThrow(() -> {
            ofxCat.importTransactions(emptyPath);
        });

        // Import service was called (it would fail in real usage when parsing a directory)
        assertTrue(importServiceCalled,
                "Empty path resolves to current directory, which passes exists/readable check");
    }

    @Test
    void importBackupDirectoryCreatedIfNotExists() throws Exception {
        // Setup: Valid file, but imported files directory doesn't exist yet
        Path validFile = tempDir.resolve("valid.ofx");
        Files.writeString(validFile, "<?xml version=\"1.0\"?><OFX></OFX>");

        // Execute
        ofxCat.importTransactions(validFile.toString());

        // Verify: Backup directory should be created
        Path importedDir = testPathUtils.getImportedFilesPath();
        assertTrue(Files.isDirectory(importedDir),
                "Imported files directory should be created");
    }

    @Test
    void importFileCopiedToBackup() throws Exception {
        // Setup: Valid file
        String fileContent = "<?xml version=\"1.0\"?><OFX>test content</OFX>";
        Path validFile = tempDir.resolve("valid.ofx");
        Files.writeString(validFile, fileContent);

        // Execute
        ofxCat.importTransactions(validFile.toString());

        // Verify: File should be copied to backup location
        Path backupFile = testPathUtils.getImportedFilesPath().resolve("valid.ofx");
        assertTrue(Files.exists(backupFile), "Backup file should exist");
        assertEquals(fileContent, Files.readString(backupFile), "Backup content should match original");
    }

    @Test
    void databaseBackupCreatedBeforeImport() throws Exception {
        // Setup: Create database file and valid import file
        Path databaseFile = testPathUtils.getDatabasePath();
        String dbContent = "database content";
        Files.writeString(databaseFile, dbContent);

        Path validFile = tempDir.resolve("valid.ofx");
        Files.writeString(validFile, "<?xml version=\"1.0\"?><OFX></OFX>");

        // Execute
        ofxCat.importTransactions(validFile.toString());

        // Verify: Database backup should exist
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        Path backupPath = testPathUtils.getBackupsPath().resolve("ofxcat-" + today + ".db");
        assertTrue(Files.exists(backupPath), "Database backup should be created");
        assertEquals(dbContent, Files.readString(backupPath), "Backup content should match database");
    }

    @Test
    void databaseBackupDirectoryCreatedIfNotExists() throws Exception {
        // Setup: Create database file, but backups directory doesn't exist
        Path databaseFile = testPathUtils.getDatabasePath();
        Files.writeString(databaseFile, "database content");

        Path validFile = tempDir.resolve("valid.ofx");
        Files.writeString(validFile, "<?xml version=\"1.0\"?><OFX></OFX>");

        // Verify: Backups directory doesn't exist yet
        assertFalse(Files.exists(testPathUtils.getBackupsPath()));

        // Execute
        ofxCat.importTransactions(validFile.toString());

        // Verify: Backups directory should be created
        assertTrue(Files.isDirectory(testPathUtils.getBackupsPath()),
                "Backups directory should be created");
    }

    @Test
    void databaseBackupSkippedIfAlreadyExistsForToday() throws Exception {
        // Setup: Create database file and existing backup for today
        Path databaseFile = testPathUtils.getDatabasePath();
        String originalDbContent = "original database content";
        Files.writeString(databaseFile, originalDbContent);

        Path backupsDir = testPathUtils.getBackupsPath();
        Files.createDirectories(backupsDir);

        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        Path existingBackup = backupsDir.resolve("ofxcat-" + today + ".db");
        String existingBackupContent = "existing backup content";
        Files.writeString(existingBackup, existingBackupContent);

        Path validFile = tempDir.resolve("valid.ofx");
        Files.writeString(validFile, "<?xml version=\"1.0\"?><OFX></OFX>");

        // Execute
        ofxCat.importTransactions(validFile.toString());

        // Verify: Existing backup should not be overwritten
        assertEquals(existingBackupContent, Files.readString(existingBackup),
                "Existing backup should not be overwritten");
    }

    @Test
    void importFailsGracefullyIfBackupFails() throws Exception {
        // Setup: Create database file and configure PathUtils to fail backup
        Path databaseFile = testPathUtils.getDatabasePath();
        Files.writeString(databaseFile, "database content");

        // Create a file where the backup directory should be, so createDirectories will fail
        Path blockerFile = tempDir.resolve("blocker-file");
        Files.writeString(blockerFile, "this is a file, not a directory");

        testPathUtils.setFailBackup(true);

        Path validFile = tempDir.resolve("valid.ofx");
        Files.writeString(validFile, "<?xml version=\"1.0\"?><OFX></OFX>");

        // Execute & Verify: Should throw CliException
        CliException exception = assertThrows(CliException.class, () -> {
            ofxCat.importTransactions(validFile.toString());
        });

        assertTrue(exception.getMessage().contains("backup"),
                "Error message should mention backup failure, got: " + exception.getMessage());
        assertFalse(importServiceCalled, "Import service should not be called when backup fails");
    }

    @Test
    void databaseBackupSkippedIfNoDatabaseExists() throws Exception {
        // Setup: Valid import file, but no database file exists
        Path validFile = tempDir.resolve("valid.ofx");
        Files.writeString(validFile, "<?xml version=\"1.0\"?><OFX></OFX>");

        // Verify: Database doesn't exist
        assertFalse(Files.exists(testPathUtils.getDatabasePath()));

        // Execute: Should not throw, backup is skipped
        assertDoesNotThrow(() -> {
            ofxCat.importTransactions(validFile.toString());
        });

        assertTrue(importServiceCalled, "Import service should be called");
        // Backups directory may or may not exist (implementation doesn't create it if no db)
    }

    // Test doubles

    private class TestPathUtils extends PathUtils {
        private final Path basePath;
        private boolean failBackup = false;

        TestPathUtils(Path basePath) {
            this.basePath = basePath;
        }

        void setFailBackup(boolean failBackup) {
            this.failBackup = failBackup;
        }

        @Override
        public Path expand(String path) {
            // Expand ~ to the temp directory for testing
            if (path.startsWith("~" + File.separatorChar) || path.startsWith("~/")) {
                return basePath.resolve(path.substring(2));
            }
            return Path.of(path);
        }

        @Override
        public Path getImportedFilesPath() {
            return basePath.resolve("imported");
        }

        @Override
        public Path getDataPath() {
            return basePath;
        }

        @Override
        public Path getBackupsPath() {
            if (failBackup) {
                // Return a path inside a file (not a directory) to cause IOException
                // when createDirectories is called
                return basePath.resolve("blocker-file").resolve("backups");
            }
            return basePath.resolve("backups");
        }

        @Override
        public Path getDatabasePath() {
            return basePath.resolve("ofxcat.db");
        }
    }

    private class StubTransactionImportService extends TransactionImportService {
        StubTransactionImportService() {
            // 12 null params to match constructor: CLI, OfxParser, AccountDao, TransactionCleanerFactory,
            // Connection, CategorizedTransactionDao, TransactionCategoryService, CategoryDao,
            // TransferMatchingService, TransferDao, TransactionTokenDao, TokenNormalizer
            super(null, null, null, null, null, null, null, null, null, null, null, null);
        }

        @Override
        public void importTransactions(File file) {
            importServiceCalled = true;
            importedFile = file;
        }
    }

    private static class StubReportingService extends ReportingService {
        StubReportingService() {
            // 4 null params: CategorizedTransactionDao, AccountDao, CategoryDao, CLI
            super(null, null, null, null);
        }
    }

    private static class StubTokenMigrationService extends TokenMigrationService {
        StubTokenMigrationService() {
            // 6 null params: Connection, CategorizedTransactionDao, TransactionTokenDao,
            // CategoryDao, TokenNormalizer, KeywordRulesConfig
            super(null, null, null, null, null, null);
        }

        @Override
        public boolean isMigrationNeeded() {
            return false;
        }

        @Override
        public MigrationReport migrateExistingTransactions() {
            return new MigrationReport();
        }
    }

    private static class StubCategoryCombineService extends CategoryCombineService {
        StubCategoryCombineService() {
            super(null, null, null);
        }
    }

    private static class StubFlyway extends Flyway {
        StubFlyway() {
            super(Flyway.configure());
        }

        // Note: migrate() returns MigrateResult in newer Flyway, so we don't override it
        // The parent implementation will be used, which is fine for testing
    }

    private static class StubCLI extends CLI {
        StubCLI() {
            super(null, null);
        }

        @Override
        public boolean promptYesNo(String prompt) {
            return false; // Don't delete the file
        }

        @Override
        public void waitForInput(String prompt) {
            // No-op
        }

        @Override
        public void exit() {
            // No-op
        }
    }
}
