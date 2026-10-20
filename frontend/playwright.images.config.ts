import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  testMatch: ['image-loading-states.spec.ts', 'authenticated-image-gallery.spec.ts'],
  workers: 1,
  retries: 0,
  reporter: 'list',
  use: {
    baseURL: 'http://127.0.0.1:4182',
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    serviceWorkers: 'block',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'webkit', use: { ...devices['Desktop Safari'] } },
  ],
  webServer: {
    command: 'npm run dev -- --host 127.0.0.1 --port 4182',
    url: 'http://127.0.0.1:4182/e2e/fixtures/image-states.html',
    reuseExistingServer: !process.env.CI,
    timeout: 60_000,
  },
  outputDir: 'test-results/image-states',
})
