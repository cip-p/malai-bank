import { expect, test } from "./fixtures";
import { pageSeo } from "../../src/content/seo";
import { site } from "../../src/content/site";

const INDEXABLE_PAGES = [
  { path: "/", seo: pageSeo.home, crumb: undefined },
  { path: "/operations/", seo: pageSeo.operations, crumb: "Operations" },
  { path: "/executive/", seo: pageSeo.executive, crumb: "Executive" },
  { path: "/architecture/", seo: pageSeo.architecture, crumb: "Architecture" },
  { path: "/security/", seo: pageSeo.security, crumb: "Security" },
  { path: "/about/", seo: pageSeo.about, crumb: "About" },
  {
    path: "/what-this-isnt/",
    seo: pageSeo.whatThisIsnt,
    crumb: "What this isn't",
  },
  { path: "/privacy/", seo: pageSeo.privacy, crumb: "Privacy" },
  { path: "/terms/", seo: pageSeo.terms, crumb: "Terms" },
];

function metaContent(page: import("@playwright/test").Page, selector: string) {
  return page.locator(selector).getAttribute("content");
}

async function graphOf(page: import("@playwright/test").Page) {
  const raw = await page
    .locator('head script[type="application/ld+json"]')
    .textContent();
  expect(raw, "page must carry a JSON-LD block").toBeTruthy();
  return JSON.parse(raw as string);
}

test.describe("robots.txt", () => {
  test("is served, allows crawling and advertises the sitemap", async ({
    request,
  }) => {
    const resp = await request.get("/robots.txt");
    expect(resp.ok()).toBeTruthy();
    const body = await resp.text();
    expect(body).toContain("User-agent: *");
    expect(body).toContain("Allow: /");
    expect(body).toContain("Sitemap: https://malaibank.com/sitemap-index.xml");
  });
});

