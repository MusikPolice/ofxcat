package org.slf4j;

/**
 * This is a partial copy of the <a href="https://www.slf4j.org/api/org/slf4j/Logger.html">org.slf4j.Logger</a> interface
 * See the comment in {@link ca.jonathanfritz.ofxcat.utils.Log4jLogger} for an explanation of why it is required.
 */
public interface Logger {

    // Level enabled checks
    boolean isTraceEnabled();

    boolean isDebugEnabled();

    boolean isInfoEnabled();

    boolean isWarnEnabled();

    boolean isErrorEnabled();

    // TRACE
    void trace(String message);

    void trace(String format, Object arg);

    void trace(String format, Object arg1, Object arg2);

    void trace(String format, Object[] argArray);

    void trace(String message, Throwable t);

    // DEBUG
    void debug(String message);

    void debug(String format, Object arg);

    void debug(String format, Object arg1, Object arg2);

    void debug(String format, Object[] argArray);

    void debug(String message, Throwable t);

    // INFO
    void info(String message);

    void info(String format, Object arg);

    void info(String format, Object arg1, Object arg2);

    void info(String format, Object[] argArray);

    void info(String message, Throwable t);

    // WARN
    void warn(String message);

    void warn(String format, Object arg);

    void warn(String format, Object arg1, Object arg2);

    void warn(String format, Object[] argArray);

    void warn(String message, Throwable t);

    // ERROR
    void error(String message);

    void error(String format, Object arg);

    void error(String format, Object arg1, Object arg2);

    void error(String format, Object[] argArray);

    void error(String message, Throwable t);

    // Name
    String getName();
}
