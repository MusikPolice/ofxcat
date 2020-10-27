package ca.jonathanfritz.ofxcat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class OfxCatTest {

    @Test
    void constructorRunsFlywayMigrationTest() {
        final Flyway mockFlyway = Mockito.mock(Flyway.class);
        when(mockFlyway.migrate()).thenReturn(1);

        new OfxCat(mockFlyway, null);

        Mockito.verify(mockFlyway, times(1)).migrate();
        Mockito.verifyNoMoreInteractions(mockFlyway);
    }

    // TODO: tests for CLI params
}