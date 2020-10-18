package ca.jonathanfritz.ofxcat.transactions;

import ca.jonathanfritz.ofxcat.datastore.utils.Entity;

import java.util.Objects;

/**
 * Represents a {@link Transaction} that has been associated with a {@link Category}
 */
public class CategorizedTransaction extends Transaction implements Entity {

    private final Long id;
    private final Category category;

    public CategorizedTransaction(Transaction transaction, Category category) {
        this(null, transaction, category);
    }

    public CategorizedTransaction(Long id, Transaction transaction, Category category) {
        super(Transaction.newBuilder(transaction));
        this.id = id;
        this.category = category;
    }

    public Category getCategory() {
        return category;
    }

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public String toString() {
        return "CategorizedTransaction{" +
                "id=" + id +
                ", category=" + category +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        CategorizedTransaction that = (CategorizedTransaction) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(category, that.category);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), id, category);
    }
}
