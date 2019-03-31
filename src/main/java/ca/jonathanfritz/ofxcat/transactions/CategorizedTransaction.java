package ca.jonathanfritz.ofxcat.transactions;

/**
 * Represents a {@link Transaction} that has been associated with a {@link Category}
 */
public class CategorizedTransaction extends Transaction {

    private final Category category;

    CategorizedTransaction(Transaction transaction, Category category) {
        super(transaction.getType(), transaction.getDate(), transaction.getAmount(), transaction.getDescription());
        this.category = category;
    }

    public Category getCategory() {
        return category;
    }
}
