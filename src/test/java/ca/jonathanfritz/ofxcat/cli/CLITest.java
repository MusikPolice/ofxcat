package ca.jonathanfritz.ofxcat.cli;

import com.google.inject.Inject;
import org.beryx.textio.TextIO;

class CLITest {

    private final TextIO textIO;

    @Inject
    CLITest(TextIO textIO) {
        this.textIO = textIO;
        this.textIO.getTextTerminal()
    }


}