import { expect, test } from "./fixtures";
import { findForbidden } from "../../src/lib/forbidden-terms";

async function loadSitemapPaths(
  request: import("@playwright/test").APIRequestContext,
): Promise<string[]> {
  const indexResp = await request.get("/sitemap-index.xml");
  expect(indexResp.ok(), "sitemap-index.xml must be reachable").toBeTruthy();
  const indexXml = await indexResp.text();
  const sitemapUrls = [...indexXml.matchAll(/<loc>([^<]+)<\/loc>/g)].map(
    (m) => m[1],
  );
  expect(
    sitemapUrls.length,
    "sitemap index must list child sitemaps",
  ).toBeGreaterThan(0);

  const paths = new Set<string>();
  for (const sitemapUrl of sitemapUrls) {
    const childPath = new URL(sitemapUrl).pathname;
    const childResp = await request.get(childPath);
    expect(childResp.ok(), `${childPath} must be reachable`).toBeTruthy();
    const childXml = await childResp.text();
    for (const m of childXml.matchAll(/<loc>([^<]+)<\/loc>/g)) {
      paths.add(new URL(m[1]).pathname);
    }
  }

  return [...paths];
}

test.describe("rendered HTML contains no forbidden terms", () => {
  test("every page in the sitemap is clean", async ({ page, request }) => {
    const paths = await loadSitemapPaths(request);
    expect(
      paths.length,
      "sitemap must enumerate at least one page",
    ).toBeGreaterThan(0);

    for (const path of paths) {
      await page.goto(path);
      const html = await page.content();
      const hits = findForbidden(html);
      expect(
        hits,
        `${path} contains forbidden terms: ${hits.join(", ")}`,
      ).toEqual([]);
    }
  });
});
