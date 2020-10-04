package ca.jonathanfritz.ofxcat.datastore.utils;

public interface Entity {

    /**
     * Every {@link Entity} must have an auto-incremented primary key called id for the helper logic in
     * {@link DatabaseTransaction} to function correctly.
     * @return the unique id of this object, or null if the object has not yet been persisted to the database
     */
    Long getId();
}
