package ca.jonathanfritz.ofxcat.cleaner;

import ca.jonathanfritz.ofxcat.io.OfxTransaction;
import ca.jonathanfritz.ofxcat.transactions.Transaction;

import java.util.Arrays;

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
    String getBankId();

    /**
     * The displayable name of the institution that this cleaner is designed for
     */
    String getInstitutionName();

    /**
     * Converts an {@link OfxTransaction} into a {@link Transaction.Builder}, tidying up any institution-specific hiccups along the way
     */
    Transaction.Builder clean(OfxTransaction ofxTransaction);

    /**
     * Matches the {@link OfxTransaction#getType()} field with a known {@link ca.jonathanfritz.ofxcat.transactions.Transaction.TransactionType}.
     * If there is no field match, {@link ca.jonathanfritz.ofxcat.transactions.Transaction.TransactionType#UNKNOWN} is returned.
     */
    default Transaction.TransactionType categorizeTransactionType(final OfxTransaction ofxTransaction) {
        return Arrays.stream(Transaction.TransactionType.values())
                .filter(transactionType -> transactionType.toString().equalsIgnoreCase(ofxTransaction.getType()))
                .findFirst()
                .orElse(Transaction.TransactionType.UNKNOWN);
    }
}
