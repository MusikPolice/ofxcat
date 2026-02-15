package ca.jonathanfritz.ofxcat.io;

import java.util.Objects;

public class OfxAccount {

    private final String bankId;
    private final String accountId;
    private final String accountType;

    private OfxAccount(Builder builder) {
        bankId = builder.bankId;
        accountId = builder.accountId;
        accountType = builder.accountType;
    }

    public String getBankId() {
        return bankId;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getAccountType() {
        return accountType;
    }

    @Override
    public String toString() {
        return "OfxAccount{" + "bankId='"
                + bankId + '\'' + ", accountId='"
                + accountId + '\'' + ", accountType='"
                + accountType + '\'' + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OfxAccount that = (OfxAccount) o;
        return Objects.equals(bankId, that.bankId)
                && Objects.equals(accountId, that.accountId)
                && Objects.equals(accountType, that.accountType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bankId, accountId, accountType);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static Builder newBuilder(OfxAccount copy) {
        Builder builder = new Builder();
        builder.bankId = copy.getBankId();
        builder.accountId = copy.getAccountId();
        builder.accountType = copy.getAccountType();
        return builder;
    }

    public static final class Builder {
        private String bankId;
        private String accountId;
        private String accountType;

        private Builder() {}

        public Builder setBankId(String bankId) {
            this.bankId = bankId;
            return this;
        }

        public Builder setAccountId(String accountId) {
            this.accountId = accountId;
            return this;
        }

        public Builder setAccountType(String accountType) {
            this.accountType = accountType;
            return this;
        }

        public OfxAccount build() {
            return new OfxAccount(this);
        }
    }
}
