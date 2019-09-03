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

    // TODO: don't discard account numbers - we can match them to other accounts to improve UI
    private static final List<Pattern> patternsToDiscard = Arrays.asList(
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

    private static final Map<Pattern, String> patternsToReplace = new HashMap<>();

    static final String RBC_BANK_ID = "900000100";

    public RbcTransactionCleaner() {
        // online transfer between accounts - groups with WWW TRANSFER
        patternsToReplace.put(Pattern.compile("^WWW TRF DDA - \\d+.*$"), "TRANSFER");

        // Interac e-transfer with autodeposit
        patternsToReplace.put(Pattern.compile("^E-TRF AUTODEPOSIT$"), "INTERAC E-TRANSFER AUTO-DEPOSIT");

        // Interac e-transfer outgoing
        patternsToReplace.put(Pattern.compile("^EMAIL TRFS$"), "INTERAC E-TRANSFER");
    }

    @Override
    public String getBankId() {
        return RBC_BANK_ID;
    }

    @Override
    public String getInstitutionName() {
        return "Royal Bank Canada";
    }

    @Override
    public Transaction.Builder clean(OfxTransaction ofxTransaction) {
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

        return Transaction.newBuilder()
            .setType(categorizeTransactionType(ofxTransaction))
            .setDate(ofxTransaction.getDate())
            .setAmount(ofxTransaction.getAmount())
            .setDescription(description.toString());
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
