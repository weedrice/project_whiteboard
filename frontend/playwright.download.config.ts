import { fileURLToPath } from 'node:url'
import { defineConfig, devices } from '@playwright/test'

const frontendRoot = fileURLToPath(new URL('./', import.meta.url))

export default defineConfig({
  testDir: './e2e',
  testMatch: ['protected-file-download.spec.ts'],
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 2 : 0,
  reporter: process.env.CI
    ? [['html', { open: 'never', outputFolder: 'playwright-report-download' }], ['list']]
    : 'list',
  use: {
    baseURL: 'http://127.0.0.1:4173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    serviceWorkers: 'block',
  },
  projects: [
    { name: 'chromium-download', use: { ...devices['Desktop Chrome'] } },
    { name: 'firefox-download', use: { ...devices['Desktop Firefox'] } },
    { name: 'webkit-download', use: { ...devices['Desktop Safari'] } },
  ],
  webServer: {
    command: 'npm run preview -- --host 127.0.0.1',
    cwd: frontendRoot,
    url: 'http://127.0.0.1:4173',
    reuseExistingServer: !process.env.CI,
    timeout: 60_000,
  },
  outputDir: 'test-results/download-cross-browser',
})
