package ca.jonathanfritz.ofxcat.cli;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;

public class CLIModule extends AbstractModule {

    @Provides
    TextIO provideTextIO() {
        return TextIoFactory.getTextIO();
    }
}
