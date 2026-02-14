package ca.jonathanfritz.ofxcat.cleaner;

import java.util.UUID;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.Test;

class TransactionCleanerFactoryTest {

    private static final TransactionCleanerFactory factory = new TransactionCleanerFactory();

    @Test
    void findByBankIdRbcTest() {
        final TransactionCleaner tc = factory.findByBankId(RbcTransactionCleaner.RBC_BANK_ID);
        MatcherAssert.assertThat(tc instanceof RbcTransactionCleaner, Is.is(true));
    }

    @Test
    void findByBankIdDefaultTest() {
        final TransactionCleaner tc = factory.findByBankId(UUID.randomUUID().toString());
        MatcherAssert.assertThat(tc instanceof DefaultTransactionCleaner, Is.is(true));
    }
}
