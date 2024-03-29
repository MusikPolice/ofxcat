package ca.jonathanfritz.ofxcat.utils;

import ca.jonathanfritz.ofxcat.OfxCat;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.file.Path;

public class PathUtils {

    private static final Logger logger = LogManager.getLogger(PathUtils.class);

    /**
     * Returns the full path to the ~/.ofxcat directory
     */
    public Path getDataPath() {
        final String homeDirectory = System.getProperty("user.home");
        return join(homeDirectory, ".ofxcat");
    }

    public Path getImportedFilesPath() {
        return getDataPath().resolve("imported");
    }

    public String getDatabaseConnectionString() {
        final String connectionString = String.format("%s%s", "jdbc:sqlite:", join(getDataPath().toString(), "ofxcat.db"));
        logger.info("Database connection string is " + connectionString);
        return connectionString;
    }

    /**
     * Joins multiple file path components together, ensuring that exactly one instance of {@link File#separator} is between each component
     */
    public Path join(String ...components) {
        final StringBuilder sb = new StringBuilder();
        for (String component : components) {
            if (sb.length() == 0) {
                sb.append(component);
            } else if (sb.lastIndexOf(File.separator) == sb.length() - 1) {
                if (component.startsWith(File.separator)) {
                    sb.append(component.substring(1));
                } else {
                    sb.append(component);
                }
            } else {
                if (!component.startsWith(File.separator)) {
                    sb.append(File.separator);
                }
                sb.append(component);
            }
        }
        return Path.of(sb.toString());
    }

    /**
     * Expands unix-style path notations, including:
     * <ul>
     *     <li>~/ for current user's home directory</li>
     * </ul>
     */
    public Path expand(String path) {
        // handle unix-style home directory notation (i.e. ~/Documents/someFile.ofx)
        if (path.startsWith("~" + File.separatorChar)) {
            final String homeDirectory = System.getProperty("user.home");
            return join(homeDirectory, path.substring(1));
        }

        return Path.of(path);
    }
}
