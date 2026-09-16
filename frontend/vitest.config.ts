import { defineConfig } from 'vitest/config';

/**
 * Loaded by the Angular unit-test builder via the `runnerConfig` option.
 *
 * The default 5s per-test timeout is too tight for the first test in a file
 * that renders a component: that render pays the one-off cost of instantiating
 * the Angular Material and module graph, which measured ~7.5s on the CI runner
 * while every later render in the same file took tens of milliseconds. The
 * limit exists to catch hangs, so it is raised rather than removed.
 */
export default defineConfig({
  test: {
    testTimeout: 30_000,
    hookTimeout: 30_000,
  },
});
