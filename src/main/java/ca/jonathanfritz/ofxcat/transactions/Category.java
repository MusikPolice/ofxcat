package ca.jonathanfritz.ofxcat.transactions;

import java.util.Objects;

public class Category {

    private final String name;

    public Category(String name) {
        this.name = name.trim().toUpperCase();
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Category category = (Category) o;
        return name.equals(category.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
