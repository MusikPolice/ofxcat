package org.slf4j;

/**
 * This is a partial copy of the <a href="https://www.slf4j.org/api/org/slf4j/Logger.html">org.slf4.Logger</a> interface
 * See the comment in {@link ca.jonathanfritz.ofxcat.utils.Log4jLogger} for an explanation of why it is required.
 */
public interface Logger {

    void debug(String message);

    void debug(String format, Object arg);

    void debug(String format, Object[] argArray);

    void debug(String message, Throwable t);

    void info(String message);

    void info(String format, Object arg);

    void info(String format, Object[] argArray);

    void info(String message, Throwable t);

    void warn(String message);

    void warn(String format, Object arg);

    void warn(String format, Object[] argArray);

    void warn(String message, Throwable t);

    void error(String message);

    void error(String format, Object arg);

    void error(String format, Object[] argArray);

    void error(String message, Throwable t);
}
