package ca.jonathanfritz.ofxcat.cli;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import java.io.IOException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

public class CLIModule extends AbstractModule {

    @Provides
    Terminal provideTerminal() throws IOException {
        return TerminalBuilder.builder().system(true).build();
    }

    @Provides
    LineReader provideLineReader(Terminal terminal) {
        return LineReaderBuilder.builder().terminal(terminal).build();
    }
}
