package ca.jonathanfritz.ofxcat.dao;

import com.google.inject.Inject;

import java.sql.Connection;

public class DescriptionCategoryDao {

    private final Connection connection;

    @Inject
    public DescriptionCategoryDao(Connection connection) {
        this.connection = connection;
    }

    // TODO: implement methods and things
}
