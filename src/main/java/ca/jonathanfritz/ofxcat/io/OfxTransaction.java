package ca.jonathanfritz.ofxcat.io;

import com.webcohesion.ofx4j.domain.data.common.TransactionType;

import java.time.LocalDate;
import java.util.Objects;

public class OfxTransaction {
    private final TransactionType type;
    private final LocalDate date;
    private final float amount;
    private final String fitId; // bank id, followed by date in format yyyymmdd, followed by 12 character hex string
    private final String name;
    private final String memo;
    private final OfxAccount account;

    private OfxTransaction(TransactionBuilder transactionBuilder) {
        this.type = transactionBuilder.type;
        this.date = transactionBuilder.date;
        this.amount = transactionBuilder.amount;
        this.fitId = transactionBuilder.fitId;
        this.name = transactionBuilder.name;
        this.memo = transactionBuilder.memo;
        this.account = transactionBuilder.account;
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

    public String getFitId() {
        return fitId;
    }

    public String getName() {
        return name;
    }

    public String getMemo() {
        return memo;
    }

    public OfxAccount getAccount() {
        return account;
    }

    public static TransactionBuilder newBuilder() {
        return new TransactionBuilder();
    }

    public static TransactionBuilder newBuilder(OfxTransaction other) {
        return new TransactionBuilder()
                .setAmount(other.amount)
                .setDate(other.date)
                .setFitId(other.fitId)
                .setMemo(other.memo)
                .setName(other.name)
                .setType(other.type)
                .setAccount(other.account);
    }
    @Override
    public String toString() {
        return "OfxTransaction{" +
                "type='" + type + '\'' +
                ", date=" + date +
                ", amount=" + amount +
                ", fitId='" + fitId + '\'' +
                ", name='" + name + '\'' +
                ", memo='" + memo + '\'' +
                ", account=" + account +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OfxTransaction that = (OfxTransaction) o;
        return Float.compare(that.amount, amount) == 0 &&
                Objects.equals(type, that.type) &&
                Objects.equals(date, that.date) &&
                Objects.equals(fitId, that.fitId) &&
                Objects.equals(name, that.name) &&
                Objects.equals(memo, that.memo) &&
                Objects.equals(account, that.account);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, date, amount, fitId, name, memo, account);
    }

    public static class TransactionBuilder {
        private TransactionType type;
        private LocalDate date;
        private float amount;
        private String fitId;
        private String name;
        private String memo;
        private OfxAccount account;

        private TransactionBuilder() {}

        public TransactionBuilder setType(TransactionType type) {
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

        public TransactionBuilder setAccount(OfxAccount account) {
            this.account = account;
            return this;
        }

        public OfxTransaction build() {
            return new OfxTransaction(this);
        }
    }
}
