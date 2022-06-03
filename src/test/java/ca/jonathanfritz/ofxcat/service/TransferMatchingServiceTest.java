package ca.jonathanfritz.ofxcat.service;

import ca.jonathanfritz.ofxcat.TestUtils;
import ca.jonathanfritz.ofxcat.datastore.dto.Account;
import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Transfer;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TransferMatchingServiceTest {

    private final TransferMatchingService service = new TransferMatchingService();

    @Test
    public void test() {
        final LocalDate today = LocalDate.now();
        final Account checking = TestUtils.createRandomAccount("Checking");
        final Account savings = TestUtils.createRandomAccount("Savings");

        final Map<Account, List<Transaction>> accountTransactions = new HashMap<>();
        final List<Transaction> checkingTransactions = Arrays.asList(
                TestUtils.createRandomTransaction(checking, UUID.randomUUID().toString(), today, 100f, Transaction.TransactionType.XFER),
                TestUtils.createRandomTransaction(checking, UUID.randomUUID().toString(), today, 50f, Transaction.TransactionType.XFER),
                TestUtils.createRandomTransaction(checking, UUID.randomUUID().toString(), today, -20f, Transaction.TransactionType.XFER),
                TestUtils.createRandomTransaction(checking, UUID.randomUUID().toString(), today, 200f, Transaction.TransactionType.XFER)
        );
        accountTransactions.put(checking, checkingTransactions);

        final List<Transaction> savingsTransactions = Arrays.asList(
                TestUtils.createRandomTransaction(savings, UUID.randomUUID().toString(), today, -100f, Transaction.TransactionType.XFER),
                TestUtils.createRandomTransaction(savings, UUID.randomUUID().toString(), today, -50f, Transaction.TransactionType.XFER),
                TestUtils.createRandomTransaction(savings, UUID.randomUUID().toString(), today, 20f, Transaction.TransactionType.XFER)
        );
        accountTransactions.put(savings, savingsTransactions);

        // identify will find three transfers between checking and savings
        final Set<Transfer> transfers = service.match(accountTransactions);
        assertEquals(3, transfers.size());
        assertTrue(transfers.stream().anyMatch(t ->
                t.getSink().getFitId().equals(checkingTransactions.get(0).getFitId()) &&
                        t.getSource().getFitId().equals(savingsTransactions.get(0).getFitId())
        ));
        assertTrue(transfers.stream().anyMatch(t ->
                t.getSink().getFitId().equals(checkingTransactions.get(1).getFitId()) &&
                        t.getSource().getFitId().equals(savingsTransactions.get(1).getFitId())
        ));
        assertTrue(transfers.stream().anyMatch(t ->
                t.getSource().getFitId().equals(checkingTransactions.get(2).getFitId()) &&
                        t.getSink().getFitId().equals(savingsTransactions.get(2).getFitId())
        ));

        // there will be one remaining unmatched transaction
        assertEquals(2, accountTransactions.size());
        assertTrue(accountTransactions.containsKey(savings));
        assertTrue(accountTransactions.containsKey(checking));

        assertEquals(1, accountTransactions.get(checking).size());
        assertEquals(checkingTransactions.get(3), accountTransactions.get(checking).get(0));

        assertTrue(accountTransactions.get(savings).isEmpty());
    }
}