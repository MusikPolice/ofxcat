package ca.jonathanfritz.ofxcat.datastore.dto;

import ca.jonathanfritz.ofxcat.datastore.utils.Entity;
import java.util.Objects;

public class Transfer implements Entity {

    private final Long id;
    private final CategorizedTransaction source;
    private final CategorizedTransaction sink;

    public Transfer(CategorizedTransaction source, CategorizedTransaction sink) {
        this(null, source, sink);
    }

    public Transfer(Long id, CategorizedTransaction source, CategorizedTransaction sink) {
        this.id = id;
        this.source = source;
        this.sink = sink;
    }

    @Override
    public Long getId() {
        return id;
    }

    public CategorizedTransaction getSource() {
        return source;
    }

    public CategorizedTransaction getSink() {
        return sink;
    }

    @Override
    public String toString() {
        return "Transfer{" + "id=" + id + ", source=" + source + ", sink=" + sink + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Transfer transfer = (Transfer) o;
        return Objects.equals(id, transfer.id)
                && Objects.equals(source, transfer.source)
                && Objects.equals(sink, transfer.sink);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, source, sink);
    }
}
