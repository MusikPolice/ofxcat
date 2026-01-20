package ca.jonathanfritz.ofxcat.datastore.dto;

import ca.jonathanfritz.ofxcat.datastore.utils.Entity;

import java.util.Objects;

/**
 * Represents a single normalized token for a CategorizedTransaction.
 * Tokens are used for matching similar transactions.
 */
public class TransactionToken implements Entity {

    private final Long id;
    private final long transactionId;
    private final String token;

    public TransactionToken(long transactionId, String token) {
        this(null, transactionId, token);
    }

    public TransactionToken(Long id, long transactionId, String token) {
        this.id = id;
        this.transactionId = transactionId;
        this.token = token;
    }

    @Override
    public Long getId() {
        return id;
    }

    public long getTransactionId() {
        return transactionId;
    }

    public String getToken() {
        return token;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransactionToken that = (TransactionToken) o;
        return transactionId == that.transactionId &&
                Objects.equals(id, that.id) &&
                Objects.equals(token, that.token);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, transactionId, token);
    }

    @Override
    public String toString() {
        return "TransactionToken{" +
                "id=" + id +
                ", transactionId=" + transactionId +
                ", token='" + token + '\'' +
                '}';
    }
}
