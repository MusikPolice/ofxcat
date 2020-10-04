package ca.jonathanfritz.ofxcat.transactions;

import ca.jonathanfritz.ofxcat.datastore.utils.Entity;

import java.util.Objects;

public class Account implements Entity {

    private final Long id;
    private final String bankId;
    private final String accountId;
    private final String accountType;
    private final String name;

    private Account(Builder builder) {
        id = builder.id;
        bankId = builder.bankId;
        accountId = builder.accountId;
        accountType = builder.accountType;
        name = builder.name;
    }

    @Override
    public Long getId() {
        return id;
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
                Objects.equals(name, account.name) &&
                Objects.equals(id, account.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bankId, accountId, accountType, name, id);
    }

    @Override
    public String toString() {
        return "Account{" +
                "bankId='" + bankId + '\'' +
                ", accountId='" + accountId + '\'' +
                ", accountType='" + accountType + '\'' +
                ", name='" + name + '\'' +
                ", id=" + id +
                '}';
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static Builder newBuilder(Account copy) {
        Builder builder = new Builder();
        if (copy != null) {
            builder.bankId = copy.getBankId();
            builder.accountId = copy.getAccountId();
            builder.accountType = copy.getAccountType();
            builder.name = copy.getName();
        }
        return builder;
    }

    public static final class Builder {
        private Long id;
        private String bankId;
        private String accountId;
        private String accountType;
        private String name;

        private Builder() {
        }

        public Builder setId(Long id) {
            this.id = id;
            return this;
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
