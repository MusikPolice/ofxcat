package ca.jonathanfritz.ofxcat.exception;

public class CliException extends OfxCatException {

    public CliException(String message) {
        super(message);
    }

    public CliException(String message, Throwable t) {
        super(message, t);
    }
}
