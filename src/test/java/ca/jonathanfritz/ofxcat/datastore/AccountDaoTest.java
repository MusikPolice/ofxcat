package ca.jonathanfritz.ofxcat.datastore;

import ca.jonathanfritz.ofxcat.AbstractDatabaseTest;
import ca.jonathanfritz.ofxcat.datastore.dto.Account;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class AccountDaoTest extends AbstractDatabaseTest {

    @Test
    public void insertSuccessTest() {
        final Account accountToInsert = Account.newBuilder()
                .setBankId(UUID.randomUUID().toString())
                .setAccountNumber(UUID.randomUUID().toString())
                .setAccountType("Savings")
                .setName("Rainy Day Fund")
                .build();

        // insert the account
        final AccountDao accountDao = new AccountDao(connection);
        final Account insertedAccount = accountDao.insert(accountToInsert).get();
        Assertions.assertNotNull(insertedAccount.getId());
        Assertions.assertEquals(accountToInsert.getBankId(), insertedAccount.getBankId());
        Assertions.assertEquals(accountToInsert.getAccountNumber(), insertedAccount.getAccountNumber());
        Assertions.assertEquals(accountToInsert.getAccountType(), insertedAccount.getAccountType());
        Assertions.assertEquals(accountToInsert.getName(), insertedAccount.getName());

        // get it back
        final Account foundAccount = accountDao.select(insertedAccount.getId()).get();
        Assertions.assertNotSame(foundAccount, insertedAccount);
        Assertions.assertEquals(foundAccount, insertedAccount);
    }

    @Test
    public void selectAllTest() {
        // create some accounts
        final AccountDao accountDao = new AccountDao(connection);
        final List<Account> expected = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            final Account accountToInsert = Account.newBuilder()
                    .setBankId(UUID.randomUUID().toString())
                    .setAccountNumber(UUID.randomUUID().toString())
                    .setAccountType(UUID.randomUUID().toString())
                    .setName(UUID.randomUUID().toString())
                    .build();
            expected.add(accountDao.insert(accountToInsert).get());
        }

        // get the accounts
        final List<Account> actual = accountDao.select();
        Assertions.assertEquals(expected, actual);
    }

    @Test
    public void selectByAccountIdSuccessTest() {
        final Account accountToInsert = Account.newBuilder()
                .setBankId(UUID.randomUUID().toString())
                .setAccountNumber(UUID.randomUUID().toString())
                .setAccountType("Nest Egg")
                .setName("All My Monies")
                .build();

        // insert the account
        final AccountDao accountDao = new AccountDao(connection);
        final Account insertedAccount = accountDao.insert(accountToInsert).get();

        // get it back
        final Account foundAccount = accountDao.selectByAccountNumber(accountToInsert.getAccountNumber()).get();
        Assertions.assertEquals(foundAccount, insertedAccount);
    }
}