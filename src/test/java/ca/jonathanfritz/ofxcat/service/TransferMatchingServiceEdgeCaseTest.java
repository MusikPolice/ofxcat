package ca.jonathanfritz.ofxcat.service;

import static org.junit.jupiter.api.Assertions.*;

import ca.jonathanfritz.ofxcat.TestUtils;
import ca.jonathanfritz.ofxcat.datastore.dto.Account;
import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Transfer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Edge case tests for TransferMatchingService to verify correct handling of ambiguous,
 * invalid, and boundary condition scenarios.
 */
class TransferMatchingServiceEdgeCaseTest {

    private final TransferMatchingService service = new TransferMatchingService();

    @Test
    void transferWithNoMatchingSink() {
        // Setup: XFER transaction with amount=-100, no corresponding +100
        // Expected: Left in transaction queue, not matched
        final LocalDate today = LocalDate.now();
        final Account checking = TestUtils.createRandomAccount("Checking");

        final Transaction unmatchedSource = TestUtils.createRandomTransaction(
                checking, UUID.randomUUID().toString(), today, -100f, Transaction.TransactionType.XFER);

        final Map<Account, List<Transaction>> accountTransactions = new HashMap<>();
        accountTransactions.put(checking, new ArrayList<>(Collections.singletonList(unmatchedSource)));

        // Execute
        final Set<Transfer> transfers = service.match(accountTransactions);

        // Verify: No transfer created, transaction remains in queue
        assertEquals(0, transfers.size());
        assertEquals(1, accountTransactions.get(checking).size());
        assertEquals(unmatchedSource, accountTransactions.get(checking).get(0));
    }

    @Test
    void transferWithMultipleMatchingSinks() {
        // Setup: Source=-100, two sinks=+100 on same day, different accounts
        // Expected: No transfer created (ambiguous match)
        final LocalDate today = LocalDate.now();
        final Account checking = TestUtils.createRandomAccount("Checking");
        final Account savings1 = TestUtils.createRandomAccount("Savings1");
        final Account savings2 = TestUtils.createRandomAccount("Savings2");

        final Transaction source = TestUtils.createRandomTransaction(
                checking, UUID.randomUUID().toString(), today, -100f, Transaction.TransactionType.XFER);
        final Transaction sink1 = TestUtils.createRandomTransaction(
                savings1, UUID.randomUUID().toString(), today, 100f, Transaction.TransactionType.XFER);
        final Transaction sink2 = TestUtils.createRandomTransaction(
                savings2, UUID.randomUUID().toString(), today, 100f, Transaction.TransactionType.XFER);

        final Map<Account, List<Transaction>> accountTransactions = new HashMap<>();
        accountTransactions.put(checking, new ArrayList<>(Collections.singletonList(source)));
        accountTransactions.put(savings1, new ArrayList<>(Collections.singletonList(sink1)));
        accountTransactions.put(savings2, new ArrayList<>(Collections.singletonList(sink2)));

        // Execute
        final Set<Transfer> transfers = service.match(accountTransactions);

        // Verify: No transfer created due to ambiguity
        assertEquals(0, transfers.size());
        assertEquals(1, accountTransactions.get(checking).size());
        assertEquals(1, accountTransactions.get(savings1).size());
        assertEquals(1, accountTransactions.get(savings2).size());
    }

    @Test
    void transferToSameAccount() {
        // Setup: Source and sink both in same account, same date/amount
        // Expected: Not matched (transfers must be between different accounts)
        final LocalDate today = LocalDate.now();
        final Account checking = TestUtils.createRandomAccount("Checking");

        final Transaction source = TestUtils.createRandomTransaction(
                checking, UUID.randomUUID().toString(), today, -100f, Transaction.TransactionType.XFER);
        final Transaction sink = TestUtils.createRandomTransaction(
                checking, UUID.randomUUID().toString(), today, 100f, Transaction.TransactionType.XFER);

        final Map<Account, List<Transaction>> accountTransactions = new HashMap<>();
        accountTransactions.put(checking, new ArrayList<>(Arrays.asList(source, sink)));

        // Execute
        final Set<Transfer> transfers = service.match(accountTransactions);

        // Verify: No transfer created (same account)
        assertEquals(0, transfers.size());
        assertEquals(2, accountTransactions.get(checking).size());
    }

