package ca.jonathanfritz.ofxcat.datastore.dto;

import ca.jonathanfritz.ofxcat.datastore.utils.Entity;

import java.util.Objects;

public class Account implements Entity {

    private final Long id;
    private final String bankId;
    private final String accountNumber;
    private final String accountType;
    private final String name;

    private Account(Builder builder) {
        id = builder.id;
        bankId = builder.bankId;
        accountNumber = builder.accountNumber;
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

    public String getAccountNumber() {
        return accountNumber;
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
                Objects.equals(accountNumber, account.accountNumber) &&
                Objects.equals(accountType, account.accountType) &&
                Objects.equals(name, account.name) &&
                Objects.equals(id, account.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bankId, accountNumber, accountType, name, id);
    }

    @Override
    public String toString() {
        return "Account{" +
                "bankId='" + bankId + '\'' +
                ", accountNumber='" + accountNumber + '\'' +
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
            builder.id = copy.getId();
            builder.bankId = copy.getBankId();
            builder.accountNumber = copy.getAccountNumber();
            builder.accountType = copy.getAccountType();
            builder.name = copy.getName();
        }
        return builder;
    }

    public static final class Builder {
        private Long id;
        private String bankId;
        private String accountNumber;
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

        public Builder setAccountNumber(String accountNumber) {
            this.accountNumber = accountNumber;
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
