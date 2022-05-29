package ca.jonathanfritz.ofxcat.datastore.dto;

import ca.jonathanfritz.ofxcat.datastore.utils.Entity;

import java.util.Objects;

public class Category implements Entity {

    private final Long id;
    private final String name;

    /**
     * Corresponds to the default UNKNOWN category created in V8__Create_Default_Unknown_Category.sql
     * Used to categorize transactions that the user does not recognize
     */
    public static final Category UNKNOWN = new Category(0L, "UNKNOWN");

    /**
     * Corresponds to the default TRANSFER category created in V9__Create_Default_Transfer_Category.sql
     * Used to categorize transactions that represent inter-account transfers
     */
    public static final Category TRANSFER = new Category(1L, "TRANSFER");

    public Category(String name) {
        this(null, name);
    }

    public Category(Long id, String name) {
        this.id = id;
        this.name = name != null ? name.trim().toUpperCase() : null;
    }

    @Override
    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Category category = (Category) o;
        return Objects.equals(id, category.id) &&
                Objects.equals(name, category.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }

    @Override
    public String toString() {
        return "Category{" +
                "id=" + id +
                ", name='" + name + '\'' +
                '}';
    }
}