    @Test
    void transferWithZeroAmount() {
        // Setup: XFER transactions with amount=0
        // Expected: Behavior documented (currently will match if amounts are both 0)
        final LocalDate today = LocalDate.now();
        final Account checking = TestUtils.createRandomAccount("Checking");
        final Account savings = TestUtils.createRandomAccount("Savings");

        // Note: Zero amount transactions don't make practical sense, but we document the behavior
        // Both transactions are 0, so technically one is the negation of the other
        final Transaction source = TestUtils.createRandomTransaction(
                checking, UUID.randomUUID().toString(), today, 0f, Transaction.TransactionType.XFER);
        final Transaction sink = TestUtils.createRandomTransaction(
                savings, UUID.randomUUID().toString(), today, 0f, Transaction.TransactionType.XFER);

        final Map<Account, List<Transaction>> accountTransactions = new HashMap<>();
        accountTransactions.put(checking, new ArrayList<>(Collections.singletonList(source)));
        accountTransactions.put(savings, new ArrayList<>(Collections.singletonList(sink)));

        // Execute
        final Set<Transfer> transfers = service.match(accountTransactions);

        // Document actual behavior: Zero amount transfers are not matched
        // because source.amount (0) is not < 0, so it won't be in sourceTransactions
        assertEquals(0, transfers.size());
    }

    @Test
    void transferWithVerySmallAmount() {
        // Setup: Transfer of 1 cent
        // Expected: Correctly matched
        final LocalDate today = LocalDate.now();
        final Account checking = TestUtils.createRandomAccount("Checking");
        final Account savings = TestUtils.createRandomAccount("Savings");

        final Transaction source = TestUtils.createRandomTransaction(
                checking, UUID.randomUUID().toString(), today, -0.01f, Transaction.TransactionType.XFER);
        final Transaction sink = TestUtils.createRandomTransaction(
                savings, UUID.randomUUID().toString(), today, 0.01f, Transaction.TransactionType.XFER);

        final Map<Account, List<Transaction>> accountTransactions = new HashMap<>();
        accountTransactions.put(checking, new ArrayList<>(Collections.singletonList(source)));
        accountTransactions.put(savings, new ArrayList<>(Collections.singletonList(sink)));

        // Execute
        final Set<Transfer> transfers = service.match(accountTransactions);

        // Verify: Transfer correctly matched
        assertEquals(1, transfers.size());
        final Transfer transfer = transfers.iterator().next();
        assertEquals(source.getFitId(), transfer.getSource().getFitId());
        assertEquals(sink.getFitId(), transfer.getSink().getFitId());
        assertTrue(accountTransactions.get(checking).isEmpty());
        assertTrue(accountTransactions.get(savings).isEmpty());
    }

    @Test
    void transferWithVeryLargeAmount() {
        // Setup: Transfer of $999,999,999.99
        // Expected: Correctly matched
        final LocalDate today = LocalDate.now();
        final Account checking = TestUtils.createRandomAccount("Checking");
        final Account savings = TestUtils.createRandomAccount("Savings");

        final float largeAmount = 999999999.99f;
        final Transaction source = TestUtils.createRandomTransaction(
                checking, UUID.randomUUID().toString(), today, -largeAmount, Transaction.TransactionType.XFER);
        final Transaction sink = TestUtils.createRandomTransaction(
                savings, UUID.randomUUID().toString(), today, largeAmount, Transaction.TransactionType.XFER);

        final Map<Account, List<Transaction>> accountTransactions = new HashMap<>();
        accountTransactions.put(checking, new ArrayList<>(Collections.singletonList(source)));
        accountTransactions.put(savings, new ArrayList<>(Collections.singletonList(sink)));

        // Execute
        final Set<Transfer> transfers = service.match(accountTransactions);

        // Verify: Transfer correctly matched
        assertEquals(1, transfers.size());
        final Transfer transfer = transfers.iterator().next();
        assertEquals(source.getFitId(), transfer.getSource().getFitId());
        assertEquals(sink.getFitId(), transfer.getSink().getFitId());
    }

