package ca.jonathanfritz.ofxcat.cleaner;

import ca.jonathanfritz.ofxcat.io.OfxTransaction;
import ca.jonathanfritz.ofxcat.transactions.Transaction;

/**
 * The default transaction cleaner that is used if the source institution is unrecognized
 */
public class DefaultTransactionCleaner implements TransactionCleaner {

    public static final String DEFAULT_BANK_ID = "default";

    @Override
    public String getBankId() {
        return DEFAULT_BANK_ID;
    }

    @Override
    public String getInstitutionName() {
        return "default";
    }

    @Override
    public Transaction.Builder clean(OfxTransaction ofxTransaction) {
        final String description = String.format("%s %s", ofxTransaction.getName().toUpperCase().trim(), ofxTransaction.getMemo().toUpperCase().trim());

        return Transaction.newBuilder()
            .setType(categorizeTransactionType(ofxTransaction))
            .setDate(ofxTransaction.getDate())
            .setAmount(ofxTransaction.getAmount())
            .setDescription(description);
    }
}
