package ca.jonathanfritz.ofxcat.service;

/**
 * Callback interface for reporting migration progress.
 * Used by TokenMigrationService to report progress to the UI layer.
 */
@FunctionalInterface
public interface MigrationProgressCallback {

    /**
     * Called to report migration progress.
     *
     * @param current the number of transactions processed so far
     * @param total the total number of transactions to process
     */
    void onProgress(int current, int total);

    /**
     * A no-op callback that ignores progress updates.
     */
    MigrationProgressCallback NOOP = (current, total) -> {};
}
