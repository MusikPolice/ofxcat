package ca.jonathanfritz.ofxcat.exception;

public class CliException extends OfxCatException {

    private static final long serialVersionUID = 1L;

    public CliException(String message) {
        super(message);
    }

    public CliException(String message, Throwable t) {
        super(message, t);
    }
}
