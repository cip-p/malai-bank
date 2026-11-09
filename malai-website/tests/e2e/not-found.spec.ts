import { test, expect } from "./fixtures";

test.describe("404 page", () => {
  test("unknown URLs return a 404 status", async ({ page }) => {
    const response = await page.goto("/this-page-does-not-exist");
    expect(response?.status()).toBe(404);
  });

  test("renders the not-found heading and a link back home", async ({
    page,
  }) => {
    await page.goto("/this-page-does-not-exist");
    const section = page.getByTestId("not-found");
    await expect(section.getByRole("heading", { level: 1 })).toHaveText(
      "This page doesn’t exist.",
    );
    const home = section.getByTestId("not-found-home-link");
    await expect(home).toHaveAttribute("href", "/");
  });

  test("serves the site chrome around the 404 body", async ({ page }) => {
    await page.goto("/this-page-does-not-exist");
    await expect(page.getByTestId("footer")).toBeVisible();
  });
});
