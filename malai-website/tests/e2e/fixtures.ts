import { test as base, expect } from "@playwright/test";

export const test = base.extend<{ blockAnalytics: void }>({
  blockAnalytics: [
    async ({ page }, use) => {
      await page.route("https://plausible.io/**", (route) =>
        route.fulfill({ status: 204, body: "" }),
      );
      await use();
    },
    { auto: true },
  ],
});

export { expect };
