package ca.jonathanfritz.ofxcat.io;

import com.webcohesion.ofx4j.io.OFXParseException;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.Is;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

class OfxParserTest {

    private static final String ONE_ACCOUNT_OFX = "oneaccount.ofx";
    private static final String TWO_ACCOUNTS_OFX = "twoaccounts.ofx";

    // this test data matches the contents of the two ofx files
    private static final LocalDate december10th2018 = LocalDate.of(2018, 12, 10);
    private static final OfxAccount expectedAccount1 = OfxAccount.newBuilder()
            .setAccountId("089429276203")
            .setAccountType("CHECKING")
            .setBankId("900000100")
            .build();
    private static final Set<OfxTransaction> expectedAccount1Transactions = Set.of(
            OfxTransaction.newBuilder().setType("POS").setDate(december10th2018).setAmount(-31.21f).setFitId("90000010020181210D0219892AA17").setName("CHEESECAKE FACTORY").setMemo("IDP PURCHASE - 7135").setAccount(expectedAccount1).build(),
            OfxTransaction.newBuilder().setType("POS").setDate(december10th2018).setAmount(-61.4f).setFitId("90000010020181210D02197E2FA27").setName("WILD WING").setMemo("IDP PURCHASE - 8381").setAccount(expectedAccount1).build(),
            OfxTransaction.newBuilder().setType("ATM").setDate(december10th2018).setAmount(-360.0f).setFitId("90000010020181210D0219652EA07").setName("Withdrawal").setMemo("PTB WD --- KB681166").setAccount(expectedAccount1).build(),
            OfxTransaction.newBuilder().setType("DEBIT").setDate(december10th2018).setAmount(-300.0f).setFitId("90000010020181210D02192620AD7").setName("Loan Pmt").setMemo("WWW LOAN PMT - 2155").setAccount(expectedAccount1).build(),
            OfxTransaction.newBuilder().setType("DEBIT").setDate(december10th2018).setAmount(-7.08f).setFitId("90000010020181210D02192320AA7").setName("C-IDP PURCHASE-7123").setMemo("FARM BOY").setAccount(expectedAccount1).build(),
            OfxTransaction.newBuilder().setType("DEBIT").setDate(december10th2018).setAmount(-68.49f).setFitId("90000010020181210D02194C22AA7").setName("C-IDP PURCHASE-1229").setMemo("VALUMART").setAccount(expectedAccount1).build()
    );

    private static final LocalDate december17th2018 = LocalDate.of(2018, 12, 17);
    private static final OfxAccount expectedAccount2 = OfxAccount.newBuilder()
            .setAccountId("089321286209")
            .setAccountType("CHECKING")
            .setBankId("900000100")
            .build();
    private static final Set<OfxTransaction> expectedAccount2Transactions = Set.of(
            OfxTransaction.newBuilder().setType("POS").setDate(december17th2018).setAmount(-241.94f).setFitId("90000010020181217C08184422CF1").setName("A AND M WOOD A").setMemo("IDP PURCHASE - 2835").setAccount(expectedAccount2).build(),
            OfxTransaction.newBuilder().setType("DEBIT").setDate(december17th2018).setAmount(-190.34f).setFitId("90000010020181217C0910F89BF59").setName("UTILITY BILL PMT").setMemo("UTILITIES").setAccount(expectedAccount2).build()
    );

    @Test
    void parseOneAccountTest() throws IOException, OFXParseException {
        final OfxParser ofxParser = new OfxParser();
        final Map<OfxAccount, Set<OfxTransaction>> accountTransactions = ofxParser.parse(loadOfxFile(ONE_ACCOUNT_OFX));

        // there should be one account that contains six transactions
        MatcherAssert.assertThat(accountTransactions.size(), IsEqual.equalTo(1));
        final Set<OfxTransaction> transactions = accountTransactions.get(expectedAccount1);
        MatcherAssert.assertThat(transactions.size(), IsEqual.equalTo(6));
        MatcherAssert.assertThat(transactions.stream().allMatch(t -> t.getAccount().equals(expectedAccount1)), Is.is(true));
        MatcherAssert.assertThat(transactions.stream().allMatch(t -> t.getDate().equals(december10th2018)), Is.is(true));
        MatcherAssert.assertThat(transactions, IsEqual.equalTo(expectedAccount1Transactions));
    }

    @Test
    void parseTwoAccountsTest() throws IOException, OFXParseException {
        final OfxParser ofxParser = new OfxParser();
        final Map<OfxAccount, Set<OfxTransaction>> accountTransactions = ofxParser.parse(loadOfxFile(TWO_ACCOUNTS_OFX));

        MatcherAssert.assertThat(accountTransactions.size(), IsEqual.equalTo(2));

        // one account contains six transactions
        Set<OfxTransaction> transactions = accountTransactions.get(expectedAccount1);
        MatcherAssert.assertThat(transactions.size(), IsEqual.equalTo(6));
        MatcherAssert.assertThat(transactions.stream().allMatch(t -> t.getAccount().equals(expectedAccount1)), Is.is(true));
        MatcherAssert.assertThat(transactions.stream().allMatch(t -> t.getDate().equals(december10th2018)), Is.is(true));
        MatcherAssert.assertThat(transactions, IsEqual.equalTo(expectedAccount1Transactions));

        // the other account contains two transactions
        transactions = accountTransactions.get(expectedAccount2);
        MatcherAssert.assertThat(transactions.size(), IsEqual.equalTo(2));
        MatcherAssert.assertThat(transactions.stream().allMatch(t -> t.getAccount().equals(expectedAccount2)), Is.is(true));
        MatcherAssert.assertThat(transactions.stream().allMatch(t -> t.getDate().equals(december17th2018)), Is.is(true));
        MatcherAssert.assertThat(transactions, IsEqual.equalTo(expectedAccount2Transactions));
    }

    private InputStream loadOfxFile(String filename) {
        return this.getClass().getClassLoader().getResourceAsStream(filename);
    }
}