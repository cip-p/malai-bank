import { expect, test } from "./fixtures";

const PRODUCTION_CSP = [
  "default-src 'self';",
  "script-src 'self' https://plausible.io;",
  "style-src 'self' 'unsafe-inline' fonts.googleapis.com;",
  "font-src 'self' fonts.gstatic.com data:;",
  "img-src 'self' data: https:;",
  "connect-src 'self' https://plausible.io;",
  "frame-ancestors 'none';",
  "base-uri 'self';",
  "form-action 'self';",
].join(" ");

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

interface Violation {
  directive: string;
  blocked: string;
}

declare global {
  interface Window {
    __cspViolations?: Violation[];
  }
}

test.describe("the deployed content security policy", () => {
  test.beforeEach(async ({ page, baseURL }) => {
    await page.route(`${baseURL}/**`, async (route, request) => {
      if (request.resourceType() !== "document") return route.fallback();
      const response = await route.fetch();
      const body = await response.body();
      await route.fulfill({
        status: response.status(),
        headers: {
          ...response.headers(),
          "content-security-policy": PRODUCTION_CSP,
        },
        body,
      });
    });

    await page.addInitScript(() => {
      window.__cspViolations = [];
      document.addEventListener("securitypolicyviolation", (event) => {
        window.__cspViolations?.push({
          directive: event.violatedDirective,
          blocked: event.blockedURI,
        });
      });
    });
  });

  test.afterEach(async ({ page }) => {
    await page.unrouteAll({ behavior: "ignoreErrors" });
  });

  for (const path of PAGES) {
    test(`${path} loads without tripping the policy`, async ({ page }) => {
      await page.goto(path, { waitUntil: "networkidle" });
      const violations = await page.evaluate(
        () => window.__cspViolations ?? [],
      );
      expect(
        violations,
        `${path} violates the production CSP: ${JSON.stringify(violations)}`,
      ).toEqual([]);
    });
  }

  test("the 404 page loads without tripping the policy", async ({ page }) => {
    await page.goto("/does-not-exist", { waitUntil: "networkidle" });
    const violations = await page.evaluate(() => window.__cspViolations ?? []);
    expect(violations).toEqual([]);
  });

  test("no page ships an executable inline script", async ({ page }) => {
    for (const path of PAGES) {
      await page.goto(path);
      const inlineScripts = await page.evaluate(() =>
        [...document.querySelectorAll("script")]
          .filter((el) => !el.src && el.type !== "application/ld+json")
          .map((el) => el.textContent?.slice(0, 60) ?? ""),
      );
      expect(
        inlineScripts,
        `${path} ships an inline script that 'script-src self' will block`,
      ).toEqual([]);
    }
  });

  test("the mobile menu script is served from this origin", async ({
    page,
  }) => {
    await page.goto("/");
    const sources = await page.evaluate(() =>
      [...document.querySelectorAll("script[src]")].map((el) =>
        el.getAttribute("src"),
      ),
    );
    expect(sources.some((src) => src?.startsWith("/_astro/"))).toBe(true);
  });

  test("the structured data block survives the policy", async ({ page }) => {
    await page.goto("/operations", { waitUntil: "networkidle" });
    const nodes = await page.evaluate(() => {
      const el = document.querySelector('script[type="application/ld+json"]');
      return el ? JSON.parse(el.textContent ?? "{}")["@graph"]?.length : 0;
    });
    expect(nodes).toBeGreaterThan(0);
    const violations = await page.evaluate(() => window.__cspViolations ?? []);
    expect(violations).toEqual([]);
  });

  test("the analytics queue stub still initialises", async ({ page }) => {
    await page.goto("/", { waitUntil: "networkidle" });
    expect(await page.evaluate(() => typeof window.plausible)).toBe("function");
    const violations = await page.evaluate(() => window.__cspViolations ?? []);
    expect(violations).toEqual([]);
  });
});
