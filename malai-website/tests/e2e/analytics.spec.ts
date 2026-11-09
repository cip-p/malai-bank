import { test, expect } from "./fixtures";

declare global {
  interface Window {
    plausible?: (...args: unknown[]) => void;
  }
}

const SCRIPT_SRC = "https://plausible.io/js/pa-oTcMMe0Sxuw3RYC-nqU6D.js";

const PAGES = [
  "/",
  "/operations",
  "/executive",
  "/architecture",
  "/security",
  "/about",
  "/what-this-isnt",
  "/privacy",
  "/terms",
];

test.describe("plausible analytics", () => {
  test("loads the tracker asynchronously from the document head", async ({
    page,
  }) => {
    await page.goto("/");
    const script = page.locator(`head > script[src="${SCRIPT_SRC}"]`);
    await expect(script).toHaveCount(1);
    await expect(script).toHaveAttribute("async", "");
  });

  test("initialises the plausible queue before the tracker arrives", async ({
    page,
  }) => {
    await page.goto("/");
    const queued = await page.evaluate(() => typeof window.plausible);
    expect(queued).toBe("function");
  });

  test("serves the queue stub from this origin, ahead of the tracker", async ({
    page,
  }) => {
    await page.goto("/");
    const order = await page.evaluate(() =>
      [...document.querySelectorAll("head script[src]")].map(
        (el) => el.getAttribute("src") ?? "",
      ),
    );
    const stub = order.indexOf("/plausible-init.js");
    const tracker = order.indexOf(SCRIPT_SRC);
    expect(stub).toBeGreaterThanOrEqual(0);
    expect(stub).toBeLessThan(tracker);

    const response = await page.request.get("/plausible-init.js");
    expect(response.status()).toBe(200);
  });

  test("is present on every standalone page", async ({ page }) => {
    for (const path of PAGES) {
      await page.goto(path);
      await expect(
        page.locator(`head > script[src="${SCRIPT_SRC}"]`),
      ).toHaveCount(1);
    }
  });
});
