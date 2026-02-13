package ca.jonathanfritz.ofxcat.cleaner;

import ca.jonathanfritz.ofxcat.cleaner.rules.AmountMatcherRule;
import ca.jonathanfritz.ofxcat.cleaner.rules.TransactionMatcherRule;
import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import ca.jonathanfritz.ofxcat.io.OfxTransaction;
import com.webcohesion.ofx4j.domain.data.common.TransactionType;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ca.jonathanfritz.ofxcat.utils.StringUtils.coerceNullableString;

/**
 * A transaction cleaner that tidies up data imported from RBC
 */
public class RbcTransactionCleaner implements TransactionCleaner {

    private static final List<TransactionMatcherRule> rules = new ArrayList<>();

    static final String RBC_BANK_ID = "900000100";
    static final String RBC_INSTITUTION_NAME = "Royal Bank Canada";

    public RbcTransactionCleaner() {
        // inter-account transfer
        final Function<OfxTransaction, Transaction.Builder> interAccountTransferTransformer = ofxTransaction -> {
            final String description;
            if (ofxTransaction.getAmount() < 0) {
                // debit
                description = "TRANSFER OUT OF ACCOUNT";
            } else {
                // credit
                description = "TRANSFER INTO ACCOUNT";
            }
            return Transaction.newBuilder(ofxTransaction.getFitId())
                    .setType(Transaction.TransactionType.XFER)
                    .setDate(ofxTransaction.getDate())
                    .setAmount(ofxTransaction.getAmount())
                    .setDescription(description);
        };
        rules.add(TransactionMatcherRule.newBuilder()
                .withName(Pattern.compile("^WWW TRF DDA - \\d+.*$", Pattern.CASE_INSENSITIVE))
                .build(interAccountTransferTransformer));
        rules.add(TransactionMatcherRule.newBuilder()
                .withMemo(Pattern.compile("^WWW TRANSFER - \\d+.*$", Pattern.CASE_INSENSITIVE))
                .build(interAccountTransferTransformer));
        rules.add(TransactionMatcherRule.newBuilder()
                .withName(Pattern.compile("^WWW TFR TIN0.*$", Pattern.CASE_INSENSITIVE))
                .build(interAccountTransferTransformer));

        // scheduled transfer from one account to a line of credit
        rules.add(TransactionMatcherRule.newBuilder()
                .withType(TransactionType.DEBIT)
                .withAmount(AmountMatcherRule.isLessThan(0))
                .withMemo(Pattern.compile("^WWW LOAN PMT - \\d+.*$", Pattern.CASE_INSENSITIVE))
                .build(ofxTransaction -> Transaction.newBuilder(ofxTransaction.getFitId())
                        .setDate(ofxTransaction.getDate())
                        .setAmount(ofxTransaction.getAmount())
                        .setType(Transaction.TransactionType.XFER)
                        .setDescription("LINE OF CREDIT PAYMENT")));

        // scheduled transfer to a line of credit from another account
        rules.add(TransactionMatcherRule.newBuilder()
                .withType(TransactionType.CREDIT)
                .withAmount(AmountMatcherRule.isGreaterThan(0))
                .withName(Pattern.compile("^WWW PMT TIN0.*", Pattern.CASE_INSENSITIVE))
                .build(ofxTransaction -> Transaction.newBuilder(ofxTransaction.getFitId())
                        .setDate(ofxTransaction.getDate())
                        .setAmount(ofxTransaction.getAmount())
                        .setType(Transaction.TransactionType.XFER)
                        .setDescription("LINE OF CREDIT PAYMENT")));

        // credit card payment
        rules.add(TransactionMatcherRule.newBuilder()
                .withType(TransactionType.CREDIT)
                .withAmount(AmountMatcherRule.isGreaterThan(0))
                .withName(Pattern.compile("^PAYMENT - THANK YOU.*", Pattern.CASE_INSENSITIVE))
                .build(ofxTransaction -> Transaction.newBuilder(ofxTransaction.getFitId())
                        .setDate(ofxTransaction.getDate())
                        .setAmount(ofxTransaction.getAmount())
                        .setType(Transaction.TransactionType.XFER)
                        .setDescription("CREDIT CARD PAYMENT")));

        // online bill payment - strip the memo field prefix
        rules.add(TransactionMatcherRule.newBuilder()
                .withType(TransactionType.DEBIT)
                .withAmount(AmountMatcherRule.isLessThan(0))
                .withMemo(Pattern.compile("^WWW PAYMENT - \\d+.*$", Pattern.CASE_INSENSITIVE))
                .build(ofxTransaction -> Transaction.newBuilder(ofxTransaction.getFitId())
                        .setType(Transaction.TransactionType.DEBIT)
                        .setDate(ofxTransaction.getDate())
                        .setAmount(ofxTransaction.getAmount())
                        .setDescription(ofxTransaction.getMemo().substring(19))));

        // wire transfer
        rules.add(TransactionMatcherRule.newBuilder()
                .withName(Pattern.compile("^FUNDS TRANSFER CR", Pattern.CASE_INSENSITIVE))
                .build(ofxTransaction -> Transaction.newBuilder(ofxTransaction.getFitId())
                        .setType(categorizeTransactionType(ofxTransaction))
                        .setDate(ofxTransaction.getDate())
                        .setAmount(ofxTransaction.getAmount())
                        .setDescription("WIRE TRANSFER")));

        // incoming Interac e-transfer with auto-deposit
        rules.add(TransactionMatcherRule.newBuilder()
                .withType(TransactionType.CREDIT)
                .withAmount(AmountMatcherRule.isGreaterThan(0))
                .withName(Pattern.compile("^E-TRF AUTODEPOSIT$", Pattern.CASE_INSENSITIVE))
                .build(ofxTransaction -> Transaction.newBuilder(ofxTransaction.getFitId())
                        .setType(Transaction.TransactionType.CREDIT)
                        .setDate(ofxTransaction.getDate())
                        .setAmount(ofxTransaction.getAmount())
                        .setDescription("INCOMING INTERAC E-TRANSFER AUTO-DEPOSIT")));

        // incoming Interac e-transfer
        rules.add(TransactionMatcherRule.newBuilder()
                .withType(TransactionType.CREDIT)
                .withAmount(AmountMatcherRule.isGreaterThan(0))
                .withName(Pattern.compile("^Email Trfs Can.*$", Pattern.CASE_INSENSITIVE))
                .withMemo(Pattern.compile("^INT E-TRF CAN.*$", Pattern.CASE_INSENSITIVE))
                .build(ofxTransaction -> Transaction.newBuilder(ofxTransaction.getFitId())
                        .setType(Transaction.TransactionType.CREDIT)
                        .setDate(ofxTransaction.getDate())
                        .setAmount(ofxTransaction.getAmount())
                        .setDescription("INCOMING INTERAC E-TRANSFER")));
        rules.add(TransactionMatcherRule.newBuilder()
                .withType(TransactionType.CREDIT)
                .withAmount(AmountMatcherRule.isGreaterThan(0))
                .withName(Pattern.compile("^Email Trfs.*$", Pattern.CASE_INSENSITIVE))
                .withMemo(Pattern.compile("^INTERAC E-TRF-.*$", Pattern.CASE_INSENSITIVE))
                .build(ofxTransaction -> Transaction.newBuilder(ofxTransaction.getFitId())
                        .setType(Transaction.TransactionType.CREDIT)
                        .setDate(ofxTransaction.getDate())
                        .setAmount(ofxTransaction.getAmount())
                        .setDescription("INCOMING INTERAC E-TRANSFER")));

        // outgoing Interac e-transfer
        rules.add(TransactionMatcherRule.newBuilder()
                .withType(TransactionType.DEBIT)
                .withAmount(AmountMatcherRule.isLessThan(0))
                .withMemo(Pattern.compile("^INTERAC E-TRF-\\s\\d*$", Pattern.CASE_INSENSITIVE))
                .build(ofxTransaction -> Transaction.newBuilder(ofxTransaction.getFitId())
                        .setType(Transaction.TransactionType.DEBIT)
                        .setDate(ofxTransaction.getDate())
                        .setAmount(ofxTransaction.getAmount())
                        .setDescription("OUTGOING INTERAC E-TRANSFER")));
        rules.add(TransactionMatcherRule.newBuilder()
                .withType(TransactionType.DEBIT)
                .withAmount(AmountMatcherRule.isLessThan(0))
                .withMemo(Pattern.compile("^E-TRANSFER SENT", Pattern.CASE_INSENSITIVE))
                .build(ofxTransaction -> Transaction.newBuilder(ofxTransaction.getFitId())
                        .setType(Transaction.TransactionType.DEBIT)
                        .setDate(ofxTransaction.getDate())
                        .setAmount(ofxTransaction.getAmount())
                        .setDescription("OUTGOING INTERAC E-TRANSFER")));

        // sending money via Interac E-Transfer can incur service charges
        rules.add(TransactionMatcherRule.newBuilder()
                .withType(TransactionType.DEBIT)
                .withAmount(AmountMatcherRule.isLessThan(0))
                .withName(Pattern.compile("^INTERAC-SC-\\d+$", Pattern.CASE_INSENSITIVE))
                .build(ofxTransaction -> Transaction.newBuilder(ofxTransaction.getFitId())
                        .setType(Transaction.TransactionType.FEE)
                        .setDate(ofxTransaction.getDate())
                        .setAmount(ofxTransaction.getAmount())
                        .setDescription("INTERAC E-TRANSFER SERVICE CHARGE")));
        rules.add(TransactionMatcherRule.newBuilder()
                .withType(TransactionType.DEBIT)
                .withAmount(AmountMatcherRule.isLessThan(0))
                .withName(Pattern.compile("^INT E-TRF FEE\\s*$", Pattern.CASE_INSENSITIVE))
                .build(ofxTransaction -> Transaction.newBuilder(ofxTransaction.getFitId())
                        .setType(Transaction.TransactionType.FEE)
                        .setDate(ofxTransaction.getDate())
                        .setAmount(ofxTransaction.getAmount())
                        .setDescription("INTERAC E-TRANSFER SERVICE CHARGE")));

        // sometimes an e-transfer can be cancelled and refunded
        rules.add(TransactionMatcherRule.newBuilder()
                .withType(TransactionType.CREDIT)
                .withAmount(AmountMatcherRule.isGreaterThan(0))
                .withMemo(Pattern.compile("^E-TRANSFER CANCEL"))
                .build(ofxTransaction -> Transaction.newBuilder(ofxTransaction.getFitId())
                        .setType(Transaction.TransactionType.CREDIT)
                        .setDate(ofxTransaction.getDate())
                        .setAmount(ofxTransaction.getAmount())
                        .setDescription("CANCELLED INTERAC E-TRANSFER")));

        // personal loan repayment (car loan, small business loan, etc)
        rules.add(TransactionMatcherRule.newBuilder()
                .withType(TransactionType.DEBIT)
                .withAmount(AmountMatcherRule.isLessThan(0))
                .withName(Pattern.compile("^PERSONAL LOAN$", Pattern.CASE_INSENSITIVE))
                .build(ofxTransaction -> Transaction.newBuilder(ofxTransaction.getFitId())
                        .setType(Transaction.TransactionType.DEBIT)
                        .setDate(ofxTransaction.getDate())
                        .setAmount(ofxTransaction.getAmount())
                        .setDescription("PERSONAL LOAN REPAYMENT")));

        // purchases made in USD have MEMO like "5.00 USD @ 1.308000000000" to indicate currency conversion
        // these tend to confuse the auto-categorization algorithm, so discard them
        rules.add(TransactionMatcherRule.newBuilder()
                .withType(TransactionType.DEBIT)
                .withAmount(AmountMatcherRule.isLessThan(0))
                .withMemo(Pattern.compile("^\\d*\\.\\d*\\sUSD*\\s@\\s\\d*.\\d*$", Pattern.CASE_INSENSITIVE))
                .build(ofxTransaction -> Transaction.newBuilder(ofxTransaction.getFitId())
                        .setType(categorizeTransactionType(ofxTransaction))
                        .setDate(ofxTransaction.getDate())
                        .setAmount(ofxTransaction.getAmount())
                        .setDescription(coerceNullableString(ofxTransaction.getName()) + " (USD PURCHASE)")));

        // Interac purchase - these appear with both the DEBIT and POS transaction types
        // strip the useless MEMO field because it confuses the transaction matcher
        rules.add(TransactionMatcherRule.newBuilder()
                .withAmount(AmountMatcherRule.isLessThan(0))
                .withMemo(Pattern.compile("^IDP PURCHASE\\s*-\\s*\\d+.*$", Pattern.CASE_INSENSITIVE))
                .build(ofxTransaction -> Transaction.newBuilder(ofxTransaction.getFitId())
                        .setType(Transaction.TransactionType.DEBIT)
                        .setDate(ofxTransaction.getDate())
                        .setAmount(ofxTransaction.getAmount())
                        .setDescription(ofxTransaction.getName())));

        // another variation of interac purchase
        rules.add(TransactionMatcherRule.newBuilder()
                .withType(TransactionType.DEBIT)
                .withAmount(AmountMatcherRule.isLessThan(0))
                .withName(Pattern.compile("^WWWINTERAC PUR.*$", Pattern.CASE_INSENSITIVE))
                .build(ofxTransaction -> Transaction.newBuilder(ofxTransaction.getFitId())
                        .setType(Transaction.TransactionType.DEBIT)
                        .setDate(ofxTransaction.getDate())
                        .setAmount(ofxTransaction.getAmount())
                        .setDescription(ofxTransaction.getMemo())));

        // contactless Interac purchase
        rules.add(TransactionMatcherRule.newBuilder()
                .withType(TransactionType.DEBIT)
                .withAmount(AmountMatcherRule.isLessThan(0))
                .withName(Pattern.compile("^C-IDP PURCHASE\\s*-\\s*\\d+.*$", Pattern.CASE_INSENSITIVE))
                .build(ofxTransaction -> Transaction.newBuilder(ofxTransaction.getFitId())
                        .setType(categorizeTransactionType(ofxTransaction))
                        .setDate(ofxTransaction.getDate())
                        .setAmount(ofxTransaction.getAmount())
                        .setDescription(ofxTransaction.getMemo())));

        // ATM withdrawal
        rules.add(TransactionMatcherRule.newBuilder()
                .withType(TransactionType.ATM)
                .withAmount(AmountMatcherRule.isLessThan(0))
                .withMemo(Pattern.compile("^PTB CB WD-.*$", Pattern.CASE_INSENSITIVE))
                .build(ofxTransaction -> Transaction.newBuilder(ofxTransaction.getFitId())
                        .setType(Transaction.TransactionType.DEBIT)
                        .setDate(ofxTransaction.getDate())
                        .setAmount(ofxTransaction.getAmount())
                        .setDescription("ATM WITHDRAWAL")));
        rules.add(TransactionMatcherRule.newBuilder()
                .withType(TransactionType.ATM)
                .withAmount(AmountMatcherRule.isLessThan(0))
                .withMemo(Pattern.compile("^PTB WD ---.*$", Pattern.CASE_INSENSITIVE))
                .build(ofxTransaction -> Transaction.newBuilder(ofxTransaction.getFitId())
                        .setType(Transaction.TransactionType.DEBIT)
                        .setDate(ofxTransaction.getDate())
                        .setAmount(ofxTransaction.getAmount())
                        .setDescription("ATM WITHDRAWAL")));

        // ATM deposit
        rules.add(TransactionMatcherRule.newBuilder()
                .withType(TransactionType.ATM)
                .withAmount(AmountMatcherRule.isGreaterThan(0))
                .withMemo(Pattern.compile("^PTB DEP --.*$", Pattern.CASE_INSENSITIVE))
                .build(ofxTransaction -> Transaction.newBuilder(ofxTransaction.getFitId())
                        .setType(Transaction.TransactionType.CREDIT)
                        .setDate(ofxTransaction.getDate())
                        .setAmount(ofxTransaction.getAmount())
                        .setDescription("ATM DEPOSIT")));

        // some kind of miscellaneous payment - discard the useless name field
        rules.add(TransactionMatcherRule.newBuilder()
                .withName(Pattern.compile("^MISC PAYMENT$", Pattern.CASE_INSENSITIVE))
                .build(ofxTransaction -> Transaction.newBuilder(ofxTransaction.getFitId())
                        .setType(categorizeTransactionType(ofxTransaction))
                        .setDate(ofxTransaction.getDate())
                        .setAmount(ofxTransaction.getAmount())
                        .setDescription(ofxTransaction.getMemo())));
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
        final Optional<TransactionMatcherRule> transformer = rules.stream().filter(r -> r.matches(ofxTransaction)).findFirst();
        if (transformer.isPresent()) {
            return transformer.get().apply(ofxTransaction);
        }

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
        return input.toUpperCase().trim();
    }
}
