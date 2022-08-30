package org.slf4j;

import ca.jonathanfritz.ofxcat.utils.Log4jLogger;

/**
 * This implementation of the <a href="https://www.slf4j.org/api/org/slf4j/LoggerFactory.html">org.slf4.LoggerFactory</a>
 * binds the SLF4J API to my {@link Log4jLogger} implementation.
 * See the comment in {@link ca.jonathanfritz.ofxcat.utils.Log4jLogger} for an explanation of why it is required.
 */
public class LoggerFactory {

    public static <T> Logger getLogger(Class<T> clazz) {
        return new Log4jLogger(clazz);
    }
}
