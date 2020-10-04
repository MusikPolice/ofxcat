package ca.jonathanfritz.ofxcat.datastore;

import ca.jonathanfritz.ofxcat.AbstractDatabaseTest;
import ca.jonathanfritz.ofxcat.datastore.AccountDao;
import ca.jonathanfritz.ofxcat.transactions.Account;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

class AccountDaoTest extends AbstractDatabaseTest {

    @AfterEach
    void cleanup() {
        cleanDatabase();
    }

    @Test
    public void insertSuccessTest() {
        final Account accountToInsert = Account.newBuilder()
                .setBankId(UUID.randomUUID().toString())
                .setAccountId(UUID.randomUUID().toString())
                .setAccountType("Savings")
                .setName("Rainy Day Fund")
                .build();

        // insert the account
        final AccountDao accountDao = new AccountDao(connection);
        final Account insertedAccount = accountDao.insert(accountToInsert).get();
        Assertions.assertNotNull(insertedAccount.getId());
        Assertions.assertEquals(accountToInsert.getBankId(), insertedAccount.getBankId());
        Assertions.assertEquals(accountToInsert.getAccountId(), insertedAccount.getAccountId());
        Assertions.assertEquals(accountToInsert.getAccountType(), insertedAccount.getAccountType());
        Assertions.assertEquals(accountToInsert.getName(), insertedAccount.getName());

        // get it back
        final Account foundAccount = accountDao.select(insertedAccount.getId()).get();
        Assertions.assertNotSame(foundAccount, insertedAccount);
        Assertions.assertEquals(foundAccount, insertedAccount);
    }

    @Test
    public void selectByAccountIdSuccessTest() {
        final Account accountToInsert = Account.newBuilder()
                .setBankId(UUID.randomUUID().toString())
                .setAccountId(UUID.randomUUID().toString())
                .setAccountType("Nest Egg")
                .setName("All My Monies")
                .build();

        // insert the account
        final AccountDao accountDao = new AccountDao(connection);
        final Account insertedAccount = accountDao.insert(accountToInsert).get();

        // get it back
        final Account foundAccount = accountDao.selectByAccountNumber(accountToInsert.getAccountId()).get();
        Assertions.assertEquals(foundAccount, insertedAccount);
    }
}