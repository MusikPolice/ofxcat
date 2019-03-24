package ca.jonathanfritz.ofxcat.io;

import ca.jonathanfritz.ofxcat.transactions.Transaction;
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
import java.util.HashSet;
import java.util.Set;

public class OfxParser {

    private static final String STMTTRN = "STMTTRN";
    private static final String TRNTYPE = "TRNTYPE";
    private static final String DTPOSTED = "DTPOSTED";
    private static final String TRNAMT = "TRNAMT";
    private static final String FITID = "FITID";
    private static final String NAME = "NAME";
    private static final String MEMO = "MEMO";

    private static final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final Logger logger = LoggerFactory.getLogger(OfxParser.class);

    public Set<Transaction> parse(final InputStream inputStream) throws IOException, OFXParseException {
        final Set<Transaction> transactions = new HashSet<>();

        final OFXReader ofxReader = new NanoXMLOFXReader();
        ofxReader.setContentHandler(new OFXHandler() {

            Transaction.TransactionBuilder transactionBuilder;

            public void startAggregate(String name) {
                if (STMTTRN.equalsIgnoreCase(name)) {
                    transactionBuilder = Transaction.newBuilder();
                }
            }

            public void onElement(String name, String value) throws OFXSyntaxException {
                if (TRNTYPE.equalsIgnoreCase(name)) {
                    transactionBuilder.setType(value);
                } else if (DTPOSTED.equalsIgnoreCase(name)) {
                    try {
                        // date format is 20181210120000[-5:EST], but date is always set to 120000, so we can just ignore it
                        // and interpret the first 8 characters as a date
                        final LocalDate localDate = LocalDate.parse(value.substring(0, 8), dateFormatter);
                        transactionBuilder.setDate(localDate);
                    } catch (DateTimeParseException ex) {
                        throw new OFXSyntaxException(String.format("Failed to parse DTPOSTED %s as LocalDate", value), ex);
                    }
                } else if (TRNAMT.equalsIgnoreCase(name)) {
                    try {
                        transactionBuilder.setAmount(Float.parseFloat(value));
                    } catch (NumberFormatException ex) {
                        throw new OFXSyntaxException(String.format("Failed to parse TRNAMT %s as float", value), ex);
                    }
                } else if (FITID.equalsIgnoreCase(name)) {
                    transactionBuilder.setFitId(value);
                } else if (NAME.equalsIgnoreCase(name)) {
                    transactionBuilder.setName(value);
                } else if (MEMO.equalsIgnoreCase(name)) {
                    transactionBuilder.setMemo(value);
                }
            }

            public void endAggregate(String name) {
                if (STMTTRN.equalsIgnoreCase(name)) {
                    final Transaction t = transactionBuilder.build();
                    transactions.add(t);
                    logger.debug("Parsed transaction {}", t);
                }
            }

            public void onHeader(String s, String s1) {
                // we ignore headers, so this is a no-op
            }
        });

        ofxReader.parse(inputStream);
        return transactions;
    }

}
