import AxeBuilder from "@axe-core/playwright";
import { test, expect } from "./fixtures";

const routes = [
  "/",
  "/operations/",
  "/executive/",
  "/architecture/",
  "/security/",
  "/about/",
  "/what-this-isnt/",
  "/privacy/",
  "/terms/",
  "/404",
] as const;

const viewports = [
  { name: "desktop", width: 1440, height: 900 },
  { name: "mobile", width: 390, height: 844 },
] as const;

const tags = [
  "wcag2a",
  "wcag2aa",
  "wcag21a",
  "wcag21aa",
  "wcag22aa",
  "best-practice",
];

for (const route of routes) {
  test.describe(`${route} — accessibility`, () => {
    for (const viewport of viewports) {
      test(`zero axe violations at ${viewport.name} (${viewport.width}x${viewport.height})`, async ({
        page,
      }) => {
        await page.setViewportSize({
          width: viewport.width,
          height: viewport.height,
        });
        await page.goto(route);

        const results = await new AxeBuilder({ page }).withTags(tags).analyze();

        expect(
          results.violations,
          results.violations
            .map(
              (v) =>
                `${v.id} (${v.impact}): ${v.help} — ${v.nodes.length} node(s)`,
            )
            .join("\n"),
        ).toEqual([]);
      });
    }
  });
}
