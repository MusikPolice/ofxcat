package ca.jonathanfritz.ofxcat.datastore.utils;

import java.sql.SQLException;

/**
 * Just like a {@link java.util.function.Consumer<T>}, except that its {@link #accept(T)} method can throw an instance
 * of {@link SQLException}.
 * @param <T> the type of object consumed by the {@link #accept(T)} method
 */
@FunctionalInterface
public interface SqlConsumer<T> {
    void accept(T t) throws SQLException;
}
