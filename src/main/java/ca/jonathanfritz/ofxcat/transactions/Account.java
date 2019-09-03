package ca.jonathanfritz.ofxcat.transactions;

import java.util.Objects;

public class Account {

    private final String bankId;
    private final String accountId;
    private final String accountType;
    private final String name;

    private Account(Builder builder) {
        bankId = builder.bankId;
        accountId = builder.accountId;
        accountType = builder.accountType;
        name = builder.name;
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

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Account account = (Account) o;
        return Objects.equals(bankId, account.bankId) &&
                Objects.equals(accountId, account.accountId) &&
                Objects.equals(accountType, account.accountType) &&
                Objects.equals(name, account.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bankId, accountId, accountType, name);
    }

    @Override
    public String toString() {
        return "Account{" +
                "bankId='" + bankId + '\'' +
                ", accountId='" + accountId + '\'' +
                ", accountType='" + accountType + '\'' +
                ", name='" + name + '\'' +
                '}';
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static Builder newBuilder(Account copy) {
        Builder builder = new Builder();
        builder.bankId = copy.getBankId();
        builder.accountId = copy.getAccountId();
        builder.accountType = copy.getAccountType();
        builder.name = copy.getName();
        return builder;
    }

    public static final class Builder {
        private String bankId;
        private String accountId;
        private String accountType;
        private String name;

        private Builder() {
        }

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

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Account build() {
            return new Account(this);
        }
    }
}
