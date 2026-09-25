package kaptainwutax.seedcrackerX.api;

/**
 * Compile-only copy of SeedCrackerX's public API (github.com/19MisterX98/SeedcrackerX). Not
 * bundled: SeedCrackerX supplies the real interface, and without it the entrypoint that
 * implements this is never loaded.
 */
public interface SeedCrackerAPI {

    void pushWorldSeed(long seed);
}
