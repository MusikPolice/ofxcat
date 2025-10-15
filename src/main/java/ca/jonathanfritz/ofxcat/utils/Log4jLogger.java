package ca.jonathanfritz.ofxcat.utils;

import org.apache.logging.log4j.LogManager;
import org.slf4j.Logger;

/**
 * This is a terrible hack.
 * org.beryx:text-io has a transitive dependency on org.slf4j:slf4j-api that it uses to create a static instance of
 * org.slf4j.Logger. Unfortunately, SLF4J insists on polluting my terminal output with a warning that claims that no
 * SLF4J implementations are found at runtime, even if I include org.slf4j:slf4j-simple or
 * org.apache.logging.log4j:log4j-slf4j18-impl as dependencies.
 * My ugly hack solution to this problem was to exclude the transitive dependency on org.slf4j:slf4j-api from
 * org.beryx:text-io, and to wrap an instance of org.apache.logging.log4j.Logger with the org.slf4j.Logger API. This
 * tricks org.beryx:text-io into behaving at runtime and manages to suppress that annoying warning.
 */
public class Log4jLogger implements Logger {

    private final org.apache.logging.log4j.Logger log;

    public <T> Log4jLogger(Class<T> clazz) {
        log = LogManager.getLogger(clazz);
    }

    // Level enabled checks
    @Override
    public boolean isTraceEnabled() { return log.isTraceEnabled(); }
    @Override
    public boolean isDebugEnabled() { return log.isDebugEnabled(); }
    @Override
    public boolean isInfoEnabled() { return log.isInfoEnabled(); }
    @Override
    public boolean isWarnEnabled() { return log.isWarnEnabled(); }
    @Override
    public boolean isErrorEnabled() { return log.isErrorEnabled(); }

    // TRACE
    @Override
    public void trace(String message) { log.trace(message); }
    @Override
    public void trace(String format, Object arg) { log.trace(format, arg); }
    @Override
    public void trace(String format, Object arg1, Object arg2) { log.trace(format, arg1, arg2); }
    @Override
    public void trace(String format, Object[] argArray) { log.trace(format, argArray); }
    @Override
    public void trace(String message, Throwable t) { log.trace(message, t); }

    // DEBUG
    @Override
    public void debug(String message) { log.debug(message); }
    @Override
    public void debug(String format, Object arg) { log.debug(format, arg); }
    @Override
    public void debug(String format, Object arg1, Object arg2) { log.debug(format, arg1, arg2); }
    @Override
    public void debug(String format, Object[] argArray) { log.debug(format, argArray); }
    @Override
    public void debug(String message, Throwable t) { log.debug(message, t); }

    // INFO
    @Override
    public void info(String message) { log.info(message); }
    @Override
    public void info(String format, Object arg) { log.info(format, arg); }
    @Override
    public void info(String format, Object arg1, Object arg2) { log.info(format, arg1, arg2); }
    @Override
    public void info(String format, Object[] argArray) { log.info(format, argArray); }
    @Override
    public void info(String message, Throwable t) { log.info(message, t); }

    // WARN
    @Override
    public void warn(String message) { log.warn(message); }
    @Override
    public void warn(String format, Object arg) { log.warn(format, arg); }
    @Override
    public void warn(String format, Object arg1, Object arg2) { log.warn(format, arg1, arg2); }
    @Override
    public void warn(String format, Object[] argArray) { log.warn(format, argArray); }
    @Override
    public void warn(String message, Throwable t) { log.warn(message, t); }

    // ERROR
    @Override
    public void error(String message) { log.error(message); }
    @Override
    public void error(String format, Object arg) { log.error(format, arg); }
    @Override
    public void error(String format, Object arg1, Object arg2) { log.error(format, arg1, arg2); }
    @Override
    public void error(String format, Object[] argArray) { log.error(format, argArray); }
    @Override
    public void error(String message, Throwable t) { log.error(message, t); }

    @Override
    public String getName() { return log.getName(); }
}
