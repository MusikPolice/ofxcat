package ca.jonathanfritz.ofxcat.transactions;

/**
 * Represents a {@link Transaction} that has been associated with a {@link Category}
 */
public class CategorizedTransaction extends Transaction {

    private final Category category;

    public CategorizedTransaction(Transaction transaction, Category category) {
        super(Transaction.newBuilder(transaction));
        this.category = category;
    }

    public Category getCategory() {
        return category;
    }
}
