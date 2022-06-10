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
        MatcherAssert.assertThat(transaction.getDescription(), IsEqual.equalTo("OUTGOING INTERAC E-TRANSFER"));
    }

    @Test
    public void interacEtransferSentTest() {
        final OfxTransaction ofxTransaction = OfxTransaction.newBuilder()
                .setName("Email Trfs")
                .setMemo("E-TRANSFER SENT ")
                .build();

        final Transaction transaction = rbcTransactionCleaner.clean(ofxTransaction).build();
        MatcherAssert.assertThat(transaction.getDescription(), IsEqual.equalTo("OUTGOING INTERAC E-TRANSFER"));
    }

    @Test
    public void interacEtransferCancelledTest() {
        final OfxTransaction ofxTransaction = OfxTransaction.newBuilder()
                .setType(TransactionType.CREDIT)
                .setName("Email Trfs Can")
                .setMemo("E-TRANSFER CANCEL")
                .build();

        final Transaction transaction = rbcTransactionCleaner.clean(ofxTransaction).build();
        MatcherAssert.assertThat(transaction.getDescription(), IsEqual.equalTo("CANCELLED INTERAC E-TRANSFER"));
    }

    @Test
    public void usdPurchaseTest() {
        final OfxTransaction ofxTransaction = OfxTransaction.newBuilder()
                .setName("SUBSTACK SUBSTACK.COM CA        ")
                .setMemo("5.00 USD @ 1.294000000000")
                .build();

        final Transaction transaction = rbcTransactionCleaner.clean(ofxTransaction).build();
        MatcherAssert.assertThat(transaction.getDescription(), IsEqual.equalTo("SUBSTACK SUBSTACK.COM CA (USD PURCHASE)"));
    }

    @Test
    public void outgoingInterAccountTransferTrfDdaTest() {
        final OfxTransaction ofxTransaction = OfxTransaction.newBuilder()
                .setType(TransactionType.DEBIT)
                .setAmount(-100.00f)
                .setName("WWW TRF DDA - 0565              ")
                .build();

        final Transaction transaction = rbcTransactionCleaner.clean(ofxTransaction).build();
        MatcherAssert.assertThat(transaction.getDescription(), IsEqual.equalTo("TRANSFER OUT OF ACCOUNT"));
        MatcherAssert.assertThat(transaction.getType(), IsEqual.equalTo(Transaction.TransactionType.XFER));
    }

    @Test
    public void incomingInterAccountTransferTrfDdaTest() {
        final OfxTransaction ofxTransaction = OfxTransaction.newBuilder()
                .setType(TransactionType.DEBIT)
                .setAmount(100.00f)
                .setName("WWW TRF DDA - 0565              ")
                .build();

        final Transaction transaction = rbcTransactionCleaner.clean(ofxTransaction).build();
        MatcherAssert.assertThat(transaction.getDescription(), IsEqual.equalTo("TRANSFER INTO ACCOUNT"));
        MatcherAssert.assertThat(transaction.getType(), IsEqual.equalTo(Transaction.TransactionType.XFER));
    }

    @Test
    public void outgoingInterAccountTransferWwwTransferTest() {
        final OfxTransaction ofxTransaction = OfxTransaction.newBuilder()
                .setType(TransactionType.DEBIT)
                .setAmount(-100.00f)
                .setName("Transfer                        ")
                .setMemo("WWW TRANSFER - 7288 ")
                .build();

        final Transaction transaction = rbcTransactionCleaner.clean(ofxTransaction).build();
        MatcherAssert.assertThat(transaction.getDescription(), IsEqual.equalTo("TRANSFER OUT OF ACCOUNT"));
        MatcherAssert.assertThat(transaction.getType(), IsEqual.equalTo(Transaction.TransactionType.XFER));
    }

    @Test
    public void incomingInterAccountTransferWwwTransferTest() {
        final OfxTransaction ofxTransaction = OfxTransaction.newBuilder()
                .setType(TransactionType.DEBIT)
                .setAmount(100.00f)
                .setName("Transfer                        ")
                .setMemo("WWW TRANSFER - 7288 ")
                .build();

        final Transaction transaction = rbcTransactionCleaner.clean(ofxTransaction).build();
        MatcherAssert.assertThat(transaction.getDescription(), IsEqual.equalTo("TRANSFER INTO ACCOUNT"));
        MatcherAssert.assertThat(transaction.getType(), IsEqual.equalTo(Transaction.TransactionType.XFER));
    }

    @Test
    public void interacServiceChargeScTest() {
        final OfxTransaction ofxTransaction = OfxTransaction.newBuilder()
                .setAmount(-2.00f)
                .setFitId("90000010020210913S001F508AFAB")
                .setName("INTERAC-SC-7891                 ")
                .build();

        final Transaction transaction = rbcTransactionCleaner.clean(ofxTransaction).build();
        MatcherAssert.assertThat(transaction.getDescription(), IsEqual.equalTo("INTERAC E-TRANSFER SERVICE CHARGE"));
        MatcherAssert.assertThat(transaction.getType(), IsEqual.equalTo(Transaction.TransactionType.FEE));
    }

    @Test
    public void interacServiceChargeFeeTest() {
        final OfxTransaction ofxTransaction = OfxTransaction.newBuilder()
                .setAmount(-1.00f)
                .setFitId("90000010030205111S002C37F0F62")
                .setName("INT E-TRF FEE                   ")
                .build();

        final Transaction transaction = rbcTransactionCleaner.clean(ofxTransaction).build();
        MatcherAssert.assertThat(transaction.getDescription(), IsEqual.equalTo("INTERAC E-TRANSFER SERVICE CHARGE"));
        MatcherAssert.assertThat(transaction.getType(), IsEqual.equalTo(Transaction.TransactionType.FEE));
    }

    @Test
    public void wireTransferTest() {
        final OfxTransaction ofxTransaction = OfxTransaction.newBuilder()
                .setType(TransactionType.CREDIT)
                .setAmount(2400)
                .setName("FUNDS TRANSFER CR")
                .setMemo("TT JONATHAN M F")
                .build();

        final Transaction transaction = rbcTransactionCleaner.clean(ofxTransaction).build();
        MatcherAssert.assertThat(transaction.getDescription(), IsEqual.equalTo("WIRE TRANSFER"));
        MatcherAssert.assertThat(transaction.getType(), IsEqual.equalTo(Transaction.TransactionType.CREDIT));
    }

    @Test
    public void lineOfCreditPaymentTest() {
        final OfxTransaction outgoing = OfxTransaction.newBuilder()
                .setType(TransactionType.CREDIT)
                .setAmount(300)
                .setName("WWW PMT TIN0-04601")
                .build();

        Transaction transaction = rbcTransactionCleaner.clean(outgoing).build();
        MatcherAssert.assertThat(transaction.getDescription(), IsEqual.equalTo("LINE OF CREDIT PAYMENT"));
        MatcherAssert.assertThat(transaction.getType(), IsEqual.equalTo(Transaction.TransactionType.XFER));

        final OfxTransaction incoming = OfxTransaction.newBuilder()
                .setType(TransactionType.DEBIT)
                .setAmount(-300)
                .setName("Loan Pmt")
                .setMemo("WWW LOAN PMT - 5784 ")
                .build();

        transaction = rbcTransactionCleaner.clean(incoming).build();
        MatcherAssert.assertThat(transaction.getDescription(), IsEqual.equalTo("LINE OF CREDIT PAYMENT"));
        MatcherAssert.assertThat(transaction.getType(), IsEqual.equalTo(Transaction.TransactionType.XFER));
    }
}