    @Test
    void transferOnDifferentDatesOffByOneDay() {
        // Setup: Source on 2023-01-01, sink on 2023-01-02
        // Expected: Not matched (date matching is exact)
        // This documents a known limitation - transfers that take days to clear won't auto-match
        final Account checking = TestUtils.createRandomAccount("Checking");
        final Account savings = TestUtils.createRandomAccount("Savings");

        final Transaction source = TestUtils.createRandomTransaction(
                checking,
                UUID.randomUUID().toString(),
                LocalDate.of(2023, 1, 1),
                -100f,
                Transaction.TransactionType.XFER);
        final Transaction sink = TestUtils.createRandomTransaction(
                savings,
                UUID.randomUUID().toString(),
                LocalDate.of(2023, 1, 2),
                100f,
                Transaction.TransactionType.XFER);

        final Map<Account, List<Transaction>> accountTransactions = new HashMap<>();
        accountTransactions.put(checking, new ArrayList<>(Collections.singletonList(source)));
        accountTransactions.put(savings, new ArrayList<>(Collections.singletonList(sink)));

        // Execute
        final Set<Transfer> transfers = service.match(accountTransactions);

        // Verify: Not matched due to date difference
        assertEquals(0, transfers.size());
        assertEquals(1, accountTransactions.get(checking).size());
        assertEquals(1, accountTransactions.get(savings).size());
    }

    @Test
    void xferTransactionThatIsNotActuallyATransfer() {
        // Setup: XFER type transaction with no match
        // Expected: Left in queue to be categorized normally
        // Banks may mark transactions as XFER that aren't inter-account transfers
        final LocalDate today = LocalDate.now();
        final Account checking = TestUtils.createRandomAccount("Checking");

        final Transaction xferTypeButNotTransfer = TestUtils.createRandomTransaction(
                checking, UUID.randomUUID().toString(), today, -50f, Transaction.TransactionType.XFER);

        final Map<Account, List<Transaction>> accountTransactions = new HashMap<>();
        accountTransactions.put(checking, new ArrayList<>(Collections.singletonList(xferTypeButNotTransfer)));

        // Execute
        final Set<Transfer> transfers = service.match(accountTransactions);

        // Verify: No transfer created, transaction available for normal categorization
        assertEquals(0, transfers.size());
        assertEquals(1, accountTransactions.get(checking).size());
        assertEquals(xferTypeButNotTransfer, accountTransactions.get(checking).get(0));
    }

    @Test
    void floatRoundingInTransferAmounts() {
        // Setup: Source=-10.10, sink=+10.10 (potential float rounding)
        // This test documents current behavior with float precision
        final LocalDate today = LocalDate.now();
        final Account checking = TestUtils.createRandomAccount("Checking");
        final Account savings = TestUtils.createRandomAccount("Savings");

        // 10.10 is a known problematic float value
        final float amount = 10.10f;
        final Transaction source = TestUtils.createRandomTransaction(
                checking, UUID.randomUUID().toString(), today, -amount, Transaction.TransactionType.XFER);
        final Transaction sink = TestUtils.createRandomTransaction(
                savings, UUID.randomUUID().toString(), today, amount, Transaction.TransactionType.XFER);

        final Map<Account, List<Transaction>> accountTransactions = new HashMap<>();
        accountTransactions.put(checking, new ArrayList<>(Collections.singletonList(source)));
        accountTransactions.put(savings, new ArrayList<>(Collections.singletonList(sink)));

        // Execute
        final Set<Transfer> transfers = service.match(accountTransactions);

        // Document actual behavior: Even with float precision issues, the comparison works
        // because we're comparing the same float values (not computed values)
        assertEquals(1, transfers.size());
        final Transfer transfer = transfers.iterator().next();
        assertEquals(source.getFitId(), transfer.getSource().getFitId());
        assertEquals(sink.getFitId(), transfer.getSink().getFitId());
    }

