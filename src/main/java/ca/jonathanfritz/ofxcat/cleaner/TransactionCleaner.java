package ca.jonathanfritz.ofxcat.cleaner;

import ca.jonathanfritz.ofxcat.io.OfxTransaction;
import ca.jonathanfritz.ofxcat.transactions.Transaction;

/**
 * The transaction data that comes in ofx files tends to be messy, and every institution has its own quirks. A {@link TransactionCleaner} attempts
 * to tidy up that data to make it easier to work with. Use {@link TransactionCleanerFactory#findByBankId(String)} to find an implementation that
 * is appropriate for your banking institution.
 *
 * @see TransactionCleanerFactory
 */
public interface TransactionCleaner {

    /**
     * Returns the unique identifier of the banking institution that this {@link TransactionCleaner} is designed to work with.
     * Corresponds to the BANKID attribute in an OFX document
     */
    public String getBankId();

    /**
     * The displayable name of the institution that this cleaner is designed for
     */
    public String getInstitutionName();

    /**
     * Converts an {@link OfxTransaction} into a {@link Transaction}, tidying up any institution-specific hiccups along the way
     */
    public Transaction clean(OfxTransaction ofxTransaction);
}
