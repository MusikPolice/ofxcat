package ca.jonathanfritz.ofxcat;

import ca.jonathanfritz.ofxcat.exception.CliException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OfxCatTest {

    @Test
    void getMode_parsesMigrateMode() throws CliException {
        OfxCat.Mode mode = OfxCat.getMode(new String[]{"migrate"});
        assertEquals(OfxCat.Mode.MIGRATE, mode);
    }

    @Test
    void getMode_parsesMigrateModeIgnoresCase() throws CliException {
        OfxCat.Mode mode = OfxCat.getMode(new String[]{"MIGRATE"});
        assertEquals(OfxCat.Mode.MIGRATE, mode);
    }

    @Test
    void getMigrateOptions_defaultsToNotDryRun() throws CliException {
        OfxCat.MigrateOptions options = OfxCat.getMigrateOptions(new String[]{"migrate"});
        assertFalse(options.dryRun());
    }

    @Test
    void getMigrateOptions_parsesDryRunFlag() throws CliException {
        OfxCat.MigrateOptions options = OfxCat.getMigrateOptions(new String[]{"migrate", "--dry-run"});
        assertTrue(options.dryRun());
    }

    @Test
    void getMigrateOptions_parsesDryRunFlagWithOtherArgs() throws CliException {
        // Even if there are other args (like extra flags), dry-run should be parsed
        OfxCat.MigrateOptions options = OfxCat.getMigrateOptions(new String[]{"migrate", "--dry-run"});
        assertTrue(options.dryRun());
    }
}