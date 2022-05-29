package ca.jonathanfritz.ofxcat.datastore.dto;

import java.util.Objects;
import java.util.stream.Stream;

public record Transfer(Transaction source,
                       Transaction sink) {

    public Stream<Transaction> stream() {
        return Stream.of(source, sink);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Transfer transfer = (Transfer) o;
        return Objects.equals(source, transfer.source) && Objects.equals(sink, transfer.sink);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, sink);
    }
}
