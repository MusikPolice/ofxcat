package ca.jonathanfritz.ofxcat.service;

import ca.jonathanfritz.ofxcat.datastore.dto.Account;
import ca.jonathanfritz.ofxcat.datastore.dto.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Transfer;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        for (Transaction source : sourceTransactions) {
            final List<Transaction> potentialSinks = sinkTransactions.stream()
                    .filter(t -> t.getDate().equals(source.getDate()))
                    .filter(t -> t.getAmount() == source.getAmount() * -1)
                    .filter(t -> !t.getAccount().equals(source.getAccount()))
                    .toList();

            // if we found exactly one candidate, create a Transfer that represents the movement of funds from one
            // account to the other
            if (potentialSinks.size() == 1) {
                final Transaction sink = potentialSinks.get(0);
                transfers.add(new Transfer(
                    new CategorizedTransaction(source, Category.TRANSFER),
                    new CategorizedTransaction(sink, Category.TRANSFER)
                ));
            }
        }

        // finally, we can remove all matched transactions from the incoming map of account transactions
        final Set<CategorizedTransaction> matchedTransactions = transfers.stream()
                .flatMap(t -> Stream.of(t.getSink(), t.getSource()))
                .collect(Collectors.toSet());
        for (Account account : accountTransactions.keySet()) {
            final List<Transaction> filtered = accountTransactions.get(account).stream()
                    .filter(t -> matchedTransactions.stream().noneMatch(ct -> ct.getFitId().equals(t.getFitId())))
                    .toList();
            accountTransactions.put(account, filtered);
        }

        return transfers;
    }
}
