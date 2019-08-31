package ca.jonathanfritz.ofxcat.io;

import com.webcohesion.ofx4j.io.OFXHandler;
import com.webcohesion.ofx4j.io.OFXParseException;
import com.webcohesion.ofx4j.io.OFXReader;
import com.webcohesion.ofx4j.io.OFXSyntaxException;
import com.webcohesion.ofx4j.io.nanoxml.NanoXMLOFXReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class OfxParser {

    // TODO: the ofx format contains an AVAILBAL block that shows the balance in the account on the date of export. Could use to work backward and find balance after each transaction

    // bank account information elements
    private static final String BANKACCTFROM = "BANKACCTFROM";  // start/end of account info element
    private static final String BANKID = "BANKID";              // unique id of the institution
    private static final String ACCTID = "ACCTID";              // bank account number that the transactions belong to
    private static final String ACCTTYPE = "ACCTTYPE";          // type of bank account

    // transaction elements
    private static final String STMTTRN = "STMTTRN";    // start/end of transaction element
    private static final String TRNTYPE = "TRNTYPE";    // the type of transaction
    private static final String DTPOSTED = "DTPOSTED";  // date on which the transaction occurred
    private static final String TRNAMT = "TRNAMT";      // amount of transaction, currency non-specific
    private static final String FITID = "FITID";        // unique id of transaction
    private static final String NAME = "NAME";          // transaction name - interchangeable with memo
    private static final String MEMO = "MEMO";          // transaction memo - interchangeable with name

    private static final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final Logger logger = LoggerFactory.getLogger(OfxParser.class);

    public Map<OfxAccount, Set<OfxTransaction>> parse(final InputStream inputStream) throws IOException, OFXParseException {
        final Map<OfxAccount, Set<OfxTransaction>> transactions = new HashMap<>();

        final OFXReader ofxReader = new NanoXMLOFXReader();
        ofxReader.setContentHandler(new OFXHandler() {

            // in progress builders - the OFXReader executes callback methods as it encounters elements in the file, so
            // we need to start pojos and then add to them as new elements are read
            OfxAccount.Builder accountBuilder;
            OfxTransaction.TransactionBuilder transactionBuilder;

            // if the file contains transactions from multiple accounts, a BANKACCTFROM entity will proceed the transactions
            // for each account. We can attach the transactions for the account to this object
            OfxAccount currentAccount;

            // fired whenever a new ofx entity (an account or a transaction) starts
            public void startAggregate(String name) {
                if (BANKACCTFROM.equalsIgnoreCase(name)) {
                    accountBuilder = OfxAccount.newBuilder();
                } else if (STMTTRN.equalsIgnoreCase(name)) {
                    transactionBuilder = OfxTransaction.newBuilder();
                    if (currentAccount != null) {
                        transactionBuilder.setAccount(currentAccount);
                    }
                }
            }

            // fired for each attribute of the ofx entity
            public void onElement(String name, String value) throws OFXSyntaxException {
                switch (name.toUpperCase()) {
                    //account information
                    case BANKID:
                        accountBuilder.setInstitutionId(value);
                        break;
                    case ACCTID:
                        accountBuilder.setAccountNumber(value);
                        break;
                    case ACCTTYPE:
                        accountBuilder.setAccountType(value);
                        break;

                    // transaction information
                    case TRNTYPE:
                        transactionBuilder.setType(value);
                        break;
                    case DTPOSTED:
                        try {
                            // date format is 20181210120000[-5:EST], but date is always set to 120000, so we can just ignore it
                            // and interpret the first 8 characters as a date
                            final LocalDate localDate = LocalDate.parse(value.substring(0, 8), dateFormatter);
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
                    default:
                        // unhandled - there are lots of OFX elements that we don't use
                }
            }

            // fired whenever the currently open ofx entity ends
            public void endAggregate(String name) {
                if (BANKACCTFROM.equalsIgnoreCase(name)) {
                    currentAccount = accountBuilder.build();
                    logger.debug("Parsed bank account information {}", currentAccount);
                } else if (STMTTRN.equalsIgnoreCase(name)) {
                    final OfxTransaction t = transactionBuilder.build();
                    if (transactions.containsKey(currentAccount)) {
                        final Set<OfxTransaction> accountTransactions = new HashSet<>(transactions.get(currentAccount));
                        accountTransactions.add(t);
                        transactions.put(currentAccount, accountTransactions);
                    } else {
                        transactions.put(currentAccount, Set.of(t));
                    }
                    logger.debug("Parsed transaction {}", t);
                }
            }

            // we ignore headers, so this is a no-op
            public void onHeader(String name, String value) {}
        });

        // blocks until the entire ofx file has been processed
        ofxReader.parse(inputStream);
        return transactions;
    }
}