test.describe("canonical URL shape", () => {
  const SKIPPED = /^(#|mailto:|tel:)/;

  test("every internal link points at the trailing-slash form", async ({
    page,
  }) => {
    const offenders: string[] = [];

    for (const { path } of INDEXABLE_PAGES) {
      await page.goto(path);
      const hrefs = await page
        .locator('a[href^="/"]:not([href^="//"])')
        .evaluateAll((links) =>
          links.map((link) => link.getAttribute("href") as string),
        );

      for (const href of hrefs) {
        if (SKIPPED.test(href)) continue;
        const [route] = href.split(/[?#]/);
        if (route.endsWith("/")) continue;
        if (route.split("/").pop()?.includes(".")) continue;
        offenders.push(`${path} -> ${href}`);
      }
    }

    expect(
      offenders,
      "slashless internal links get crawled as duplicates of the canonical",
    ).toEqual([]);
  });
});

test.describe("per-page metadata", () => {
  for (const { path, seo } of INDEXABLE_PAGES) {
    test(`${path} carries its title, description, canonical and index directive`, async ({
      page,
    }) => {
      await page.goto(path);
      await expect(page).toHaveTitle(seo.title);
      expect(await metaContent(page, 'head meta[name="description"]')).toBe(
        seo.description,
      );

      const canonical = await page
        .locator('head link[rel="canonical"]')
        .getAttribute("href");
      expect(canonical).toBe(new URL(path, "https://malaibank.com").href);

      const robots = await metaContent(page, 'head meta[name="robots"]');
      expect(robots).toContain("index");
      expect(robots).not.toContain("noindex");
    });
  }

  test("titles stay inside the search-result truncation budget", () => {
    for (const { path, seo } of INDEXABLE_PAGES) {
      expect(seo.title.length, `${path} title is too long`).toBeLessThanOrEqual(
        60,
      );
    }
  });

  test("descriptions stay inside the search-result truncation budget", () => {
    for (const { path, seo } of INDEXABLE_PAGES) {
      expect(
        seo.description.length,
        `${path} description is too long`,
      ).toBeLessThanOrEqual(158);
      expect(
        seo.description.length,
        `${path} description is too short`,
      ).toBeGreaterThanOrEqual(70);
    }
  });

  test("every indexable page has a distinct title and description", () => {
    const titles = INDEXABLE_PAGES.map((p) => p.seo.title);
    const descriptions = INDEXABLE_PAGES.map((p) => p.seo.description);
    expect(new Set(titles).size).toBe(titles.length);
    expect(new Set(descriptions).size).toBe(descriptions.length);
  });
});

test.describe("social sharing card", () => {
  test("advertises a raster og:image that actually resolves", async ({
    page,
    request,
  }) => {
    await page.goto("/");
    const image = await metaContent(page, 'head meta[property="og:image"]');
    expect(image).toBe(`https://malaibank.com${site.ogImage}`);
    expect(image).toMatch(/\.png$/);

    const resp = await request.get(site.ogImage);
    expect(resp.ok(), "og:image must resolve").toBeTruthy();
    expect(resp.headers()["content-type"]).toContain("image/png");
  });

  test("declares image dimensions, alt text, locale and site name", async ({
    page,
  }) => {
    await page.goto("/");
    expect(
      await metaContent(page, 'head meta[property="og:image:width"]'),
    ).toBe(String(site.ogImageWidth));
    expect(
      await metaContent(page, 'head meta[property="og:image:height"]'),
    ).toBe(String(site.ogImageHeight));
    expect(await metaContent(page, 'head meta[property="og:image:alt"]')).toBe(
      site.ogImageAlt,
    );
    expect(await metaContent(page, 'head meta[property="og:locale"]')).toBe(
      site.locale,
    );
    expect(await metaContent(page, 'head meta[property="og:site_name"]')).toBe(
      site.brand,
    );
  });

  test("carries a large-image twitter card mirroring the page metadata", async ({
    page,
  }) => {
    await page.goto("/operations/");
    expect(await metaContent(page, 'head meta[name="twitter:card"]')).toBe(
      "summary_large_image",
    );
    expect(await metaContent(page, 'head meta[name="twitter:title"]')).toBe(
      pageSeo.operations.title,
    );
    expect(
      await metaContent(page, 'head meta[name="twitter:description"]'),
    ).toBe(pageSeo.operations.description);
    expect(await metaContent(page, 'head meta[name="twitter:image"]')).toBe(
      `https://malaibank.com${site.ogImage}`,
    );
  });

  test("og:url tracks the page, not the origin", async ({ page }) => {
    await page.goto("/security/");
    expect(await metaContent(page, 'head meta[property="og:url"]')).toBe(
      "https://malaibank.com/security/",
    );
  });
});

test.describe("site icons", () => {
  test("the head links the ico, svg and apple touch icons", async ({
    page,
  }) => {
    await page.goto("/");
    await expect(
      page.locator('head link[rel="icon"][href="/favicon.ico"]'),
    ).toHaveCount(1);
    await expect(
      page.locator('head link[rel="icon"][href="/favicon.svg"]'),
    ).toHaveCount(1);
    await expect(
      page.locator(
        'head link[rel="apple-touch-icon"][href="/apple-touch-icon.png"]',
      ),
    ).toHaveCount(1);
  });

  test("favicon.ico resolves for clients that never read the head", async ({
    request,
  }) => {
    const resp = await request.get("/favicon.ico");
    expect(resp.ok(), "favicon.ico must resolve").toBeTruthy();
    expect(resp.headers()["content-type"]).toContain("icon");
  });

  test("the apple touch icon resolves as a png", async ({ request }) => {
    const resp = await request.get("/apple-touch-icon.png");
    expect(resp.ok(), "apple-touch-icon must resolve").toBeTruthy();
    expect(resp.headers()["content-type"]).toContain("image/png");
  });
});

test.describe("structured data", () => {
  test("home page publishes a valid organisation and website graph", async ({
    page,
  }) => {
    await page.goto("/");
    const graph = await graphOf(page);
    expect(graph["@context"]).toBe("https://schema.org");

    const nodes: Record<string, string>[] = graph["@graph"];
    const organisation = nodes.find((n) => n["@type"] === "Organization");
    const website = nodes.find((n) => n["@type"] === "WebSite");

    expect(organisation).toBeDefined();
    expect(organisation!.name).toBe(site.brand);
    expect(organisation!.url).toBe("https://malaibank.com/");
    expect(organisation!.email).toBe(site.contactEmail);

    expect(website).toBeDefined();
    expect(website!.inLanguage).toBe(site.language);
    expect((website!.publisher as unknown as { "@id": string })["@id"]).toBe(
      organisation!["@id"],
    );
  });

  test("organisation logo is a raster image that resolves", async ({
    page,
    request,
  }) => {
    await page.goto("/");
    const graph = await graphOf(page);
    const organisation = graph["@graph"].find(
      (n: Record<string, string>) => n["@type"] === "Organization",
    );
    const logo = organisation.logo;
    expect(logo["@type"]).toBe("ImageObject");
    expect(logo.url).toMatch(/\.png$/);
    const resp = await request.get(new URL(logo.url).pathname);
    expect(resp.ok(), "organisation logo must resolve").toBeTruthy();
    expect(resp.headers()["content-type"]).toContain("image/png");
  });

  test("never claims to be a regulated financial institution", async ({
    page,
  }) => {
    const bannedTypes = [
      "Bank",
      "BankOrCreditUnion",
      "BankOrChequeCashingService",
      "FinancialService",
      "FinancialProduct",
    ];
    for (const { path } of INDEXABLE_PAGES) {
      await page.goto(path);
      const graph = await graphOf(page);
      const types = graph["@graph"].map(
        (n: Record<string, string>) => n["@type"],
      );
      for (const banned of bannedTypes) {
        expect(types, `${path} must not be typed as ${banned}`).not.toContain(
          banned,
        );
      }
    }
  });

  test("carries the not-a-bank disclosure in machine-readable form", async ({
    page,
  }) => {
    await page.goto("/");
    const graph = await graphOf(page);
    const organisation = graph["@graph"].find(
      (n: Record<string, string>) => n["@type"] === "Organization",
    );
    expect(organisation.disambiguatingDescription).toContain("Not a bank");
    expect(organisation.disambiguatingDescription).toContain(
      "not authorised by the FCA or PRA",
    );
  });

  test("inner pages carry a two-step breadcrumb trail", async ({ page }) => {
    for (const { path, crumb } of INDEXABLE_PAGES) {
      if (!crumb) continue;
      await page.goto(path);
      const graph = await graphOf(page);
      const breadcrumb = graph["@graph"].find(
        (n: Record<string, string>) => n["@type"] === "BreadcrumbList",
      );
      expect(breadcrumb, `${path} must publish a breadcrumb`).toBeDefined();

      const items = breadcrumb.itemListElement;
      expect(items).toHaveLength(2);
      expect(items[0].position).toBe(1);
      expect(items[0].name).toBe("Home");
      expect(items[0].item).toBe("https://malaibank.com/");
      expect(items[1].position).toBe(2);
      expect(items[1].name).toBe(crumb);
      expect(items[1].item).toBe(`https://malaibank.com${path}`);
    }
  });

  test("the home page publishes no breadcrumb", async ({ page }) => {
    await page.goto("/");
    const graph = await graphOf(page);
    const types = graph["@graph"].map(
      (n: Record<string, string>) => n["@type"],
    );
    expect(types).not.toContain("BreadcrumbList");
  });

  test("every page's WebPage node is anchored to its canonical URL", async ({
    page,
  }) => {
    for (const { path } of INDEXABLE_PAGES) {
      await page.goto(path);
      const graph = await graphOf(page);
      const webPage = graph["@graph"].find(
        (n: Record<string, string>) => n["@type"] === "WebPage",
      );
      const canonical = await page
        .locator('head link[rel="canonical"]')
        .getAttribute("href");
      expect(webPage.url).toBe(canonical);
    }
  });
});

test.describe("pages kept out of the index", () => {
  test("the 404 page is noindex and publishes no structured data", async ({
    page,
  }) => {
    await page.goto("/does-not-exist");
    const robots = await metaContent(page, 'head meta[name="robots"]');
    expect(robots).toContain("noindex");
    expect(robots).toContain("nofollow");
    await expect(
      page.locator('head script[type="application/ld+json"]'),
    ).toHaveCount(0);
  });

  test("the sitemap lists every indexable page and nothing else", async ({
    request,
  }) => {
    const index = await (await request.get("/sitemap-index.xml")).text();
    const children = [...index.matchAll(/<loc>([^<]+)<\/loc>/g)].map(
      (m) => new URL(m[1]).pathname,
    );

    const listed = new Set<string>();
    for (const child of children) {
      const xml = await (await request.get(child)).text();
      for (const m of xml.matchAll(/<loc>([^<]+)<\/loc>/g)) {
        listed.add(new URL(m[1]).pathname);
      }
    }

    for (const { path } of INDEXABLE_PAGES) {
      expect([...listed], `sitemap must list ${path}`).toContain(path);
    }
    for (const path of [...listed]) {
      expect(path, "sitemap must not list the 404").not.toContain("404");
    }
  });
});
