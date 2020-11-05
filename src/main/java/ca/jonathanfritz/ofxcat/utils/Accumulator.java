package ca.jonathanfritz.ofxcat.utils;

import java.util.function.BiFunction;

/**
 * Container class for adding a series of values together over some period of time.
 * The object starts with an initial value and an addition function that describes how to add two instances of T together.
 * The addition function is executed whenever the {@link #add(T)} method is called, and the result is stored as the current value.
 */
public class Accumulator<T> {
    private T currentValue;
    private final BiFunction<T, T, T> additionFunction;

    public Accumulator(T initialValue, BiFunction<T, T, T> additionFunction) {
        this.currentValue = initialValue;
        this.additionFunction = additionFunction;
    }

    public T add(T term) {
        currentValue = additionFunction.apply(currentValue, term);
        return currentValue;
    }

    public T getCurrentValue() {
        return currentValue;
    }
}
