package ca.jonathanfritz.ofxcat.utils;

import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;

class PathUtilsTest {

    @Test
    void noSeparatorsTest() {
        final Path joined = PathUtils.join("C:\\Users", "SomeUser");
        assertThat(joined.toString(), IsEqual.equalTo("C:\\Users" + File.separator + "SomeUser"));
    }

    @Test
    void trailingSeparatorTest() {
        final Path joined = PathUtils.join("C:\\Users" + File.separator, "SomeUser");
        assertThat(joined.toString(), IsEqual.equalTo("C:\\Users" + File.separator + "SomeUser"));
    }

    @Test
    void leadingSeparatorTest() {
        final Path joined = PathUtils.join("C:\\Users", File.separator + "SomeUser");
        assertThat(joined.toString(), IsEqual.equalTo("C:\\Users" + File.separator + "SomeUser"));
    }

    @Test
    void leadingAndTrailingSeparatorTest() {
        final Path joined = PathUtils.join("C:\\Users" + File.separator, File.separator + "SomeUser");
        assertThat(joined.toString(), IsEqual.equalTo("C:\\Users" + File.separator + "SomeUser"));
    }
}