package ca.jonathanfritz.ofxcat.transactions;

import java.time.LocalDate;
import java.util.Objects;

public class Transaction {

    private final TransactionType type;
    private final LocalDate date;
    private final float amount;
    private final String description;
    private final Account account;

    protected Transaction(Builder builder) {
        type = builder.type;
        date = builder.date;
        amount = builder.amount;
        description = builder.description;
        account = builder.account;
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

    public Account getAccount() {
        return account;
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "type=" + type +
                ", date=" + date +
                ", amount=" + amount +
                ", description='" + description + '\'' +
                ", account=" + account +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Transaction that = (Transaction) o;
        return Double.compare(that.amount, amount) == 0 &&
                type == that.type &&
                Objects.equals(date, that.date) &&
                Objects.equals(description, that.description) &&
                Objects.equals(account, that.account);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, date, amount, description, account);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static Builder newBuilder(Transaction copy) {
        Builder builder = new Builder();
        builder.type = copy.getType();
        builder.date = copy.getDate();
        builder.amount = copy.getAmount();
        builder.description = copy.getDescription();
        builder.account = Account.newBuilder(copy.getAccount()).build();
        return builder;
    }

    public static final class Builder {

        private TransactionType type;
        private LocalDate date;
        private float amount;
        private String description;
        private Account account;

        private Builder() {
        }

        public Builder setType(TransactionType type) {
            this.type = type;
            return this;
        }

        public Builder setDate(LocalDate date) {
            this.date = date;
            return this;
        }

        public Builder setAmount(float amount) {
            this.amount = amount;
            return this;
        }

        public Builder setDescription(String description) {
            this.description = description;
            return this;
        }

        public Builder setAccount(Account account) {
            this.account = account;
            return this;
        }

        public Transaction build() {
            return new Transaction(this);
        }
    }

    /**
     * Borrowed from {@link com.webcohesion.ofx4j.domain.data.common.TransactionType}
     */
    public enum TransactionType {

        /**
         * generic credit.
         */
        CREDIT,

        /**
         * generic debit.
         */
        DEBIT,

        /**
         * interest earned.
         */
        INT,

        /**
         * dividend.
         */
        DIV,

        /**
         * bank fee.
         */
        FEE,

        /**
         * service charge.
         */
        SRVCHG,

        /**
         * deposit.
         */
        DEP,

        /**
         * ATM transaction.
         */
        ATM,

        /**
         * point of sale
         */
        POS,

        /**
         * transfer
         */
        XFER,

        /**
         * check
         */
        CHECK,

        /**
         * electronic payment
         */
        PAYMENT,

        /**
         * cash.
         */
        CASH,

        /**
         * direct deposit.
         */
        DIRECTDEP,

        /**
         * merchant-initiated debit
         */
        DIRECTDEBIT,

        /**
         * repeating payment.
         */
        REPEATPMT,

        /**
         * other
         */
        OTHER
    }
}
