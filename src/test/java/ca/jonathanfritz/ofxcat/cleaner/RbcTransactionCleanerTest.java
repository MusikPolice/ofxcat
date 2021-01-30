package ca.jonathanfritz.ofxcat.cleaner;

import ca.jonathanfritz.ofxcat.io.OfxTransaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static ca.jonathanfritz.ofxcat.cleaner.RbcTransactionCleaner.RBC_BANK_ID;
import static ca.jonathanfritz.ofxcat.cleaner.RbcTransactionCleaner.RBC_INSTITUTION_NAME;

class RbcTransactionCleanerTest {

    private final RbcTransactionCleaner rbcTransactionCleaner = new RbcTransactionCleaner();

    @Test
    public void descriptionConcatenationNullNameTest() {
        final OfxTransaction ofxTransaction = OfxTransaction.newBuilder()
                .setMemo("Someplace")
                .build();

        final Transaction transaction = rbcTransactionCleaner.clean(ofxTransaction).build();
        MatcherAssert.assertThat(transaction.getDescription(), IsEqual.equalTo("SOMEPLACE"));
    }

    @Test
    public void descriptionConcatenationNullMemoTest() {
        final OfxTransaction ofxTransaction = OfxTransaction.newBuilder()
                .setName("Someplace")
                .build();

        final Transaction transaction = rbcTransactionCleaner.clean(ofxTransaction).build();
        MatcherAssert.assertThat(transaction.getDescription(), IsEqual.equalTo("SOMEPLACE"));
    }

    @Test
    public void descriptionConcatenationTest() {
        final OfxTransaction ofxTransaction = OfxTransaction.newBuilder()
                .setName("Someplace ")
                .setMemo(" NicE")
                .build();

        final Transaction transaction = rbcTransactionCleaner.clean(ofxTransaction).build();
        MatcherAssert.assertThat(transaction.getDescription(), IsEqual.equalTo("SOMEPLACE NICE"));
    }

    @Test
    public void dateTest() {
        final OfxTransaction ofxTransaction = OfxTransaction.newBuilder()
                .setDate(LocalDate.now())
                .build();

        final Transaction transaction = rbcTransactionCleaner.clean(ofxTransaction).build();
        MatcherAssert.assertThat(transaction.getDate(), IsEqual.equalTo(ofxTransaction.getDate()));
    }

    @Test
    public void amountTest() {
        final OfxTransaction ofxTransaction = OfxTransaction.newBuilder()
                .setAmount(134.52f)
                .build();

        final Transaction transaction = rbcTransactionCleaner.clean(ofxTransaction).build();
        MatcherAssert.assertThat(transaction.getAmount(), IsEqual.equalTo(ofxTransaction.getAmount()));
    }

    @Test
    public void defaultTypeTest() {
        final OfxTransaction ofxTransaction = OfxTransaction.newBuilder()
                .build();

        final Transaction transaction = rbcTransactionCleaner.clean(ofxTransaction).build();
        MatcherAssert.assertThat(transaction.getType(), IsEqual.equalTo(Transaction.TransactionType.OTHER));
    }

    @Test
    public void matchingTypeTest() {
        final OfxTransaction ofxTransaction = OfxTransaction.newBuilder()
                .setType("atm")
                .build();

        final Transaction transaction = rbcTransactionCleaner.clean(ofxTransaction).build();
        MatcherAssert.assertThat(transaction.getType(), IsEqual.equalTo(Transaction.TransactionType.ATM));
    }

    @Test
    public void getBankIdTest() {
        MatcherAssert.assertThat(rbcTransactionCleaner.getBankId(), IsEqual.equalTo(RBC_BANK_ID));
    }

    @Test
    public void getInstitutionNameTest() {
        MatcherAssert.assertThat(rbcTransactionCleaner.getInstitutionName(), IsEqual.equalTo(RBC_INSTITUTION_NAME));
    }

    @Test
    public void personalLoanSplTest() {
        final OfxTransaction ofxTransaction = OfxTransaction.newBuilder()
                .setName("PERSONAL LOAN")
                .setMemo("SPL")
                .build();

        final Transaction transaction = rbcTransactionCleaner.clean(ofxTransaction).build();
        MatcherAssert.assertThat(transaction.getDescription(), IsEqual.equalTo("PERSONAL LOAN REPAYMENT"));
    }

    @Test
    public void interacEtransferTest() {
        final OfxTransaction ofxTransaction = OfxTransaction.newBuilder()
                .setName("Email Trfs")
                .setMemo("INTERAC E-TRF- 8766")
                .build();

        final Transaction transaction = rbcTransactionCleaner.clean(ofxTransaction).build();
        MatcherAssert.assertThat(transaction.getDescription(), IsEqual.equalTo("INTERAC E-TRANSFER"));
    }

    // TODO: test patterns to remove/replace regexes
}