package ca.jonathanfritz.ofxcat.datastore;

import ca.jonathanfritz.ofxcat.AbstractDatabaseTest;
import ca.jonathanfritz.ofxcat.TestUtils;
import ca.jonathanfritz.ofxcat.datastore.dto.Account;
import ca.jonathanfritz.ofxcat.datastore.dto.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import ca.jonathanfritz.ofxcat.datastore.dto.Transfer;
import ca.jonathanfritz.ofxcat.datastore.utils.DatabaseTransaction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

class TransferDaoTest extends AbstractDatabaseTest {

    private final AccountDao accountDao;
    private final CategorizedTransactionDao categorizedTransactionDao;
    private final TransferDao transferDao;

    TransferDaoTest() {
        accountDao = injector.getInstance(AccountDao.class);
        categorizedTransactionDao = injector.getInstance(CategorizedTransactionDao.class);
        transferDao = injector.getInstance(TransferDao.class);
    }

    @Test
    public void test() {
        // create a source and sink
        final Account checking = accountDao.insert(TestUtils.createRandomAccount()).get();
        final Account savings = accountDao.insert(TestUtils.createRandomAccount()).get();
        final CategorizedTransaction source = categorizedTransactionDao.insert(new CategorizedTransaction(TestUtils.createRandomTransaction(checking), Category.TRANSFER)).get();
        final CategorizedTransaction sink = categorizedTransactionDao.insert(new CategorizedTransaction(TestUtils.createRandomTransaction(savings), Category.TRANSFER)).get();

        // create a transfer
        final Transfer expected = transferDao.insert(new Transfer(source, sink)).get();

        // get it back
        final Transfer actual = transferDao.select(expected.getId()).get();
        Assertions.assertNotNull(actual.getId());
        Assertions.assertEquals(expected, actual);
    }

    @Test
    public void selectByFitIdTest() {
        // create a source and sink
        final Account checking = accountDao.insert(TestUtils.createRandomAccount()).get();
        final Account savings = accountDao.insert(TestUtils.createRandomAccount()).get();
        final CategorizedTransaction source = categorizedTransactionDao.insert(new CategorizedTransaction(TestUtils.createRandomTransaction(checking), Category.TRANSFER)).get();
        final CategorizedTransaction sink = categorizedTransactionDao.insert(new CategorizedTransaction(TestUtils.createRandomTransaction(savings), Category.TRANSFER)).get();

        // create a transfer
        final Transfer expected = transferDao.insert(new Transfer(source, sink)).get();

        // get it back
        Transfer actual = transferDao.selectByFitId(source.getFitId()).get();
        Assertions.assertEquals(expected, actual);

        actual = transferDao.selectByFitId(sink.getFitId()).get();
        Assertions.assertEquals(expected, actual);
    }

    @Test
    public void isDuplicateTest() throws SQLException {
        // create a source and sink
        final Account checking = accountDao.insert(TestUtils.createRandomAccount()).get();
        final Account savings = accountDao.insert(TestUtils.createRandomAccount()).get();
        final CategorizedTransaction source = categorizedTransactionDao.insert(new CategorizedTransaction(TestUtils.createRandomTransaction(checking), Category.TRANSFER)).get();
        final CategorizedTransaction sink = categorizedTransactionDao.insert(new CategorizedTransaction(TestUtils.createRandomTransaction(savings), Category.TRANSFER)).get();

        try (final DatabaseTransaction t = new DatabaseTransaction(connection)) {
            // the transfer is not a duplicate before it has been inserted
            Transfer expected = new Transfer(source, sink);
            Assertions.assertFalse(transferDao.isDuplicate(t, expected));

            // but once it is inserted, it becomes a duplicate
            expected = transferDao.insert(expected).get();
            Assertions.assertTrue(transferDao.isDuplicate(t, expected));
        }
    }
}