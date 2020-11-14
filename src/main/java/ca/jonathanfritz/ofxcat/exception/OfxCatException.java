package ca.jonathanfritz.ofxcat.exception;

public class OfxCatException extends Exception {

    public OfxCatException(String message) {
        super(message);
    }

    public OfxCatException(String message, Throwable t) {
        super(message, t);
    }
}
