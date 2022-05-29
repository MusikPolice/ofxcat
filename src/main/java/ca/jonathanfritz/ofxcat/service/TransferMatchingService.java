package ca.jonathanfritz.ofxcat.service;

import ca.jonathanfritz.ofxcat.datastore.dto.Account;
import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Transfer;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TransferMatchingService {

    /**
     * Extracts inter-account transfers of type XFER out of the specified map of account transactions.
     * To be considered a valid transaction pair, both transactions must be of type XFER, must have occurred on the same
     * date, must affect different accounts, and one must have a value equal to the negation of the value of the other.
     * @param accountTransactions the map of account transactions. Modified byref to remove Transactions that are
     *                            identified as transfers.
     * @return a set of matched {@link Transfer}s
     */
    public Set<Transfer> match(Map<Account, List<Transaction>> accountTransactions) {
        // all of our transactions have been cleaned up and enriched with account and balance information
        // we can attempt to identify inter-account transfers by looking for the XFER type
        final Set<Transaction> sourceTransactions = new HashSet<>();
        final Set<Transaction> sinkTransactions = new HashSet<>();
        for (Map.Entry<Account, List<Transaction>> entry: accountTransactions.entrySet()) {
            sourceTransactions.addAll(entry.getValue().stream()
                    .filter(t -> t.getType() == Transaction.TransactionType.XFER)
                    .filter(t -> t.getAmount() < 0)
                    .toList());
            sinkTransactions.addAll(entry.getValue().stream()
                    .filter(t -> t.getType() == Transaction.TransactionType.XFER)
                    .filter(t -> t.getAmount() > 0)
                    .toList());
        }

        // next we'll attempt to match each source transaction with a corresponding sink transaction that took place on
        // the same day, is for the same amount (negated), and belongs to a different account
        final Set<Transfer> transfers = new HashSet<>();
        while (sourceTransactions.iterator().hasNext()) {
            final Transaction source = sourceTransactions.iterator().next();
            final List<Transaction> potentialSinks = sinkTransactions.stream()
                    .filter(t -> t.getDate().equals(source.getDate()))
                    .filter(t -> t.getAmount() == source.getAmount() * -1)
                    .filter(t -> !t.getAccount().equals(source.getAccount()))
                    .toList();

            // if we found exactly one candidate, create a Transfer that represents the movement of funds from one
            // account to the other
            if (potentialSinks.size() == 1) {
                final Transaction sink = potentialSinks.get(0);
                sourceTransactions.remove(source);
                sinkTransactions.remove(sink);
                transfers.add(new Transfer(source, sink));
            }
        }

        // finally, we can remove all matched transactions from the incoming map of account transactions
        final Set<Transaction> matchedTransactions = transfers.stream()
                .flatMap(t -> Stream.of(t.sink(), t.source()))
                .collect(Collectors.toSet());
        for (Account account : accountTransactions.keySet()) {
            final List<Transaction> filtered = accountTransactions.get(account).stream()
                    .filter(t -> !matchedTransactions.contains(t))
                    .toList();
            accountTransactions.put(account, filtered);
        }

        return transfers;
    }
}
