package ca.jonathanfritz.ofxcat.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Accumulator utility class.
 */
class AccumulatorTest {

    @Test
    void accumulatorWithSumFunction() {
        // Setup: Accumulator for summing floats
        Accumulator<Float> accumulator = new Accumulator<>(0.0f, Float::sum);

        // Execute: Add values
        accumulator.add(1.0f);
        accumulator.add(2.0f);
        accumulator.add(3.0f);

        // Verify: Sum is correct
        assertEquals(6.0f, accumulator.getCurrentValue(), 0.001f);
    }

    @Test
    void accumulatorWithNonZeroInitialValue() {
        // Setup: Accumulator starting at 100
        Accumulator<Float> accumulator = new Accumulator<>(100.0f, Float::sum);

        // Execute: Add negative values
        accumulator.add(-10.0f);
        accumulator.add(-20.0f);

        // Verify: Correctly subtracts from initial value
        assertEquals(70.0f, accumulator.getCurrentValue(), 0.001f);
    }

    @Test
    void accumulatorWithMultiplyFunction() {
        // Setup: Accumulator for multiplying integers
        Accumulator<Integer> accumulator = new Accumulator<>(2, (a, b) -> a * b);

        // Execute: Multiply values
        accumulator.add(3);
        accumulator.add(4);
        accumulator.add(5);

        // Verify: Product is correct (2 * 3 * 4 * 5 = 120)
        assertEquals(120, accumulator.getCurrentValue());
    }

    @Test
    void accumulatorWithMaxFunction() {
        // Setup: Accumulator that keeps the maximum value
        Accumulator<Integer> accumulator = new Accumulator<>(Integer.MIN_VALUE, Integer::max);

        // Execute: Add various values
        accumulator.add(5);
        accumulator.add(100);
        accumulator.add(42);
        accumulator.add(7);

        // Verify: Maximum value is retained
        assertEquals(100, accumulator.getCurrentValue());
    }

    @Test
    void accumulatorWithMinFunction() {
        // Setup: Accumulator that keeps the minimum value
        Accumulator<Integer> accumulator = new Accumulator<>(Integer.MAX_VALUE, Integer::min);

        // Execute: Add various values
        accumulator.add(50);
        accumulator.add(10);
        accumulator.add(42);
        accumulator.add(100);

        // Verify: Minimum value is retained
        assertEquals(10, accumulator.getCurrentValue());
    }

    @Test
    void accumulatorWithStringConcatenation() {
        // Setup: Accumulator for string concatenation
        Accumulator<String> accumulator = new Accumulator<>("Hello", String::concat);

        // Execute: Concatenate strings
        accumulator.add(" ");
        accumulator.add("World");
        accumulator.add("!");

        // Verify: Strings are concatenated
        assertEquals("Hello World!", accumulator.getCurrentValue());
    }

    @Test
    void accumulatorAddReturnsCurrentValue() {
        // Setup: Accumulator for summing
        Accumulator<Integer> accumulator = new Accumulator<>(0, Integer::sum);

        // Execute & Verify: add() returns the new accumulated value
        assertEquals(5, accumulator.add(5));
        assertEquals(15, accumulator.add(10));
        assertEquals(15, accumulator.getCurrentValue());
    }

    @Test
    void accumulatorWithEmptyStringInitial() {
        // Setup: Accumulator starting with empty string
        Accumulator<String> accumulator = new Accumulator<>("", String::concat);

        // Execute
        accumulator.add("test");

        // Verify
        assertEquals("test", accumulator.getCurrentValue());
    }

    @Test
    void accumulatorWithZeroAdditions() {
        // Setup: Accumulator with no additions
        Accumulator<Integer> accumulator = new Accumulator<>(42, Integer::sum);

        // Execute: Don't add anything

        // Verify: Initial value is preserved
        assertEquals(42, accumulator.getCurrentValue());
    }

    @Test
    void accumulatorWithNullSafeFunction() {
        // Setup: Accumulator that handles null gracefully
        Accumulator<String> accumulator = new Accumulator<>("default",
                (a, b) -> b != null ? a + b : a);

        // Execute: Add null value
        accumulator.add(null);
        accumulator.add(" suffix");

        // Verify: Null is handled
        assertEquals("default suffix", accumulator.getCurrentValue());
    }

    @Test
    void accumulatorWithFloatPrecision() {
        // Setup: Accumulator for floats (testing precision)
        // This documents the precision behavior when using floats
        Accumulator<Float> accumulator = new Accumulator<>(0.0f, Float::sum);

        // Execute: Add values that might cause precision issues
        accumulator.add(0.1f);
        accumulator.add(0.2f);

        // Verify: Note that 0.1 + 0.2 may not exactly equal 0.3 due to float precision
        // We use a small epsilon for comparison
        assertEquals(0.3f, accumulator.getCurrentValue(), 0.0001f);
    }

    @Test
    void accumulatorWithLargeNumbers() {
        // Setup: Accumulator for large numbers
        Accumulator<Long> accumulator = new Accumulator<>(0L, Long::sum);

        // Execute: Add large values
        accumulator.add(Long.MAX_VALUE / 2);
        accumulator.add(1000L);

        // Verify: Handles large numbers correctly
        assertEquals((Long.MAX_VALUE / 2) + 1000L, accumulator.getCurrentValue());
    }
}
