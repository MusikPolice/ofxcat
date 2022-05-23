package ca.jonathanfritz.ofxcat.cleaner.rules;

import ca.jonathanfritz.ofxcat.io.OfxTransaction;

public class AmountMatcherRule {
    private Float isEqualToMatcher;
    private Float isGreaterThanMatcher;
    private Float isLessThanMatcher;

    private AmountMatcherRule(Float isEqualToMatcher, Float isGreaterThanMatcher, Float isLessThanMatcher) {
        this.isEqualToMatcher = isEqualToMatcher;
        this.isGreaterThanMatcher = isGreaterThanMatcher;
        this.isLessThanMatcher = isLessThanMatcher;
    }

    public static AmountMatcherRule isEqualTo(float amount) {
        return new AmountMatcherRule(amount, null, null);
    }

    public static AmountMatcherRule isGreaterThan(float amount) {
        return new AmountMatcherRule(null, amount, null);
    }

    public static AmountMatcherRule isLessThan(float amount) {
        return new AmountMatcherRule(null, null, amount);
    }

    public boolean match(OfxTransaction ofxTransaction) {
        if (isEqualToMatcher != null) {
            return ofxTransaction.getAmount() == isEqualToMatcher;
        } else if (isGreaterThanMatcher != null) {
            return ofxTransaction.getAmount() > isGreaterThanMatcher;
        } else if (isLessThanMatcher != null) {
            return ofxTransaction.getAmount() < isLessThanMatcher;
        }
        return false;
    }
}
