package ca.jonathanfritz.ofxcat.utils;

import java.io.File;
import java.nio.file.Path;

public class PathUtils {

    /**
     * Returns the full path to the ~/.ofxcat directory
     */
    public static Path getDataPath() {
        final String homeDirectory = System.getProperty("user.home");
        return join(homeDirectory, ".ofxcat");
    }

    /**
     * Returns the full path to the ~/.ofxcat/transaction-categories.json file
     */
    public static Path getTransactionCategoryStorePath() {
        return join(getDataPath().toString(), "transaction-categories.json");
    }

    /**
     * Joins multiple file path components together, ensuring that exactly one instance of {@link File#separator} is between each component
     */
    public static Path join(String ...components) {
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
    public static Path expand(String path) {
        // handle unix-style home directory notation (i.e. ~/Documents/someFile.ofx)
        if (path.startsWith("~" + File.separatorChar)) {
            final String homeDirectory = System.getProperty("user.home");
            return PathUtils.join(homeDirectory, path.substring(1));
        }

        return Path.of(path);
    }
}
