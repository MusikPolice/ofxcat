package ca.jonathanfritz.ofxcat;

import ca.jonathanfritz.ofxcat.datastore.dto.Account;
import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import com.webcohesion.ofx4j.domain.data.banking.AccountType;
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

    public static Transaction createRandomTransaction(Account account, LocalDate date, float amount, Transaction.TransactionType type) {
        return Transaction.newBuilder(UUID.randomUUID().toString())
                .setAccount(account)
                .setDate(date)
                .setAmount(amount)
                .setType(type)
                .setDescription(fakeStores[RandomUtils.nextInt(0, fakeStores.length)])
                .setBalance(Math.round(RandomUtils.nextFloat(0, 10000) * 100f) / 100f)
                .build();
    }

    public static Transaction createRandomTransaction() {
        final Account account = createRandomAccount();
        final LocalDate date = LocalDate.of(RandomUtils.nextInt(2020, 2023), RandomUtils.nextInt(1, 13), RandomUtils.nextInt(1, 29));
        final float amount = Math.round(RandomUtils.nextFloat(-100, 100) * 100f) / 100f;
        final Transaction.TransactionType type = amount > 0 ? Transaction.TransactionType.CREDIT : Transaction.TransactionType.DEBIT;
        return createRandomTransaction(account, date, amount, type);
    }
}
