package ca.jonathanfritz.ofxcat.cleaner;

import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import ca.jonathanfritz.ofxcat.io.OfxTransaction;
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
     * Matches the {@link OfxTransaction#getType()} field with a known {@link Transaction.TransactionType}.
     * If there is no field match, {@link Transaction.TransactionType#OTHER} is returned.
     */
    default Transaction.TransactionType categorizeTransactionType(final OfxTransaction ofxTransaction) {
        // if type is undefined, treat it as OTHER
        final String name = ofxTransaction.getType() == null
                ? Transaction.TransactionType.OTHER.name()
                : ofxTransaction.getType().name();

        // map the enum values from one to the other - this works because our internal TransactionType enum is identical
        // to the TransactionType enum from the OFX library
        return Arrays.stream(Transaction.TransactionType.values())
                .filter(type -> type.name().equalsIgnoreCase(name))
                .findFirst()
                .orElse(Transaction.TransactionType.OTHER);
    }
}
