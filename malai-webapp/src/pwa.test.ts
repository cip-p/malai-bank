import { describe, expect, it } from "vitest";
import { readFile, stat } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const PUBLIC = path.join(ROOT, "public");

async function fileExists(p: string): Promise<boolean> {
  try {
    const s = await stat(p);
    return s.isFile();
  } catch {
    return false;
  }
}

describe("PWA install set", () => {
  it("public/manifest.webmanifest exists and matches the spec", async () => {
    const raw = await readFile(
      path.join(PUBLIC, "manifest.webmanifest"),
      "utf8",
    );
    const m = JSON.parse(raw) as {
      name: string;
      short_name: string;
      display: string;
      start_url: string;
      scope: string;
      orientation: string;
      theme_color: string;
      background_color: string;
      icons: { src: string; sizes: string; type: string; purpose?: string }[];
    };

    expect(m.name).toBe("malai");
    expect(m.short_name).toBe("malai");
    expect(m.display).toBe("standalone");
    expect(m.start_url).toBe("/");
    expect(m.scope).toBe("/");
    expect(m.orientation).toBe("portrait-primary");
    expect(/^#[0-9a-f]{6}$/i.test(m.theme_color)).toBe(true);
    expect(/^#[0-9a-f]{6}$/i.test(m.background_color)).toBe(true);

    const byKey = (sizes: string, purpose?: string) =>
      m.icons.find(
        (i) =>
          i.sizes === sizes &&
          (purpose
            ? (i.purpose ?? "").split(/\s+/).includes(purpose)
            : !i.purpose),
      );

    expect(byKey("192x192")).toMatchObject({
      src: "/icon-192.png",
      type: "image/png",
    });
    const maskable = byKey("512x512", "maskable");
    expect(maskable).toBeTruthy();
    expect(maskable!.src).toBe("/icon-512.png");
    expect(maskable!.type).toBe("image/png");
  });

  it("ships favicon.svg, apple-touch-icon-180.png, icon-192.png, icon-512.png, splash.png", async () => {
    const expected = [
      "favicon.svg",
      "apple-touch-icon-180.png",
      "icon-192.png",
      "icon-512.png",
      "splash.png",
    ];
    for (const name of expected) {
      const exists = await fileExists(path.join(PUBLIC, name));
      expect(exists, `missing public/${name}`).toBe(true);
    }
  });

  it("index.html references favicon, apple-touch-icon, manifest, and theme-color", async () => {
    const html = await readFile(path.join(ROOT, "index.html"), "utf8");
    expect(html).toMatch(
      /<link\s+[^>]*rel=["']icon["'][^>]*href=["']\/favicon\.svg["']/,
    );
    expect(html).toMatch(
      /<link\s+[^>]*rel=["']apple-touch-icon["'][^>]*href=["']\/apple-touch-icon-180\.png["']/,
    );
    expect(html).toMatch(
      /<link\s+[^>]*rel=["']manifest["'][^>]*href=["']\/manifest\.webmanifest["']/,
    );
    expect(html).toMatch(
      /<meta\s+[^>]*name=["']theme-color["'][^>]*content=["']#[0-9a-f]{6}["']/i,
    );
  });

  it("index.html theme-color matches the manifest theme_color", async () => {
    const html = await readFile(path.join(ROOT, "index.html"), "utf8");
    const m = JSON.parse(
      await readFile(path.join(PUBLIC, "manifest.webmanifest"), "utf8"),
    ) as {
      theme_color: string;
    };
    const match = html.match(
      /<meta\s+[^>]*name=["']theme-color["'][^>]*content=["'](#[0-9a-f]{6})["']/i,
    );
    expect(match).not.toBeNull();
    expect(match![1].toLowerCase()).toBe(m.theme_color.toLowerCase());
  });

  it("src/main.tsx does not register a service worker", async () => {
    const main = await readFile(path.join(ROOT, "src", "main.tsx"), "utf8");
    expect(main).not.toMatch(/serviceWorker\s*\.\s*register/);
    expect(main).not.toMatch(/registerSW/);
    expect(main).not.toMatch(/workbox/i);
  });
});
