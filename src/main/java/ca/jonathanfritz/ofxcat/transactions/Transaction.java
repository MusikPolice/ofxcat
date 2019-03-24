package ca.jonathanfritz.ofxcat.transactions;

import org.apache.commons.lang3.StringUtils;

import java.time.LocalDate;
import java.util.Objects;

public class Transaction {
    private final String type;
    private final LocalDate date;
    private final float amount;
    private final String fitId;
    private final String name;
    private final String memo;

    Transaction(TransactionBuilder transactionBuilder) {
        this.type = transactionBuilder.type;
        this.date = transactionBuilder.date;
        this.amount = transactionBuilder.amount;
        this.fitId = transactionBuilder.fitId;
        this.name = transactionBuilder.name;
        this.memo = transactionBuilder.memo;
    }

    public LocalDate getDate() {
        return date;
    }

    public float getAmount() {
        return amount;
    }

    public String getDescription() {
        return (StringUtils.isNotBlank(name) ? name : "") + (StringUtils.isNotBlank(memo) ? " " + memo : "");
    }

    public static TransactionBuilder newBuilder() {
        return new TransactionBuilder();
    }

    public static TransactionBuilder newBuilder(Transaction other) {
        return new TransactionBuilder()
                .setAmount(other.amount)
                .setDate(other.date)
                .setFitId(other.fitId)
                .setMemo(other.memo)
                .setName(other.name)
                .setType(other.type);
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "date=" + date +
                ", amount=" + amount +
                ", description='" + getDescription() + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Transaction that = (Transaction) o;
        return Float.compare(that.amount, amount) == 0 &&
                Objects.equals(type, that.type) &&
                Objects.equals(date, that.date) &&
                Objects.equals(fitId, that.fitId) &&
                Objects.equals(name, that.name) &&
                Objects.equals(memo, that.memo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, date, amount, fitId, name, memo);
    }

    public static class TransactionBuilder {
        private String type;
        private LocalDate date;
        private float amount;
        private String fitId;
        private String name;
        private String memo;

        private TransactionBuilder() {}

        public TransactionBuilder setType(String type) {
            this.type = type;
            return this;
        }

        public TransactionBuilder setDate(LocalDate date) {
            this.date = date;
            return this;
        }

        public TransactionBuilder setAmount(float amount) {
            this.amount = amount;
            return this;
        }

        public TransactionBuilder setFitId(String fitId) {
            this.fitId = fitId;
            return this;
        }

        public TransactionBuilder setName(String name) {
            this.name = name;
            return this;
        }

        public TransactionBuilder setMemo(String memo) {
            this.memo = memo;
            return this;
        }

        public Transaction build() {
            return new Transaction(this);
        }
    }
}
