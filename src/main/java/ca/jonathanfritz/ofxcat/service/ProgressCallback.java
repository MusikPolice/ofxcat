package ca.jonathanfritz.ofxcat.service;

/**
 * Callback interface for reporting the progress of a long-running operation.
 */
@FunctionalInterface
public interface ProgressCallback {

    /**
     * Called to report progress.
     *
     * @param current the number of items processed so far
     * @param total the total number of items to process
     */
    void onProgress(int current, int total);

    /**
     * A no-op callback that ignores progress updates.
     */
    ProgressCallback NOOP = (current, total) -> {};
}
