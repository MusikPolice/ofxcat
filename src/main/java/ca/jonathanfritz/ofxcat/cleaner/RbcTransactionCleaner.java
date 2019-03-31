package ca.jonathanfritz.ofxcat.cleaner;

import ca.jonathanfritz.ofxcat.io.OfxTransaction;
import ca.jonathanfritz.ofxcat.transactions.Transaction;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A transaction cleaner that tidies up data imported from RBC
 */
public class RbcTransactionCleaner implements TransactionCleaner {

    private final static List<Pattern> patternsToDiscard = Arrays.asList(
            // Interac purchase
            Pattern.compile("^IDP PURCHASE\\s*-\\s*\\d+.*$"),

            // contactless Interac purchase
            Pattern.compile("^C-IDP PURCHASE\\s*-\\s*\\d+.*$"),

            // Interact e-transfer
            Pattern.compile("^INTERAC E-TRF\\s*-\\s*\\d+.*$"),

            // ATM withdrawal
            Pattern.compile("^PTB CB WD-.*$"),

            // ATM Deposit
            Pattern.compile("^PTB DEP --.*$"),

            // ATM Withdrawal
            Pattern.compile("^PTB WD ---.*$"),

            // scheduled transfer to line of credit
            Pattern.compile("^WWW LOAN PMT - \\d+.*$"),

            // online bill payment
            Pattern.compile("^WWW PAYMENT - \\d+.*$"),

            // online transfer between accounts
            Pattern.compile("^WWW TRANSFER - \\d+.*$"),

            // paypal
            Pattern.compile("^MISC PAYMENT$")
    );

    private final static Map<Pattern, String> patternsToReplace = new HashMap<>();

    public RbcTransactionCleaner() {
        // online transfer between accounts - groups with WWW TRANSFER
        patternsToReplace.put(Pattern.compile("^WWW TRF DDA - \\d+.*$"), "TRANSFER");

        // Interac e-transfer with autodeposit
        patternsToReplace.put(Pattern.compile("^E-TRF AUTODEPOSIT$"), "INTERAC E-TRANSFER AUTO-DEPOSIT");

        // Interac e-transfer outgoing
        patternsToReplace.put(Pattern.compile("^EMAIL TRFS$"), "INTERAC E-TRANSFER");
    }

    @Override
    public Transaction clean(OfxTransaction ofxTransaction) {
        final Transaction.TransactionType type = Arrays.stream(Transaction.TransactionType.values())
                .filter(transactionType -> transactionType.toString().equalsIgnoreCase(ofxTransaction.getType()))
                .findFirst()
                .orElse(Transaction.TransactionType.UNKNOWN);

        final String name = clean(ofxTransaction.getName());
        final String memo = clean(ofxTransaction.getMemo());

        final StringBuilder description = new StringBuilder();
        description.append(StringUtils.isNotBlank(name) ? name : "");
        if (StringUtils.isNotBlank(memo)) {
            if (description.length() > 0) {
                description.append(" ");
            }
            description.append(memo);
        }

        return new Transaction(type, ofxTransaction.getDate(), ofxTransaction.getAmount(), description.toString());
    }

    private String clean(final String input) {
        if (StringUtils.isBlank(input)) {
            return null;
        }
        String transformed = input.toUpperCase().trim();
        if (patternsToDiscard.stream().anyMatch(pattern -> pattern.matcher(transformed).matches())) {
            return null;
        }

        return patternsToReplace.entrySet()
                .stream()
                .filter(patternStringEntry -> patternStringEntry.getKey().matcher(transformed).matches())
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(transformed);
    }
}
