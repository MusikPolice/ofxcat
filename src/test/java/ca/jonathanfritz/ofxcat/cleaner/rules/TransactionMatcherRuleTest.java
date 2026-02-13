package ca.jonathanfritz.ofxcat.cleaner.rules;

import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import ca.jonathanfritz.ofxcat.io.OfxTransaction;
import com.webcohesion.ofx4j.domain.data.common.TransactionType;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.hamcrest.MatcherAssert.assertThat;


class TransactionMatcherRuleTest {

    @Test
    public void typeMatcherTest() {
        final TransactionMatcherRule rule = TransactionMatcherRule.newBuilder()
                .withType(TransactionType.DEBIT)
                .build(ofxTransaction -> Transaction.newBuilder(""));

        // match
        OfxTransaction ofxTransaction = OfxTransaction.newBuilder()
                .setType(TransactionType.DEBIT)
                .build();
        assertThat(rule.matches(ofxTransaction), IsEqual.equalTo(true));

        // no match
        ofxTransaction = OfxTransaction.newBuilder()
                .setType(TransactionType.CREDIT)
                .build();
        assertThat(rule.matches(ofxTransaction), IsEqual.equalTo(false));
    }

    @Test
    public void amountMatcherTest() {
        final TransactionMatcherRule rule = TransactionMatcherRule.newBuilder()
                .withAmount(AmountMatcherRule.isEqualTo(5.00f))
                .build(ofxTransaction -> Transaction.newBuilder(""));

        // match
        OfxTransaction ofxTransaction = OfxTransaction.newBuilder()
                .setAmount(5.00f)
                .build();
        assertThat(rule.matches(ofxTransaction), IsEqual.equalTo(true));

        // no match
        ofxTransaction = OfxTransaction.newBuilder()
                .setAmount(6.00f)
                .build();
        assertThat(rule.matches(ofxTransaction), IsEqual.equalTo(false));
    }

    @Test
    public void nameMatcherTest() {
        final TransactionMatcherRule rule = TransactionMatcherRule.newBuilder()
                .withName(Pattern.compile("^Hello World$", Pattern.CASE_INSENSITIVE))
                .build(ofxTransaction -> Transaction.newBuilder(""));

        // match
        OfxTransaction ofxTransaction = OfxTransaction.newBuilder()
                .setName("Hello World")
                .build();
        assertThat(rule.matches(ofxTransaction), IsEqual.equalTo(true));

        // no match
        ofxTransaction = OfxTransaction.newBuilder()
                .setName("Goodbye World")
                .build();
        assertThat(rule.matches(ofxTransaction), IsEqual.equalTo(false));
    }

    @Test
    public void memoMatcherTest() {
        final TransactionMatcherRule rule = TransactionMatcherRule.newBuilder()
                .withMemo(Pattern.compile("^Hello World$", Pattern.CASE_INSENSITIVE))
                .build(ofxTransaction -> Transaction.newBuilder(""));

        // match
        OfxTransaction ofxTransaction = OfxTransaction.newBuilder()
                .setMemo("Hello World")
                .build();
        assertThat(rule.matches(ofxTransaction), IsEqual.equalTo(true));

        // no match
        ofxTransaction = OfxTransaction.newBuilder()
                .setMemo("Goodbye World")
                .build();
        assertThat(rule.matches(ofxTransaction), IsEqual.equalTo(false));
    }

    @Test
    public void multipleMatcherLogicalAndTest() {
        final TransactionMatcherRule rule = TransactionMatcherRule.newBuilder()
                .withMemo(Pattern.compile("^Hello World$", Pattern.CASE_INSENSITIVE))
                .withAmount(AmountMatcherRule.isEqualTo(100f))
                .build(ofxTransaction -> Transaction.newBuilder(""));

        // match
        OfxTransaction ofxTransaction = OfxTransaction.newBuilder()
                .setMemo("Hello World")
                .setAmount(100f)
                .build();
        assertThat(rule.matches(ofxTransaction), IsEqual.equalTo(true));

        // no match
        ofxTransaction = OfxTransaction.newBuilder()
                .setMemo("Hello World")
                .build();
        assertThat(rule.matches(ofxTransaction), IsEqual.equalTo(false));

        ofxTransaction = OfxTransaction.newBuilder()
                .setAmount(100f)
                .build();
        assertThat(rule.matches(ofxTransaction), IsEqual.equalTo(false));
    }
}
