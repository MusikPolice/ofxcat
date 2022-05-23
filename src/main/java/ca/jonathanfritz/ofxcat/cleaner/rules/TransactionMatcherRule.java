package ca.jonathanfritz.ofxcat.cleaner.rules;

import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import ca.jonathanfritz.ofxcat.io.OfxTransaction;
import com.webcohesion.ofx4j.domain.data.common.TransactionType;

import java.util.function.Function;
import java.util.regex.Pattern;

import static ca.jonathanfritz.ofxcat.utils.StringUtils.coerceNullableString;

public class TransactionMatcherRule {
    // if all of these conditions match...
    private final TransactionType typeMatcher;
    private final AmountMatcherRule amountMatcher;
    private final Pattern nameMatcher;
    private final Pattern memoMatcher;

    // then apply this transformation
    private final Function<OfxTransaction, Transaction.Builder> transformFunction;

    private TransactionMatcherRule(Builder builder, Function<OfxTransaction, Transaction.Builder> transformFunction) {
        this.typeMatcher = builder.typeMatcher;
        this.amountMatcher = builder.amountMatcher;
        this.nameMatcher = builder.nameMatcher;
        this.memoMatcher = builder.memoMatcher;
        this.transformFunction = transformFunction;
    }

    public boolean matches(OfxTransaction ofxTransaction) {
        final boolean typeMatches = typeMatcher == null || ofxTransaction.getType() == typeMatcher;
        final boolean amountMatches = amountMatcher == null || amountMatcher.match(ofxTransaction);
        final boolean nameMatches = nameMatcher == null || nameMatcher.matcher(
                coerceNullableString(ofxTransaction.getName())
                        .toUpperCase()
                        .trim()
        ).matches();
        final boolean memoMatches = memoMatcher == null || memoMatcher.matcher(
                (coerceNullableString(ofxTransaction.getMemo()))
                        .toUpperCase()
                        .trim()
        ).matches();

        return typeMatches && amountMatches && nameMatches && memoMatches;
    }

    public Transaction.Builder apply(OfxTransaction ofxTransaction) {
        return transformFunction.apply(ofxTransaction);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {
        private TransactionType typeMatcher = null;
        private AmountMatcherRule amountMatcher = null;
        private Pattern nameMatcher = null;
        private Pattern memoMatcher = null;

        private Builder() {
        }

        public Builder withType(TransactionType typeMatcher) {
            this.typeMatcher = typeMatcher;
            return this;
        }

        public Builder withAmount(AmountMatcherRule amountMatcher) {
            this.amountMatcher = amountMatcher;
            return this;
        }

        public Builder withName(Pattern nameMatcher) {
            this.nameMatcher = nameMatcher;
            return this;
        }

        public Builder withMemo(Pattern memoMatcher) {
            this.memoMatcher = memoMatcher;
            return this;
        }

        public TransactionMatcherRule build(Function<OfxTransaction, Transaction.Builder> transformFunction) {
            return new TransactionMatcherRule(this, transformFunction);
        }
    }
}
