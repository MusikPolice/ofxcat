package ca.jonathanfritz.ofxcat.io;

import java.util.Objects;

public class OfxAccount {

    private final String institutionId;
    private final String accountNumber;
    private final String accountType;

    private OfxAccount(Builder builder) {
        institutionId = builder.institutionId;
        accountNumber = builder.accountNumber;
        accountType = builder.accountType;
    }

    public String getInstitutionId() {
        return institutionId;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getAccountType() {
        return accountType;
    }

    @Override
    public String toString() {
        return "OfxAccount{" +
                "institutionId='" + institutionId + '\'' +
                ", accountNumber='" + accountNumber + '\'' +
                ", accountType='" + accountType + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OfxAccount that = (OfxAccount) o;
        return Objects.equals(institutionId, that.institutionId) &&
                Objects.equals(accountNumber, that.accountNumber) &&
                Objects.equals(accountType, that.accountType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(institutionId, accountNumber, accountType);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static Builder newBuilder(OfxAccount copy) {
        Builder builder = new Builder();
        builder.institutionId = copy.getInstitutionId();
        builder.accountNumber = copy.getAccountNumber();
        builder.accountType = copy.getAccountType();
        return builder;
    }


    public static final class Builder {
        private String institutionId;
        private String accountNumber;
        private String accountType;

        private Builder() {
        }

        public Builder setInstitutionId(String institutionId) {
            this.institutionId = institutionId;
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

        public OfxAccount build() {
            return new OfxAccount(this);
        }
    }
}
