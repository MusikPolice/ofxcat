package ca.jonathanfritz.ofxcat.cleaner;

import ca.jonathanfritz.ofxcat.io.OfxTransaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
            Pattern.compile("^MISC PAYMENT$"),

            // Personal loan repayment (car loan, small business loan, etc)
            Pattern.compile("^SPL$")
    );

    private static final Map<Pattern, String> patternsToReplace = new HashMap<>();

    static final String RBC_BANK_ID = "900000100";
    static final String RBC_INSTITUTION_NAME = "Royal Bank Canada";

    public RbcTransactionCleaner() {
        // online transfer between accounts - groups with WWW TRANSFER
        patternsToReplace.put(Pattern.compile("^WWW TRF DDA - \\d+.*$"), "TRANSFER");

        // Interac e-transfer with autodeposit
        patternsToReplace.put(Pattern.compile("^E-TRF AUTODEPOSIT$"), "INTERAC E-TRANSFER AUTO-DEPOSIT");

        // Interac e-transfer outgoing
        patternsToReplace.put(Pattern.compile("^EMAIL TRFS$"), "INTERAC E-TRANSFER");

        // Personal loan repayment (car loan, small business loan, etc)
        patternsToReplace.put(Pattern.compile("^PERSONAL LOAN$"), "PERSONAL LOAN REPAYMENT");
    }

    @Override
    public String getBankId() {
        return RBC_BANK_ID;
    }

    @Override
    public String getInstitutionName() {
        return RBC_INSTITUTION_NAME;
    }

    @Override
    public Transaction.Builder clean(OfxTransaction ofxTransaction) {
        final String name = clean(ofxTransaction.getName());
        final String memo = clean(ofxTransaction.getMemo());
        final String description = Stream.of(name, memo)
                .filter(StringUtils::isNotBlank)
                .map(s -> s.trim().toUpperCase())
                .collect(Collectors.joining(" "));

        return Transaction.newBuilder(ofxTransaction.getFitId())
            .setType(categorizeTransactionType(ofxTransaction))
            .setDate(ofxTransaction.getDate())
            .setAmount(ofxTransaction.getAmount())
            .setDescription(description);
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
