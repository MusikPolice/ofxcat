package ca.jonathanfritz.ofxcat.transactions;

import ca.jonathanfritz.ofxcat.dao.Entity;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

import java.util.Objects;

public class Category implements Entity {

    private final Long id;
    private final String name;

    public Category(String name) {
        this(null, name);
    }

    public Category(Long id, String name) {
        this.id = id;
        this.name = name != null ? name.trim().toUpperCase() : null;
    }

    public String getName() {
        return name;
    }

    @Override
    public Long getId() {
        return id;
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
        return ReflectionToStringBuilder.toString(this);
    }
}
