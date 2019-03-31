package ca.jonathanfritz.ofxcat.cleaner;

import ca.jonathanfritz.ofxcat.io.OfxTransaction;
import ca.jonathanfritz.ofxcat.transactions.Transaction;

import java.util.Arrays;

/**
 * The default transaction cleaner that is used if the source institution is unrecognized
 */
public class DefaultTransactionCleaner implements TransactionCleaner {

    @Override
    public Transaction clean(OfxTransaction ofxTransaction) {
        final Transaction.TransactionType type = Arrays.stream(Transaction.TransactionType.values())
                .filter(transactionType -> transactionType.toString().equalsIgnoreCase(ofxTransaction.getType()))
                .findFirst()
                .orElse(Transaction.TransactionType.UNKNOWN);

        final String description = ofxTransaction.getName().toUpperCase().trim() + " " + ofxTransaction.getMemo().toUpperCase().trim();

        return new Transaction(type, ofxTransaction.getDate(), ofxTransaction.getAmount(), description);
    }
}
