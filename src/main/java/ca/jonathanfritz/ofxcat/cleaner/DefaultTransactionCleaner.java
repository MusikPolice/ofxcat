package ca.jonathanfritz.ofxcat.cleaner;

import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import ca.jonathanfritz.ofxcat.io.OfxTransaction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;

/**
 * The default transaction cleaner that is used if the source institution is unrecognized
 */
public class DefaultTransactionCleaner implements TransactionCleaner {

    static final String DEFAULT_BANK_ID = "default";
    static final String DEFAULT_INSTITUTION_NAME = "default";

    @Override
    public String getBankId() {
        return DEFAULT_BANK_ID;
    }

    @Override
    public String getInstitutionName() {
        return DEFAULT_INSTITUTION_NAME;
    }

    @Override
    public Transaction.Builder clean(OfxTransaction ofxTransaction) {
        final String description = Stream.of(ofxTransaction.getName(), ofxTransaction.getMemo())
                .filter(StringUtils::isNotBlank)
                .map(s -> s.trim().toUpperCase())
                .collect(Collectors.joining(" "));

        return Transaction.newBuilder(ofxTransaction.getFitId())
                .setType(categorizeTransactionType(ofxTransaction))
                .setDate(ofxTransaction.getDate())
                .setAmount(ofxTransaction.getAmount())
                .setDescription(description);
    }
}
