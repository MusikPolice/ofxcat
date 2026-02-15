package ca.jonathanfritz.ofxcat.utils;

import static org.hamcrest.MatcherAssert.assertThat;

import java.io.File;
import java.nio.file.Path;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Test;

class PathUtilsTest {

    private final PathUtils pathUtils = new PathUtils();

    @Test
    void noSeparatorsTest() {
        final Path joined = pathUtils.join("C:\\Users", "SomeUser");
        assertThat(joined.toString(), IsEqual.equalTo("C:\\Users" + File.separator + "SomeUser"));
    }

    @Test
    void trailingSeparatorTest() {
        final Path joined = pathUtils.join("C:\\Users" + File.separator, "SomeUser");
        assertThat(joined.toString(), IsEqual.equalTo("C:\\Users" + File.separator + "SomeUser"));
    }

    @Test
    void leadingSeparatorTest() {
        final Path joined = pathUtils.join("C:\\Users", File.separator + "SomeUser");
        assertThat(joined.toString(), IsEqual.equalTo("C:\\Users" + File.separator + "SomeUser"));
    }

    @Test
    void leadingAndTrailingSeparatorTest() {
        final Path joined = pathUtils.join("C:\\Users" + File.separator, File.separator + "SomeUser");
        assertThat(joined.toString(), IsEqual.equalTo("C:\\Users" + File.separator + "SomeUser"));
    }

    @Test
    void getBackupsPathReturnsCorrectPath() {
        final Path backupsPath = pathUtils.getBackupsPath();
        final Path expectedPath = pathUtils.getDataPath().resolve("backups");
        assertThat(backupsPath, IsEqual.equalTo(expectedPath));
    }

    @Test
    void getDatabasePathReturnsCorrectPath() {
        final Path databasePath = pathUtils.getDatabasePath();
        final Path expectedPath = pathUtils.getDataPath().resolve("ofxcat.db");
        assertThat(databasePath, IsEqual.equalTo(expectedPath));
    }
}
