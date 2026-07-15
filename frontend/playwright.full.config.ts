import { defineConfig, devices } from '@playwright/test'

const baseURL = process.env.E2E_BASE_URL
if (!baseURL) {
  throw new Error('E2E_BASE_URL is required for test:e2e:full')
}

export default defineConfig({
  testDir: './e2e/full',
  timeout: 45_000,
  reporter: [['html', { open: 'never' }], ['list']],
  use: {
    baseURL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [{ name: 'chromium-full', use: { ...devices['Desktop Chrome'] } }],
  outputDir: 'test-results-full',
})
