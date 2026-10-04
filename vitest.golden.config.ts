import { defineConfig } from 'vitest/config';

// Runs the web search over scripts/fixtures/parity_queries.json against the built data and writes
// the results for the Android parity test (android/db: GoldenParityTest). Not part of `npm test`.
//   npx vitest run --config vitest.golden.config.ts
export default defineConfig({
  test: {
    include: ['scripts/golden.test.ts'],
    testTimeout: 600_000,
  },
});
