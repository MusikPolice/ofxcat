package ca.jonathanfritz.ofxcat.cleaner.rules;

import ca.jonathanfritz.ofxcat.io.OfxTransaction;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;

class AmountMatcherRuleTest {

    @Test
    public void isEqualToTest() {
        // match
        final AmountMatcherRule rule = AmountMatcherRule.isEqualTo(5.01f);
        OfxTransaction ofxTransaction = OfxTransaction.newBuilder()
                .setAmount(5.01f)
                .build();
        assertThat(rule.match(ofxTransaction), IsEqual.equalTo(true));

        // no match
        ofxTransaction = OfxTransaction.newBuilder()
                .setAmount(6.99f)
                .build();
        assertThat(rule.match(ofxTransaction), IsEqual.equalTo(false));
    }

    @Test
    public void isGreaterThanTest() {
        // match
        final AmountMatcherRule rule = AmountMatcherRule.isGreaterThan(5.01f);
        OfxTransaction ofxTransaction = OfxTransaction.newBuilder()
                .setAmount(6.99f)
                .build();
        assertThat(rule.match(ofxTransaction), IsEqual.equalTo(true));

        // no match
        ofxTransaction = OfxTransaction.newBuilder()
                .setAmount(4.99f)
                .build();
        assertThat(rule.match(ofxTransaction), IsEqual.equalTo(false));

        // no match
        ofxTransaction = OfxTransaction.newBuilder()
                .setAmount(5.01f)
                .build();
        assertThat(rule.match(ofxTransaction), IsEqual.equalTo(false));
    }

    @Test
    public void isLessThanTest() {
        // match
        final AmountMatcherRule rule = AmountMatcherRule.isLessThan(5.01f);
        OfxTransaction ofxTransaction = OfxTransaction.newBuilder()
                .setAmount(4.99f)
                .build();
        assertThat(rule.match(ofxTransaction), IsEqual.equalTo(true));

        // no match
        ofxTransaction = OfxTransaction.newBuilder()
                .setAmount(6.99f)
                .build();
        assertThat(rule.match(ofxTransaction), IsEqual.equalTo(false));

        // no match
        ofxTransaction = OfxTransaction.newBuilder()
                .setAmount(5.01f)
                .build();
        assertThat(rule.match(ofxTransaction), IsEqual.equalTo(false));
    }
}
