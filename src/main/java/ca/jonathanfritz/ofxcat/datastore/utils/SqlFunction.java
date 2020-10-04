package ca.jonathanfritz.ofxcat.datastore.utils;

import java.sql.SQLException;

/**
 * Just like a {@link java.util.function.Function}, except that its {@link #apply(T)} method can throw an instance
 * of {@link SQLException}
 * @param <T> the type of object consumed by the {@link #apply(T)} method
 * @param <R> the type of object that will be returned by the {@link #apply(T)} method
 */
@FunctionalInterface
public interface SqlFunction<T, R> {
    R apply(T t) throws SQLException;
}
