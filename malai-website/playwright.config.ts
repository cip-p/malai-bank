import { defineConfig, devices } from "@playwright/test";

const PORT = 4321;
const LOCAL_BASE_URL = `http://127.0.0.1:${PORT}`;
const REMOTE_BASE_URL = process.env.MALAI_WEBSITE_URL;
const BASE_URL = REMOTE_BASE_URL ?? LOCAL_BASE_URL;

export default defineConfig({
  testDir: "./tests/e2e",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: process.env.CI ? "list" : "list",
  use: {
    baseURL: BASE_URL,
    trace: "on-first-retry",
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
  ...(REMOTE_BASE_URL
    ? {}
    : {
        webServer: {
          command: `npx astro preview --host 127.0.0.1 --port ${PORT}`,
          url: LOCAL_BASE_URL,
          reuseExistingServer: !process.env.CI,
          timeout: 60_000,
        },
      }),
});
