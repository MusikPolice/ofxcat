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
                TestUtils.createRandomTransaction(checking, today, 100f, Transaction.TransactionType.XFER),
                TestUtils.createRandomTransaction(checking, today, 50f, Transaction.TransactionType.XFER),
                TestUtils.createRandomTransaction(checking, today, -20f, Transaction.TransactionType.XFER),
                TestUtils.createRandomTransaction(checking, today, 200f, Transaction.TransactionType.XFER)
        );
        accountTransactions.put(checking, checkingTransactions);

        final List<Transaction> savingsTransactions = Arrays.asList(
                TestUtils.createRandomTransaction(savings, today, -100f, Transaction.TransactionType.XFER),
                TestUtils.createRandomTransaction(savings, today, -50f, Transaction.TransactionType.XFER),
                TestUtils.createRandomTransaction(savings, today, 20f, Transaction.TransactionType.XFER)
        );
        accountTransactions.put(savings, savingsTransactions);

        // identify will find three transfers between checking and savings
        final Set<Transfer> transfers = service.match(accountTransactions);
        assertEquals(3, transfers.size());
        assertTrue(transfers.stream().anyMatch(t ->
                t.sink().equals(checkingTransactions.get(0)) && t.source().equals(savingsTransactions.get(0))
        ));
        assertTrue(transfers.stream().anyMatch(t ->
                t.sink().equals(checkingTransactions.get(1)) && t.source().equals(savingsTransactions.get(1))
        ));
        assertTrue(transfers.stream().anyMatch(t ->
                t.source().equals(checkingTransactions.get(2)) && t.sink().equals(savingsTransactions.get(2))
        ));

        // there will be one remaining unmatched transaction
        assertFalse(accountTransactions.containsKey(savings));
        assertTrue(accountTransactions.containsKey(checking));
        assertEquals(1, accountTransactions.get(checking).size());
        assertEquals(checkingTransactions.get(3), accountTransactions.get(checking).get(0));
    }
}