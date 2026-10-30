import { defineConfig, devices } from '@playwright/test'

const PREVIEW_PORT = 4173
const BUILD_TIMEOUT_MS = 120_000

export default defineConfig({
  testDir: 'e2e',
  reporter: 'list',
  use: {
    baseURL: `http://localhost:${PREVIEW_PORT}`,
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'firefox', use: { ...devices['Desktop Firefox'] } },
    { name: 'webkit', use: { ...devices['Desktop Safari'] } },
  ],
  webServer: {
    command: `npx vite build && npx vite preview --port ${PREVIEW_PORT} --strictPort`,
    url: `http://localhost:${PREVIEW_PORT}`,
    timeout: BUILD_TIMEOUT_MS,
    reuseExistingServer: false,
  },
})
