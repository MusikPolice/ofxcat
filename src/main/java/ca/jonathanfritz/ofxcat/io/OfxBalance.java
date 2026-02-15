package ca.jonathanfritz.ofxcat.io;

import java.time.LocalDate;
import java.util.Objects;

public class OfxBalance {
    private final float amount;
    private final LocalDate date;

    private OfxBalance(Builder builder) {
        amount = builder.amount;
        date = builder.date;
    }

    public float getAmount() {
        return amount;
    }

    public LocalDate getDate() {
        return date;
    }

    @Override
    public String toString() {
        return "OfxBalance{" + "amount=" + amount + ", date=" + date + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OfxBalance that = (OfxBalance) o;
        return Float.compare(that.amount, amount) == 0 && Objects.equals(date, that.date);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, date);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static Builder newBuilder(OfxBalance copy) {
        Builder builder = new Builder();
        builder.amount = copy.getAmount();
        builder.date = copy.getDate();
        return builder;
    }

    public static final class Builder {
        private float amount;
        private LocalDate date;

        private Builder() {}

        public Builder setAmount(float amount) {
            this.amount = amount;
            return this;
        }

        public Builder setDate(LocalDate date) {
            this.date = date;
            return this;
        }

        public OfxBalance build() {
            return new OfxBalance(this);
        }
    }
}
