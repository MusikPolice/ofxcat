package ca.jonathanfritz.ofxcat.model;

import java.time.LocalDate;

/**
 * Represents a detected recurring charge from a single vendor at a consistent interval and amount.
 *
 * @param vendorName the vendor's display name
 * @param frequency the billing period (e.g. "MONTHLY", "ANNUAL")
 * @param typicalAmount the median transaction amount (negative = money out)
 * @param lastCharge the date of the most recent transaction in the group
 * @param nextExpected the estimated next charge date (lastCharge + one billing period)
 */
public record Subscription(
        String vendorName, String frequency, float typicalAmount, LocalDate lastCharge, LocalDate nextExpected) {}
