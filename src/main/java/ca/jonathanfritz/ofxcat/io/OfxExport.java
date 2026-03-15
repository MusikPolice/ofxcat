package ca.jonathanfritz.ofxcat.io;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OfxExport {
    private final OfxAccount account;
    private final OfxBalance balance;
    private final OfxBalance availableBalance;
    private final Map<LocalDate, List<OfxTransaction>> transactions;

    public OfxExport(OfxAccount account, OfxBalance balance, List<OfxTransaction> transactions) {
        this(account, balance, null, transactions);
    }

    public OfxExport(
            OfxAccount account, OfxBalance balance, OfxBalance availableBalance, List<OfxTransaction> transactions) {
        this.account = account;
        this.balance = balance;
        this.availableBalance = availableBalance;
        this.transactions = transactions.stream()
                .collect(Collectors.toMap(OfxTransaction::getDate, Collections::singletonList, (a, b) -> Stream.concat(
                                a.stream(), b.stream())
                        .collect(Collectors.toList())));
    }

    public OfxAccount getAccount() {
        return account;
    }

    public OfxBalance getBalance() {
        return balance;
    }

    /**
     * Returns the available balance from the OFX file, or {@code null} if absent.
     * For bank accounts (CHECKING, SAVINGS), this reflects the cleared balance without pending
     * payment deductions and should be preferred over {@link #getBalance()} as the anchor for
     * computing per-transaction running balances.
     */
    public OfxBalance getAvailableBalance() {
        return availableBalance;
    }

    public Map<LocalDate, List<OfxTransaction>> getTransactions() {
        return transactions;
    }
}
