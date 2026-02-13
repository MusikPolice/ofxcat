package ca.jonathanfritz.ofxcat.io;

import com.webcohesion.ofx4j.domain.data.common.TransactionType;
import com.webcohesion.ofx4j.io.OFXHandler;
import com.webcohesion.ofx4j.io.OFXParseException;
import com.webcohesion.ofx4j.io.OFXReader;
import com.webcohesion.ofx4j.io.OFXSyntaxException;
import com.webcohesion.ofx4j.io.nanoxml.NanoXMLOFXReader;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OfxParser {

    // bank account information elements
    private static final String BANKACCTFROM = "BANKACCTFROM";  // start/end of account info element
    private static final String BANKID = "BANKID";              // unique id of the institution
    private static final String ACCTID = "ACCTID";              // bank account number that the transactions belong to
    private static final String ACCTTYPE = "ACCTTYPE";          // type of bank account

    // credit card information elements
    private static final String CCACCTFROM = "CCACCTFROM";      // start/end of credit card info element

    // transaction elements
    private static final String STMTTRN = "STMTTRN";    // start/end of transaction element
    private static final String TRNTYPE = "TRNTYPE";    // the type of transaction
    private static final String DTPOSTED = "DTPOSTED";  // date on which the transaction occurred
    private static final String TRNAMT = "TRNAMT";      // amount of transaction, currency non-specific
    private static final String FITID = "FITID";        // unique id of transaction
    private static final String NAME = "NAME";          // transaction name - interchangeable with memo
    private static final String MEMO = "MEMO";          // transaction memo - interchangeable with name

    // ledgerbalance elements
    public static final String LEDGERBAL = "LEDGERBAL"; // start/end of ledger balance element for an account
    public static final String BALAMT = "BALAMT";       // the balance of the account
    public static final String DTASOF = "DTASOF";       // the date on which the balance was recorded

    private static final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final Logger logger = LogManager.getLogger(OfxParser.class);

    public List<OfxExport> parse(final InputStream inputStream) throws IOException, OFXParseException {
        final Map<OfxAccount, List<OfxTransaction.TransactionBuilder>> transactions = new HashMap<>();
        final Map<OfxAccount, OfxBalance.Builder> accountBalances = new HashMap<>();

        // an OFX file that is exported from an institution may contain a mix of credit cards and traditional bank
        // accounts, but the credit cards may not have a bankId associated with them. This breaks the
        // TransactionCleanerFactory's ability to assign the correct transaction cleaner to the credit card.
        // record the first bankId found in the file and use it for every account unless otherwise specified.
        // this is a one element array because Java streams can only access effectively final variables from the parent scope :/
        final String[] bankId = {null};

        // no sense in making this a singleton, since this whole object is re-created every time the parse function is
        // called to avoid storing state in OfxParser
        final OFXReader ofxReader = new NanoXMLOFXReader();
        ofxReader.setContentHandler(new OFXHandler() {
            // in progress builders - the OFXReader executes callback methods as it encounters elements in the file, so
            // we need to start pojos and then add to them as new elements are read
            OfxAccount.Builder accountBuilder;
            OfxTransaction.TransactionBuilder transactionBuilder;

            // if the file contains transactions from multiple accounts, a BANKACCTFROM or CCACCTFROM entity will
            // proceed the transactions for each account. We can attach the transactions for the account to this object.
            OfxAccount currentAccount;

            // after each BANKTRANLIST element, there's a LEDGERBAL element that contains the current balance for that
            // account. We can use it to work backward and determine the account balance after each transaction took place
            boolean isLedgerBalanceActive = false;

            // fired whenever a new ofx entity (an account or a transaction) starts
            public void startAggregate(String name) {
                if (BANKACCTFROM.equalsIgnoreCase(name)) {
                    accountBuilder = OfxAccount.newBuilder();
                } else if (CCACCTFROM.equalsIgnoreCase(name)) {
                    // credit cards don't have an ACCTTYPE element, so force the type here
                    accountBuilder = OfxAccount.newBuilder()
                        .setAccountType("CREDIT_CARD");
                } else if (STMTTRN.equalsIgnoreCase(name)) {
                    transactionBuilder = OfxTransaction.newBuilder();
                    if (currentAccount != null) {
                        transactionBuilder.setAccount(currentAccount);
                    }
                } else if (LEDGERBAL.equalsIgnoreCase(name)) {
                    isLedgerBalanceActive = true;
                }
            }

            // fired for each attribute of the ofx entity
            public void onElement(String name, String value) throws OFXSyntaxException {
                switch (name.toUpperCase()) {
                    //account information
                    case BANKID:
                        // save the first bankId that we find in the file
                        if (StringUtils.isBlank(bankId[0])) {
                            bankId[0] = value;
                        }
                        accountBuilder.setBankId(value);
                        break;
                    case ACCTID:
                        // this element is used for the account id on normal bank accounts as well as credit cards
                        accountBuilder.setAccountId(value);
                        break;
                    case ACCTTYPE:
                        accountBuilder.setAccountType(value);
                        break;

                    // transaction information
                    case TRNTYPE:
                        transactionBuilder.setType(TransactionType.valueOf(value));
                        break;
                    case DTPOSTED:
                        try {
                            // date format is 20181210120000[-5:EST], but time is always set to 120000, so we can just ignore it
                            // and interpret the first 8 characters as a date
                            final LocalDate localDate = parseDate(value);
                            transactionBuilder.setDate(localDate);
                            break;
                        } catch (DateTimeParseException ex) {
                            throw new OFXSyntaxException(String.format("Failed to parse DTPOSTED %s as LocalDate", value), ex);
                        }
                    case TRNAMT:
                        try {
                            transactionBuilder.setAmount(Float.parseFloat(value));
                            break;
                        } catch (NumberFormatException ex) {
                            throw new OFXSyntaxException(String.format("Failed to parse TRNAMT %s as float", value), ex);
                        }
                    case FITID:
                        transactionBuilder.setFitId(value);
                        break;
                    case NAME:
                        transactionBuilder.setName(value);
                        break;
                    case MEMO:
                        transactionBuilder.setMemo(value);
                        break;

                    // ledgerbalance information
                    case BALAMT:
                        if (!isLedgerBalanceActive) {
                            break;
                        }

                        try {
                            final float amount = Float.parseFloat(value);
                            if (accountBalances.containsKey(currentAccount)) {
                                accountBalances.get(currentAccount).setAmount(amount);
                            } else {
                                accountBalances.put(currentAccount, OfxBalance.newBuilder().setAmount(amount));
                            }
                            break;
                        } catch (NumberFormatException ex) {
                            throw new OFXSyntaxException(String.format("Failed to parse BALAMT %s as float", value), ex);
                        }
                    case DTASOF:
                        if (!isLedgerBalanceActive) {
                            break;
                        }
                        try {
                            final LocalDate localDate = parseDate(value);
                            if (accountBalances.containsKey(currentAccount)) {
                                accountBalances.get(currentAccount).setDate(localDate);
                            } else {
                                accountBalances.put(currentAccount, OfxBalance.newBuilder().setDate(localDate));
                            }
                            break;
                        } catch (DateTimeParseException ex) {
                            throw new OFXSyntaxException(String.format("Failed to parse DTASOF %s as LocalDate", value), ex);
                        }

                    default:
                        // unhandled - there are lots of OFX elements that we don't use
                    }
                }

            // fired whenever the currently open ofx entity ends
            public void endAggregate(String name) {
                if (BANKACCTFROM.equalsIgnoreCase(name) || CCACCTFROM.equalsIgnoreCase(name)) {
                    currentAccount = accountBuilder.build();
                    logger.debug("Parsed bank account information {}", currentAccount);
                } else if (STMTTRN.equalsIgnoreCase(name)) {
                    if (transactions.containsKey(currentAccount)) {
                        transactions.put(currentAccount, Stream.concat(transactions.get(currentAccount).stream(),
                                Stream.of(transactionBuilder)).collect(Collectors.toList()));
                    } else {
                        transactions.put(currentAccount, Collections.singletonList(transactionBuilder));
                    }
                    logger.debug("Parsed transaction {}", transactionBuilder.build());
                } else if (LEDGERBAL.equalsIgnoreCase(name)) {
                    isLedgerBalanceActive = false;
                    final OfxBalance ofxBalance = accountBalances.get(currentAccount).build();
                    logger.debug("Recorded a balance of ${} on {} for account {}", ofxBalance.getAmount(), ofxBalance.getDate().toString(), currentAccount.getAccountId());
                }
            }

            // we ignore headers, so this is a no-op
            public void onHeader(String name, String value) {}
        });

        // blocks until the entire ofx file has been processed
        ofxReader.parse(inputStream);

        // bundle up the imported data for all accounts
        // returned list is sorted alphabetically by account id
        return transactions.entrySet().stream()
                .flatMap((Function<Map.Entry<OfxAccount, List<OfxTransaction.TransactionBuilder>>, Stream<Map.Entry<OfxAccount, List<OfxTransaction.TransactionBuilder>>>>) entry -> {
                    // if one of our accounts was not assigned a bankId, we can assume that the correct bankId is the
                    // first one found in the OFX file, since the file should only contain accounts that come from a
                    // single institution
                    // this ensures that credit cards get a bankId, which means that we run their transactions through
                    // the correct TransactionCleaner
                    final OfxAccount account = entry.getKey();
                    if (StringUtils.isBlank(account.getBankId()) && StringUtils.isNotBlank(bankId[0])) {
                        final OfxAccount updatedAccount = OfxAccount.newBuilder(account)
                                .setBankId(bankId[0])
                                .build();

                        final OfxBalance.Builder balance = accountBalances.remove(account);
                        accountBalances.put(updatedAccount, balance);

                        return Stream.of(Map.entry(updatedAccount, entry.getValue()));
                    }
                    return Stream.of(entry);
                })
                .map(entry -> new OfxExport(entry.getKey(), accountBalances.get(entry.getKey()).build(),
                        entry.getValue().stream()
                            .map(OfxTransaction.TransactionBuilder::build)
                            .collect(Collectors.toList()))
                )
                .sorted(Comparator.comparing(o -> o.getAccount().getAccountId()))
                .collect(Collectors.toList());
    }

    private LocalDate parseDate(String value) {
        // date format is 20181210120000[-5:EST], but time is always set to 120000, so we can just ignore it
        // and interpret the first 8 characters as a date
        return LocalDate.parse(value.substring(0, 8), dateFormatter);
    }
}
