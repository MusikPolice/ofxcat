package ca.jonathanfritz.ofxcat.cleaner;

import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import ca.jonathanfritz.ofxcat.io.OfxTransaction;
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

            // discards memo field of Interac e-transfer
            Pattern.compile("^INTERAC E-TRF\\s*-\\s*\\d+.*$"),

            // ATM withdrawal
            Pattern.compile("^PTB CB WD-.*$"),

            // ATM Deposit
            Pattern.compile("^PTB DEP --.*$"),

            // discards MEMO field of ATM Withdrawal
            // NAME field contains string WITHDRAWAL
            Pattern.compile("^PTB WD ---.*$"),

            // scheduled transfer to line of credit
            Pattern.compile("^WWW LOAN PMT - \\d+.*$"),

            // online bill payment
            Pattern.compile("^WWW PAYMENT - \\d+.*$"),

            // paypal
            Pattern.compile("^MISC PAYMENT$"),

            // discards memo field of Personal loan repayment (car loan, small business loan, etc)
            Pattern.compile("^SPL$")
    );

    private static final Map<Pattern, String> patternsToReplace = new HashMap<>();

    static final String RBC_BANK_ID = "900000100";
    static final String RBC_INSTITUTION_NAME = "Royal Bank Canada";

    public RbcTransactionCleaner() {
        // replaces NAME field of outgoing transfer from one account to another
        // these transactions do not have a MEMO field
        patternsToReplace.put(Pattern.compile("^WWW TRF DDA - \\d+.*$"), "TRANSFER TO ACCOUNT");

        // replaces MEMO field of incoming transfer from one account to another
        // the NAME field already contains the string "TRANSFER", so the description will be "TRANSFER FROM ACCOUNT"
        patternsToReplace.put(Pattern.compile("^WWW TRANSFER - \\d+.*$"), "FROM ACCOUNT");

        // replaces NAME field of Interac e-transfer autodeposit with human-readable string
        patternsToReplace.put(Pattern.compile("^E-TRF AUTODEPOSIT$"), "INTERAC E-TRANSFER AUTO-DEPOSIT");

        // replaces MEMO field of Interac e-transfer outgoing with human-readable string
        patternsToReplace.put(Pattern.compile("^EMAIL TRFS$"), "INTERAC E-TRANSFER");

        // replaces NAME field of Personal loan repayment (car loan, small business loan, etc) with human-readable string
        patternsToReplace.put(Pattern.compile("^PERSONAL LOAN$"), "PERSONAL LOAN REPAYMENT");

        // Purchases made in USD have MEMO like "5.00 USD @ 1.308000000000" to indicate currency conversion
        // these tend to confuse the auto-categorization algorithm, so discard them
        patternsToReplace.put(Pattern.compile("^\\d*\\.\\d*\\sUSD*\\s@\\s\\d*.\\d*$"), "(USD PURCHASE)");
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