    @Test
    void negativeAmountSignHandling() {
        // Setup: Verify that source with negative amount matches sink with positive
        // Expected: Correctly matched with negation check
        final LocalDate today = LocalDate.now();
        final Account checking = TestUtils.createRandomAccount("Checking");
        final Account savings = TestUtils.createRandomAccount("Savings");

        final Transaction source = TestUtils.createRandomTransaction(
                checking, UUID.randomUUID().toString(), today, -75.50f, Transaction.TransactionType.XFER);
        final Transaction sink = TestUtils.createRandomTransaction(
                savings, UUID.randomUUID().toString(), today, 75.50f, Transaction.TransactionType.XFER);

        final Map<Account, List<Transaction>> accountTransactions = new HashMap<>();
        accountTransactions.put(checking, new ArrayList<>(Collections.singletonList(source)));
        accountTransactions.put(savings, new ArrayList<>(Collections.singletonList(sink)));

        // Execute
        final Set<Transfer> transfers = service.match(accountTransactions);

        // Verify: Transfer matched with amount * -1 comparison
        assertEquals(1, transfers.size());
        final Transfer transfer = transfers.iterator().next();
        assertEquals(-75.50f, transfer.getSource().getAmount(), 0.001f);
        assertEquals(75.50f, transfer.getSink().getAmount(), 0.001f);
        assertEquals(source.getFitId(), transfer.getSource().getFitId());
        assertEquals(sink.getFitId(), transfer.getSink().getFitId());
    }

    @Test
    void multipleTransfersOnSameDay() {
        // Setup: Multiple valid transfers on the same day between different account pairs
        // Expected: All correctly matched
        final LocalDate today = LocalDate.now();
        final Account checking = TestUtils.createRandomAccount("Checking");
        final Account savings = TestUtils.createRandomAccount("Savings");
        final Account investment = TestUtils.createRandomAccount("Investment");

        // Transfer 1: Checking to Savings
        final Transaction t1Source = TestUtils.createRandomTransaction(
                checking, UUID.randomUUID().toString(), today, -100f, Transaction.TransactionType.XFER);
        final Transaction t1Sink = TestUtils.createRandomTransaction(
                savings, UUID.randomUUID().toString(), today, 100f, Transaction.TransactionType.XFER);

        // Transfer 2: Savings to Investment
        final Transaction t2Source = TestUtils.createRandomTransaction(
                savings, UUID.randomUUID().toString(), today, -200f, Transaction.TransactionType.XFER);
        final Transaction t2Sink = TestUtils.createRandomTransaction(
                investment, UUID.randomUUID().toString(), today, 200f, Transaction.TransactionType.XFER);

        final Map<Account, List<Transaction>> accountTransactions = new HashMap<>();
        accountTransactions.put(checking, new ArrayList<>(Collections.singletonList(t1Source)));
        accountTransactions.put(savings, new ArrayList<>(Arrays.asList(t1Sink, t2Source)));
        accountTransactions.put(investment, new ArrayList<>(Collections.singletonList(t2Sink)));

        // Execute
        final Set<Transfer> transfers = service.match(accountTransactions);

        // Verify: Two transfers created
        assertEquals(2, transfers.size());

        // Verify both transfers are correct
        assertTrue(transfers.stream()
                .anyMatch(t -> t.getSource().getFitId().equals(t1Source.getFitId())
                        && t.getSink().getFitId().equals(t1Sink.getFitId())));
        assertTrue(transfers.stream()
                .anyMatch(t -> t.getSource().getFitId().equals(t2Source.getFitId())
                        && t.getSink().getFitId().equals(t2Sink.getFitId())));

        // All transactions should be removed from the map
        assertTrue(accountTransactions.get(checking).isEmpty());
        assertTrue(accountTransactions.get(savings).isEmpty());
        assertTrue(accountTransactions.get(investment).isEmpty());
    }
}
