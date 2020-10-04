package ca.jonathanfritz.ofxcat.transactions;

import ca.jonathanfritz.ofxcat.datastore.utils.Entity;

import java.util.Objects;

/**
 * Represents the relationship between a {@link ca.jonathanfritz.ofxcat.transactions.Transaction#description} string and
 * a {@link Category} in the database
 */
public class DescriptionCategory implements Entity {

    private final Long id;
    private final String description;
    private final Category category;

    public DescriptionCategory(String description, Category category) {
        this(null, description, category);
    }

    public DescriptionCategory(Long id, String description, Category category) {
        this.id = id;
        this.description = description;
        this.category = category;
    }

    @Override
    public Long getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DescriptionCategory that = (DescriptionCategory) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(description, that.description) &&
                Objects.equals(category, that.category);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, description, category);
    }

    @Override
    public String toString() {
        return "DescriptionCategory{" +
                "id=" + id +
                ", description='" + description + '\'' +
                ", category=" + category +
                '}';
    }
}
