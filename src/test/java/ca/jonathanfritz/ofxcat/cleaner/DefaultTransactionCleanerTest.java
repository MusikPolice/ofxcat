package ca.jonathanfritz.ofxcat.cleaner;

import static ca.jonathanfritz.ofxcat.cleaner.DefaultTransactionCleaner.DEFAULT_BANK_ID;
import static ca.jonathanfritz.ofxcat.cleaner.DefaultTransactionCleaner.DEFAULT_INSTITUTION_NAME;

import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import ca.jonathanfritz.ofxcat.io.OfxTransaction;
import com.webcohesion.ofx4j.domain.data.common.TransactionType;
import java.time.LocalDate;
import java.util.UUID;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Test;

class DefaultTransactionCleanerTest {

    private final TransactionCleaner defaultTransactionCleaner = new DefaultTransactionCleaner();

    @Test
    public void descriptionConcatenationNullNameTest() {
        final OfxTransaction ofxTransaction =
                OfxTransaction.newBuilder().setMemo("Someplace").build();

        final Transaction transaction =
                defaultTransactionCleaner.clean(ofxTransaction).build();
        MatcherAssert.assertThat(transaction.getDescription(), IsEqual.equalTo("SOMEPLACE"));
    }

    @Test
    public void descriptionConcatenationNullMemoTest() {
        final OfxTransaction ofxTransaction =
                OfxTransaction.newBuilder().setName("Someplace").build();

        final Transaction transaction =
                defaultTransactionCleaner.clean(ofxTransaction).build();
        MatcherAssert.assertThat(transaction.getDescription(), IsEqual.equalTo("SOMEPLACE"));
    }

    @Test
    public void descriptionConcatenationTest() {
        final OfxTransaction ofxTransaction = OfxTransaction.newBuilder()
                .setName("Someplace ")
                .setMemo(" NicE")
                .build();

        final Transaction transaction =
                defaultTransactionCleaner.clean(ofxTransaction).build();
        MatcherAssert.assertThat(transaction.getDescription(), IsEqual.equalTo("SOMEPLACE NICE"));
    }

    @Test
    public void dateTest() {
        final OfxTransaction ofxTransaction =
                OfxTransaction.newBuilder().setDate(LocalDate.now()).build();

        final Transaction transaction =
                defaultTransactionCleaner.clean(ofxTransaction).build();
        MatcherAssert.assertThat(transaction.getDate(), IsEqual.equalTo(ofxTransaction.getDate()));
    }

    @Test
    public void amountTest() {
        final OfxTransaction ofxTransaction =
                OfxTransaction.newBuilder().setAmount(134.52f).build();

        final Transaction transaction =
                defaultTransactionCleaner.clean(ofxTransaction).build();
        MatcherAssert.assertThat(transaction.getAmount(), IsEqual.equalTo(ofxTransaction.getAmount()));
    }

    @Test
    public void defaultTypeTest() {
        final OfxTransaction ofxTransaction = OfxTransaction.newBuilder().build();

        final Transaction transaction =
                defaultTransactionCleaner.clean(ofxTransaction).build();
        MatcherAssert.assertThat(transaction.getType(), IsEqual.equalTo(Transaction.TransactionType.OTHER));
    }

    @Test
    public void matchingTypeTest() {
        final OfxTransaction ofxTransaction =
                OfxTransaction.newBuilder().setType(TransactionType.ATM).build();

        final Transaction transaction =
                defaultTransactionCleaner.clean(ofxTransaction).build();
        MatcherAssert.assertThat(transaction.getType(), IsEqual.equalTo(Transaction.TransactionType.ATM));
    }

    @Test
    public void fitIdTest() {
        final OfxTransaction ofxTransaction = OfxTransaction.newBuilder()
                .setFitId(UUID.randomUUID().toString())
                .build();

        final Transaction transaction =
                defaultTransactionCleaner.clean(ofxTransaction).build();
        MatcherAssert.assertThat(transaction.getFitId(), IsEqual.equalTo(ofxTransaction.getFitId()));
    }

    @Test
    public void getBankIdTest() {
        MatcherAssert.assertThat(defaultTransactionCleaner.getBankId(), IsEqual.equalTo(DEFAULT_BANK_ID));
    }

    @Test
    public void getInstitutionNameTest() {
        MatcherAssert.assertThat(
                defaultTransactionCleaner.getInstitutionName(), IsEqual.equalTo(DEFAULT_INSTITUTION_NAME));
    }
}
