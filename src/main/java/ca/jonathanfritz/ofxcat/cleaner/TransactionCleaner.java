package ca.jonathanfritz.ofxcat.cleaner;

import ca.jonathanfritz.ofxcat.io.OfxTransaction;
import ca.jonathanfritz.ofxcat.transactions.Transaction;

public interface TransactionCleaner {

    /**
     * Converts an {@link OfxTransaction} into a {@link Transaction}, tidying up any institution-specific hiccups along the way
     */
    public Transaction clean(OfxTransaction ofxTransaction);
}
