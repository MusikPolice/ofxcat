package ca.jonathanfritz.ofxcat;

import ca.jonathanfritz.ofxcat.datastore.dto.Account;
import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import ca.jonathanfritz.ofxcat.io.OfxAccount;
import ca.jonathanfritz.ofxcat.io.OfxTransaction;
import com.webcohesion.ofx4j.domain.data.banking.AccountType;
import com.webcohesion.ofx4j.domain.data.common.TransactionType;
import org.apache.commons.lang3.RandomUtils;

import java.time.LocalDate;
import java.util.UUID;

public class TestUtils {

    private static final String[] fakeStores = new String[]{
            "Outdoor Man",
            "Empire Records",
            "Pendant Publishing",
            "The Very Big Corporation of America",
            "WKRP",
            "Los Pollos Hermanos",
            "Vandelay Industries",
            "Soylent Industries",
            "Sterling Cooper Advertising Agency",
            "Championship Vinyl",
            "Cyberdyne Systems",
            "Ollivanders Wand Shop",
            "International Genetic Technologies",
            "Bluth Company",
            "Duff Beer",
            "Central Perk",
            "Cheers",
            "Ghostbusters",
            "Initech",
            "Wayne Enterprises",
            "Stark Industries",
            "Wonka Industries",
            "Monsters, Inc",
            "Dunder Mifflin Paper Company",
            "ACME Corp"
    };

    private static final String[] fakeCategories = new String[] {
            "Famous Kareem Abdul-Jabbars",
            "Countries between Mexico and Canada",
            "Automatic Points",
            "Tie Your Shoe",
            "Potent Potables",
            "Le Tits, Now",
            "Catch the Semen",
            "S' Words",
            "The Rapists",
            "Things You Shouldn't Put in Your Mouth",
            "The Number After 2",
            "Rhymes With 'Dog'",
            "Jap Anus Relations",
            "States That End in Hampshire",
            "What Color Is Green?",
            "Current Black presidents",
            "Sounds That Kitties Make",
            "The Penis Mightier",
            "States That Begin with California",
            "Anal Bum Cover"
    };

    public static Account createRandomAccount(String name) {
        return Account.newBuilder()
                .setAccountNumber(UUID.randomUUID().toString())
                .setName(name)
                .setBankId(UUID.randomUUID().toString())
                .setAccountType(AccountType.values()[RandomUtils.nextInt(0, AccountType.values().length)].name())
                .setId((long) UUID.randomUUID().hashCode())
                .build();
    }

    public static Account createRandomAccount() {
        return createRandomAccount(UUID.randomUUID().toString());
    }

    public static Transaction createRandomTransaction(Account account, String fitId, LocalDate date, float amount, Transaction.TransactionType type) {
        return Transaction.newBuilder(fitId)
                .setAccount(account)
                .setDate(date)
                .setAmount(amount)
                .setType(type)
                .setDescription(fakeStores[RandomUtils.nextInt(0, fakeStores.length)])
                .setBalance(Math.round(RandomUtils.nextFloat(0, 10000) * 100f) / 100f)
                .build();
    }

    public static Transaction createRandomTransaction(Account account, String fitId) {
        final LocalDate date = LocalDate.of(RandomUtils.nextInt(2020, 2023), RandomUtils.nextInt(1, 13), RandomUtils.nextInt(1, 29));
        final float amount = getRandomAmount();
        final Transaction.TransactionType type = amount > 0 ? Transaction.TransactionType.CREDIT : Transaction.TransactionType.DEBIT;
        return createRandomTransaction(account, fitId, date, amount, type);
    }

    public static Transaction createRandomTransaction(Account account) {
        return createRandomTransaction(account, UUID.randomUUID().toString());
    }

    public static Transaction createRandomTransaction(Account account, LocalDate date) {
        final float amount = getRandomAmount();
        final Transaction.TransactionType type = amount > 0 ? Transaction.TransactionType.CREDIT : Transaction.TransactionType.DEBIT;
        return createRandomTransaction(account, UUID.randomUUID().toString(), date, amount, type);
    }

    public static Transaction createRandomTransaction() {
        final Account account = createRandomAccount();
        final LocalDate date = LocalDate.of(RandomUtils.nextInt(2020, 2023), RandomUtils.nextInt(1, 13), RandomUtils.nextInt(1, 29));
        final float amount = getRandomAmount();
        final Transaction.TransactionType type = amount > 0 ? Transaction.TransactionType.CREDIT : Transaction.TransactionType.DEBIT;
        final String fitId = UUID.randomUUID().toString();
        return createRandomTransaction(account, fitId, date, amount, type);
    }

    /**
     * Returns a random float between -100 and +100 with 2 decimal places of precision
     */
    private static float getRandomAmount() {
        final int multiplier = RandomUtils.nextBoolean() ? 1 : -1;
        return (Math.round(RandomUtils.nextFloat(0, 200) * 100f) / 100f) * multiplier;
    }

    public static OfxAccount accountToOfxAccount(Account account) {
        return OfxAccount.newBuilder()
                .setAccountId(account.getAccountNumber())
                .setBankId(account.getBankId())
                .setAccountType(account.getAccountType())
                .build();
    }

    public static OfxTransaction transactionToOfxTransaction(Transaction transaction) {
        return OfxTransaction.newBuilder()
                .setFitId(transaction.getFitId())
                .setAmount(transaction.getAmount())
                .setName(transaction.getDescription())
                .setType(TransactionType.valueOf(transaction.getType().name()))
                .setDate(transaction.getDate())
                .setAccount(accountToOfxAccount(transaction.getAccount()))
                .build();
    }

    public static Category createRandomCategory() {
        return new Category(fakeCategories[RandomUtils.nextInt(0, fakeCategories.length)]);
    }
}