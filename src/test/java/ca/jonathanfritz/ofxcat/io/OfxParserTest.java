package ca.jonathanfritz.ofxcat.io;

import com.webcohesion.ofx4j.domain.data.common.TransactionType;
import com.webcohesion.ofx4j.io.OFXParseException;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.Is;
import org.hamcrest.core.IsEqual;
import org.hamcrest.core.IsNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OfxParserTest {

    private static final String ONE_ACCOUNT_OFX = "oneaccount.ofx";
    private static final String TWO_ACCOUNTS_OFX = "twoaccounts.ofx";
    private static final String CREDIT_CARD_OFX = "creditcard.ofx";
    private final String twoAccountsOneCreditCard = "twoaccountsonecreditcard.ofx";

    // this test data matches the contents of the two ofx files
    private static final LocalDate december10th2018 = LocalDate.of(2018, 12, 10);
    private static final OfxAccount expectedAccount1 = OfxAccount.newBuilder()
            .setAccountId("089429276203")
            .setAccountType("CHECKING")
            .setBankId("900000100")
            .build();
    private static final List<OfxTransaction> expectedAccount1Transactions = Arrays.asList(
            OfxTransaction.newBuilder().setType(TransactionType.POS).setDate(december10th2018).setAmount(-31.21f)
                    .setFitId("90000010020181210D0219892AA17").setName("CHEESECAKE FACTORY")
                    .setMemo("IDP PURCHASE - 7135").setAccount(expectedAccount1).build(),
            OfxTransaction.newBuilder().setType(TransactionType.POS).setDate(december10th2018).setAmount(-61.4f)
                    .setFitId("90000010020181210D02197E2FA27").setName("WILD WING")
                    .setMemo("IDP PURCHASE - 8381").setAccount(expectedAccount1).build(),
            OfxTransaction.newBuilder().setType(TransactionType.ATM).setDate(december10th2018).setAmount(-360.0f)
                    .setFitId("90000010020181210D0219652EA07").setName("Withdrawal")
                    .setMemo("PTB WD --- KB681166").setAccount(expectedAccount1).build(),
            OfxTransaction.newBuilder().setType(TransactionType.CREDIT).setDate(december10th2018).setAmount(300.0f)
                    .setFitId("90000010020181210D02192620AD7")
                    .setMemo("Bank Error in Your Favour").setAccount(expectedAccount1).build(),
            OfxTransaction.newBuilder().setType(TransactionType.DEBIT).setDate(december10th2018).setAmount(-7.08f)
                    .setFitId("90000010020181210D02192320AA7").setName("C-IDP PURCHASE-7123")
                    .setMemo("FARM BOY").setAccount(expectedAccount1).build(),
            OfxTransaction.newBuilder().setType(TransactionType.DEBIT).setDate(december10th2018).setAmount(-68.49f)
                    .setFitId("90000010020181210D02194C22AA7").setName("C-IDP PURCHASE-1229")
                    .setMemo("VALUMART").setAccount(expectedAccount1).build()
    );
    private static final OfxBalance expectedAccount1Balance = OfxBalance.newBuilder()
            .setDate(LocalDate.of(2019, 3, 19))
            .setAmount(15125.81f)
            .build();

    private static final LocalDate december17th2018 = LocalDate.of(2018, 12, 17);
    private static final OfxAccount expectedAccount2 = OfxAccount.newBuilder()
            .setAccountId("089321286209")
            .setAccountType("CHECKING")
            .setBankId("900000100")
            .build();
    private static final List<OfxTransaction> expectedAccount2Transactions = Arrays.asList(
            OfxTransaction.newBuilder().setType(TransactionType.POS).setDate(december17th2018).setAmount(-241.94f)
                    .setFitId("90000010020181217C08184422CF1").setName("A AND M WOOD A")
                    .setMemo("IDP PURCHASE - 2835").setAccount(expectedAccount2).build(),
            OfxTransaction.newBuilder().setType(TransactionType.DEBIT).setDate(december17th2018).setAmount(-190.34f)
                    .setFitId("90000010020181217C0910F89BF59").setName("UTILITY BILL PMT")
                    .setMemo("UTILITIES").setAccount(expectedAccount2).build()
    );
    private static final OfxBalance expectedAccount2Balance = OfxBalance.newBuilder()
            .setDate(LocalDate.of(2019, 3, 19))
            .setAmount(6239.75f)
            .build();

    @Test
    void parseOneAccountTest() throws IOException, OFXParseException {
        final OfxParser ofxParser = new OfxParser();
        final List<OfxExport> ofxExports = ofxParser.parse(loadOfxFile(ONE_ACCOUNT_OFX));

        // there should be one account that contains six transactions
        MatcherAssert.assertThat(ofxExports.size(), IsEqual.equalTo(1));
        final List<OfxTransaction> transactions = ofxExports.get(0).getTransactions().get(december10th2018);
        MatcherAssert.assertThat(transactions.size(), IsEqual.equalTo(6));
        MatcherAssert.assertThat(transactions.stream().allMatch(t -> t.getAccount().equals(expectedAccount1)), Is.is(true));
        MatcherAssert.assertThat(transactions.stream().allMatch(t -> t.getDate().equals(december10th2018)), Is.is(true));
        MatcherAssert.assertThat(transactions, IsEqual.equalTo(expectedAccount1Transactions));
        MatcherAssert.assertThat(ofxExports.get(0).getBalance(), IsEqual.equalTo(expectedAccount1Balance));
    }

    @Test
    void parseTwoAccountsTest() throws IOException, OFXParseException {
        final OfxParser ofxParser = new OfxParser();
        final List<OfxExport> ofxExports = ofxParser.parse(loadOfxFile(TWO_ACCOUNTS_OFX));

        MatcherAssert.assertThat(ofxExports.size(), IsEqual.equalTo(2));

        // one account contains two transactions
        List<OfxTransaction> transactions = ofxExports.get(0).getTransactions().get(december17th2018);
        MatcherAssert.assertThat(transactions.size(), IsEqual.equalTo(2));
        MatcherAssert.assertThat(transactions.stream().allMatch(t -> t.getAccount().equals(expectedAccount2)), Is.is(true));
        MatcherAssert.assertThat(transactions.stream().allMatch(t -> t.getDate().equals(december17th2018)), Is.is(true));
        MatcherAssert.assertThat(transactions, IsEqual.equalTo(expectedAccount2Transactions));
        MatcherAssert.assertThat(ofxExports.get(0).getBalance(), IsEqual.equalTo(expectedAccount2Balance));

        // the other account contains six transactions
        transactions = ofxExports.get(1).getTransactions().get(december10th2018);
        MatcherAssert.assertThat(transactions.size(), IsEqual.equalTo(6));
        MatcherAssert.assertThat(transactions.stream().allMatch(t -> t.getAccount().equals(expectedAccount1)), Is.is(true));
        MatcherAssert.assertThat(transactions.stream().allMatch(t -> t.getDate().equals(december10th2018)), Is.is(true));
        MatcherAssert.assertThat(transactions, IsEqual.equalTo(expectedAccount1Transactions));
        MatcherAssert.assertThat(ofxExports.get(1).getBalance(), IsEqual.equalTo(expectedAccount1Balance));
    }

    @Test
    void creditCardTransactionsTest() throws IOException, OFXParseException {
        final OfxParser ofxParser = new OfxParser();
        final List<OfxExport> ofxExports = ofxParser.parse(loadOfxFile(CREDIT_CARD_OFX));

        // there should be one credit card with a single transaction
        MatcherAssert.assertThat(ofxExports.size(), IsEqual.equalTo(1));
        final OfxAccount account = ofxExports.get(0).getAccount();

        // credit card id and type are set
        MatcherAssert.assertThat(account.getAccountId(), IsEqual.equalTo("1530257824995812"));
        MatcherAssert.assertThat(account.getAccountType(), IsEqual.equalTo("CREDIT_CARD"));

        // credit cards are not linked to any particular bank
        MatcherAssert.assertThat(account.getBankId(), IsEqual.equalTo(null));

        // there was one transaction in the file
        final Map<LocalDate, List<OfxTransaction>> transactions = ofxExports.get(0).getTransactions();
        MatcherAssert.assertThat(transactions.size(), IsEqual.equalTo(1));
        final LocalDate key = transactions.keySet().stream().findFirst().get();
        MatcherAssert.assertThat(key, IsEqual.equalTo(LocalDate.of(2020, 11, 7)));
        final List<OfxTransaction> ofxTransactions = transactions.get(key);
        MatcherAssert.assertThat(ofxTransactions.size(), IsEqual.equalTo(1));
        MatcherAssert.assertThat(ofxTransactions.get(0).getAccount(), IsEqual.equalTo(account));
        MatcherAssert.assertThat(ofxTransactions.get(0).getAmount(), IsEqual.equalTo(-13.99F));
        MatcherAssert.assertThat(ofxTransactions.get(0).getDate(), IsEqual.equalTo(key));
        MatcherAssert.assertThat(ofxTransactions.get(0).getName(), IsEqual.equalTo("NETFLIX.COM"));
        MatcherAssert.assertThat(ofxTransactions.get(0).getMemo(), IsEqual.equalTo("898-1629899 CA"));
        MatcherAssert.assertThat(ofxTransactions.get(0).getType(), IsEqual.equalTo(TransactionType.DEBIT));
    }

    @Test
    void allAccountsAndCreditCardsReadTest() throws IOException, OFXParseException {
        final OfxParser ofxParser = new OfxParser();
        final List<OfxExport> ofxExports = ofxParser.parse(loadOfxFile(twoAccountsOneCreditCard));

        // there should be two accounts and one credit card
        MatcherAssert.assertThat(ofxExports.size(), IsEqual.equalTo(3));

        // the first should be a checking account with two transactions
        final OfxExport checking = ofxExports.get(0);
        MatcherAssert.assertThat(checking.getAccount().getBankId(), IsNull.notNullValue());
        MatcherAssert.assertThat(checking.getAccount().getAccountId(), IsEqual.equalTo("078123116385"));
        MatcherAssert.assertThat(checking.getAccount().getAccountType(), IsEqual.equalTo("CHECKING"));
        MatcherAssert.assertThat(checking.getTransactions().values().stream().mapToLong(Collection::size).sum(), IsEqual.equalTo(2L));
        assertTrue(checking.getTransactions().values().stream()
                .flatMap(Collection::stream)
                .anyMatch(t -> t.getFitId().equalsIgnoreCase("90000410039201316C20DD355A88F")));
        assertTrue(checking.getTransactions().values().stream()
                .flatMap(Collection::stream)
                .anyMatch(t -> t.getFitId().equalsIgnoreCase("900000F0C40239126C001C35F9B43")));

        // the second should be a savings account with two transactions
        final OfxExport savings = ofxExports.get(1);
        MatcherAssert.assertThat(savings.getAccount().getBankId(), IsNull.notNullValue());
        MatcherAssert.assertThat(savings.getAccount().getAccountId(), IsEqual.equalTo("228035462751"));
        MatcherAssert.assertThat(savings.getAccount().getAccountType(), IsEqual.equalTo("SAVINGS"));
        MatcherAssert.assertThat(savings.getTransactions().values().stream().mapToLong(Collection::size).sum(), IsEqual.equalTo(2L));
        assertTrue(savings.getTransactions().values().stream()
                .flatMap(Collection::stream)
                .anyMatch(t -> t.getFitId().equalsIgnoreCase("900000C0820601159S401A9FDD687")));
        assertTrue(savings.getTransactions().values().stream()
                .flatMap(Collection::stream)
                .anyMatch(t -> t.getFitId().equalsIgnoreCase("90000040080203123400260F7415D")));

        // the third should be a credit card with one transaction
        final OfxExport creditCard = ofxExports.get(2);
        MatcherAssert.assertThat(creditCard.getAccount().getBankId(), IsNull.notNullValue());
        MatcherAssert.assertThat(creditCard.getAccount().getAccountId(), IsEqual.equalTo("3580452029974826"));
        MatcherAssert.assertThat(creditCard.getAccount().getAccountType(), IsEqual.equalTo("CREDIT_CARD"));
        MatcherAssert.assertThat(creditCard.getTransactions().values().stream().mapToLong(Collection::size).sum(), IsEqual.equalTo(1L));
        assertTrue(creditCard.getTransactions().values().stream()
                .flatMap(Collection::stream)
                .anyMatch(t -> t.getFitId().equalsIgnoreCase("9000001302920F103V05373431CF1")));

        // all three accounts should have the same bankId because they came from the same OFX file
        assertTrue(ofxExports.stream()
                .map(ofxExport -> ofxExport.getAccount().getBankId())
                .allMatch("900000100"::equals));
    }

    private InputStream loadOfxFile(String filename) {
        return this.getClass().getClassLoader().getResourceAsStream(filename);
    }
}
