package ca.jonathanfritz.ofxcat.dao;

import ca.jonathanfritz.ofxcat.transactions.Category;

/**
 * Represents the relationship between a {@link ca.jonathanfritz.ofxcat.transactions.Transaction#description} string and
 * a {@link Category} in the database
 */
public class DescriptionCategory {

    private final String description;
    private final Category category;

    public DescriptionCategory(String description, Category category) {
        this.description = description;
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }
}
