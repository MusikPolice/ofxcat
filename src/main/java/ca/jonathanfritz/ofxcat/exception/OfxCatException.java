package ca.jonathanfritz.ofxcat.exception;

public class OfxCatException extends Exception {

    private static final long serialVersionUID = 1L;

    public OfxCatException(String message) {
        super(message);
    }

    public OfxCatException(String message, Throwable t) {
        super(message, t);
    }
}
