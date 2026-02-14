package ca.jonathanfritz.ofxcat.cli;

import jakarta.inject.Inject;
import java.util.List;
import org.beryx.textio.TextIO;

/**
 * A light wrapper around {@link TextIO} that makes it possible to mock in CLI tests
 */
public class TextIOWrapper {

    private final TextIO textIO;

    @Inject
    public TextIOWrapper(TextIO textIO) {
        this.textIO = textIO;
    }

    public String promptChooseString(String prompt, List<String> choices) {
        return textIO.newStringInputReader().withNumberedPossibleValues(choices).read(prompt);
    }

    public boolean promptYesNo(String prompt) {
        return textIO.newBooleanInputReader().withDefaultValue(true).read(prompt);
    }
}
