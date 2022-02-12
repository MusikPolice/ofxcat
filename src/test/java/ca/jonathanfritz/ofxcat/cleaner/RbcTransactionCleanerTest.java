package ca.jonathanfritz.ofxcat.cleaner;

import ca.jonathanfritz.ofxcat.io.OfxTransaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import com.webcohesion.ofx4j.domain.data.common.TransactionType;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static ca.jonathanfritz.ofxcat.cleaner.RbcTransactionCleaner.RBC_BANK_ID;
import static ca.jonathanfritz.ofxcat.cleaner.RbcTransactionCleaner.RBC_INSTITUTION_NAME;

class RbcTransactionCleanerTest {

    private final TransactionCleaner rbcTransactionCleaner = new RbcTransactionCleaner();

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
                .setType(TransactionType.ATM)
                .build();

        final Transaction transaction = rbcTransactionCleaner.clean(ofxTransaction).build();
        MatcherAssert.assertThat(transaction.getType(), IsEqual.equalTo(Transaction.TransactionType.ATM));
    }

    @Test
    public void fitIdTest() {
        final OfxTransaction ofxTransaction = OfxTransaction.newBuilder()
                .setFitId(UUID.randomUUID().toString())
                .build();

        final Transaction transaction = rbcTransactionCleaner.clean(ofxTransaction).build();
        MatcherAssert.assertThat(transaction.getFitId(), IsEqual.equalTo(ofxTransaction.getFitId()));
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

    @Test
    public void interacEtransferSentTest() {
        final OfxTransaction ofxTransaction = OfxTransaction.newBuilder()
                .setName("Email Trfs")
                .setMemo("E-TRANSFER SENT ")
                .build();

        final Transaction transaction = rbcTransactionCleaner.clean(ofxTransaction).build();
        MatcherAssert.assertThat(transaction.getDescription(), IsEqual.equalTo("INTERAC E-TRANSFER E-TRANSFER SENT"));
    }

    @Test
    public void usdPurchaseTest() {
        final OfxTransaction ofxTransaction = OfxTransaction.newBuilder()
                .setName("SUBSTACK SUBSTACK.COM CA  ")
                .setMemo("5.00 USD @ 1.308000000000")
                .build();

        final Transaction transaction = rbcTransactionCleaner.clean(ofxTransaction).build();
        MatcherAssert.assertThat(transaction.getDescription(), IsEqual.equalTo("SUBSTACK SUBSTACK.COM CA (USD PURCHASE)"));
    }

    @Test
    public void interAccountTransferDebitTest() {
        final OfxTransaction ofxTransaction = OfxTransaction.newBuilder()
                .setType(TransactionType.DEBIT)
                .setDate(LocalDate.of(2021,9,13))
                .setAmount(-96.15f)
                .setFitId("90000010020210913C002AF3195DE")
                .setName("WWW TRF DDA - 6498  ")
                .build();

        final Transaction transaction = rbcTransactionCleaner.clean(ofxTransaction).build();
        MatcherAssert.assertThat(transaction.getDescription(), IsEqual.equalTo("TRANSFER TO ACCOUNT"));
    }

    @Test
    public void interAccountTransferCreditTest() {
        final OfxTransaction ofxTransaction = OfxTransaction.newBuilder()
                .setType(TransactionType.CREDIT)
                .setDate(LocalDate.of(2021,9,13))
                .setAmount(96.15f)
                .setFitId("90000010020210913S001F508AFAB")
                .setName("Transfer                        ")
                .setMemo("WWW TRANSFER - 6498 ")
                .build();

        final Transaction transaction = rbcTransactionCleaner.clean(ofxTransaction).build();
        MatcherAssert.assertThat(transaction.getDescription(), IsEqual.equalTo("TRANSFER FROM ACCOUNT"));
    }

    // TODO: test patterns to remove/replace regexes
}