package ca.jonathanfritz.ofxcat.utils;

import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;

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
}