package ca.jonathanfritz.ofxcat.transactions;

import java.time.LocalDate;
import java.util.Objects;

public class Transaction {

    private final TransactionType type;
    private final LocalDate date;
    private final float amount;
    private final String description;

    public Transaction(TransactionType type, LocalDate date, float amount, String description) {
        this.type = type;
        this.date = date;
        this.amount = amount;
        this.description = description;
    }

    public TransactionType getType() {
        return type;
    }

    public LocalDate getDate() {
        return date;
    }

    public float getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "type=" + type +
                ", date=" + date +
                ", amount=" + amount +
                ", description='" + description + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Transaction that = (Transaction) o;
        return Float.compare(that.amount, amount) == 0 &&
                type == that.type &&
                Objects.equals(date, that.date) &&
                Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, date, amount, description);
    }

    public enum TransactionType {

        /**
         * The type can't be determined
         */
        UNKNOWN,

        /**
         * Cash withdrawal at an ATM
         */
        ATM,

        /**
         * Funds credited to the account (your balance increases)
         */
        CREDIT,

        /**
         * Funds debited from the account (your balance decreases)
         */
        DEBIT,

        /**
         * Point of sale purchase, typically interac debit or visa
         */
        POS
    }
}
