import { test, expect } from "./fixtures";
import { hero } from "../../src/content/hero";
import { nav } from "../../src/content/nav";

async function scrollTo(page: import("@playwright/test").Page, y: number) {
  await page.evaluate(
    (top) =>
      window.scrollTo({ top, left: 0, behavior: "instant" as ScrollBehavior }),
    y,
  );
}

test.describe("site header", () => {
  test("renders sticky from page load on the home page", async ({ page }) => {
    await page.goto("/");
    const navEl = page.getByRole("navigation", { name: "Primary" });
    const position = await navEl.evaluate(
      (el) => getComputedStyle(el.parentElement as Element).position,
    );
    expect(position).toBe("sticky");
  });

  test("stays pinned to the top after scrolling", async ({ page }) => {
    await page.goto("/");
    const header = page.getByTestId("site-header");
    await scrollTo(page, 1500);
    const box = await header.boundingBox();
    expect(box?.y ?? -1).toBe(0);
  });

  test("renders the header on every standalone page", async ({ page }) => {
    for (const path of [
      "/operations/",
      "/executive/",
      "/architecture/",
      "/security/",
      "/about/",
      "/what-this-isnt/",
    ]) {
      await page.goto(path);
      await expect(page.getByTestId("site-header")).toBeVisible();
    }
  });

  test("primary nav links carry the expected hrefs", async ({ page }) => {
    await page.goto("/");
    const navEl = page.getByRole("navigation", { name: "Primary" });
    for (const anchor of nav.anchors) {
      const link = navEl.getByRole("link", { name: anchor.label });
      await expect(link).toHaveAttribute("href", anchor.href);
    }
  });

  test("primary CTA href matches the hero's primary CTA href", async ({
    page,
  }) => {
    await page.goto("/");
    const navEl = page.getByRole("navigation", { name: "Primary" });
    const navCta = navEl.getByRole("link", { name: nav.primaryCta.label });
    await expect(navCta).toHaveAttribute("href", hero.primaryCta.href);
  });
});

test.describe("site header — mobile menu", () => {
  test.use({ viewport: { width: 390, height: 844 } });

  test("hides the desktop nav and shows the hamburger toggle", async ({
    page,
  }) => {
    await page.goto("/");
    await expect(page.getByRole("button", { name: "Open menu" })).toBeVisible();
    await expect(page.locator("#mobile-nav-panel")).toBeHidden();
  });

  test("toggles the mobile panel open and closed", async ({ page }) => {
    await page.goto("/");
    const toggle = page.getByRole("button", { name: "Open menu" });
    await toggle.click();
    await expect(
      page.getByRole("button", { name: "Close menu" }),
    ).toHaveAttribute("aria-expanded", "true");
    await expect(page.locator("#mobile-nav-panel")).toBeVisible();

    await page.getByRole("button", { name: "Close menu" }).click();
    await expect(
      page.getByRole("button", { name: "Open menu" }),
    ).toHaveAttribute("aria-expanded", "false");
    await expect(page.locator("#mobile-nav-panel")).toBeHidden();
  });

  test("Escape closes the open panel", async ({ page }) => {
    await page.goto("/");
    await page.getByRole("button", { name: "Open menu" }).click();
    await expect(page.locator("#mobile-nav-panel")).toBeVisible();
    await page.keyboard.press("Escape");
    await expect(page.locator("#mobile-nav-panel")).toBeHidden();
  });

  test("Escape from a menu link returns focus to the toggle", async ({
    page,
  }) => {
    await page.goto("/");
    await page.getByRole("button", { name: "Open menu" }).click();
    await page.keyboard.press("Tab");
    await expect(
      page.locator("#mobile-nav-panel").getByRole("link").first(),
    ).toBeFocused();
    await page.keyboard.press("Escape");
    await expect(page.locator("#mobile-nav-panel")).toBeHidden();
    await expect(page.getByRole("button", { name: "Open menu" })).toBeFocused();
  });

  test("mobile panel exposes every primary nav anchor", async ({ page }) => {
    await page.goto("/");
    await page.getByRole("button", { name: "Open menu" }).click();
    const panel = page.locator("#mobile-nav-panel");
    for (const anchor of nav.anchors) {
      const link = panel.getByRole("link", { name: anchor.label });
      await expect(link).toHaveAttribute("href", anchor.href);
    }
  });
});
