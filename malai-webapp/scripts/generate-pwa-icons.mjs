import { chromium } from "@playwright/test";
import { writeFile, mkdir } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const PUBLIC = path.join(ROOT, "public");

function oklchToHex(L, C, h) {
  const hRad = (h * Math.PI) / 180;
  const a = C * Math.cos(hRad);
  const b = C * Math.sin(hRad);
  const lp = L + 0.3963377774 * a + 0.2158037573 * b;
  const mp = L - 0.1055613458 * a - 0.0638541728 * b;
  const sp = L - 0.0894841775 * a - 1.291485548 * b;
  const ll = lp ** 3;
  const mm = mp ** 3;
  const ss = sp ** 3;
  const lr = 4.0767416621 * ll - 3.3077115913 * mm + 0.2309699292 * ss;
  const lg = -1.2684380046 * ll + 2.6097574011 * mm - 0.3413193965 * ss;
  const lb = -0.0041960863 * ll - 0.7034186147 * mm + 1.707614701 * ss;
  const gamma = (x) => {
    const v = x <= 0.0031308 ? 12.92 * x : 1.055 * Math.pow(x, 1 / 2.4) - 0.055;
    return Math.max(0, Math.min(1, v));
  };
  const toHex = (n) =>
    Math.round(255 * n)
      .toString(16)
      .padStart(2, "0");
  return `#${toHex(gamma(lr))}${toHex(gamma(lg))}${toHex(gamma(lb))}`;
}

const COLORS = {
  primary: oklchToHex(0.46, 0.13, 40),
  background: oklchToHex(0.985, 0.012, 80),
  primaryFg: oklchToHex(0.99, 0.01, 80),
  foreground: oklchToHex(0.205, 0.028, 50),
};

function markGeometry(size, safezone) {
  const c = size / 2;
  const innerR = (size / 2) * (1 - safezone) * 0.78;
  const circleStroke = innerR * 0.11;
  const mStroke = innerR * 0.19;
  const s = innerR * 0.62;
  const pt = (x, y) => `${(c + x * s).toFixed(2)},${(c + y * s).toFixed(2)}`;
  const mPath =
    `M ${pt(-0.62, 0.55)} L ${pt(-0.62, -0.55)} ` +
    `L ${pt(0, 0.0)} L ${pt(0.62, -0.55)} L ${pt(0.62, 0.55)}`;
  return { c, innerR, circleStroke, mStroke, mPath };
}

function faviconSvg() {
  const size = 64;
  const radius = 12;
  const g = markGeometry(size, 0);
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${size} ${size}" width="${size}" height="${size}">
  <rect width="${size}" height="${size}" rx="${radius}" ry="${radius}" fill="${COLORS.primary}" />
  <circle cx="${g.c}" cy="${g.c}" r="${g.innerR.toFixed(2)}" fill="none" stroke="${COLORS.primaryFg}" stroke-width="${g.circleStroke.toFixed(2)}" />
  <path d="${g.mPath}" fill="none" stroke="${COLORS.primaryFg}" stroke-width="${g.mStroke.toFixed(2)}" stroke-linecap="round" stroke-linejoin="round" />
</svg>
`;
}

function appleTouchSvg(size) {
  const g = markGeometry(size, 0);
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${size} ${size}" width="${size}" height="${size}">
  <rect width="${size}" height="${size}" fill="${COLORS.primary}" />
  <circle cx="${g.c}" cy="${g.c}" r="${g.innerR.toFixed(2)}" fill="none" stroke="${COLORS.primaryFg}" stroke-width="${g.circleStroke.toFixed(2)}" />
  <path d="${g.mPath}" fill="none" stroke="${COLORS.primaryFg}" stroke-width="${g.mStroke.toFixed(2)}" stroke-linecap="round" stroke-linejoin="round" />
</svg>
`;
}

function anyIconSvg(size) {
  return appleTouchSvg(size);
}

function maskableIconSvg(size) {
  const safezone = 0.2;
  const g = markGeometry(size, safezone);
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${size} ${size}" width="${size}" height="${size}">
  <rect width="${size}" height="${size}" fill="${COLORS.primary}" />
  <circle cx="${g.c}" cy="${g.c}" r="${g.innerR.toFixed(2)}" fill="none" stroke="${COLORS.primaryFg}" stroke-width="${g.circleStroke.toFixed(2)}" />
  <path d="${g.mPath}" fill="none" stroke="${COLORS.primaryFg}" stroke-width="${g.mStroke.toFixed(2)}" stroke-linecap="round" stroke-linejoin="round" />
</svg>
`;
}

function splashSvg() {
  const w = 1242;
  const h = 2688;
  const cx = w / 2;
  const cy = h / 2;
  const markSize = 320;
  const g = markGeometry(markSize, 0);
  const markX = cx - markSize / 2;
  const markY = cy - markSize / 2 - 60;
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${w} ${h}" width="${w}" height="${h}">
  <rect width="${w}" height="${h}" fill="${COLORS.background}" />
  <g transform="translate(${markX}, ${markY})">
    <rect width="${markSize}" height="${markSize}" rx="56" ry="56" fill="${COLORS.primary}" />
    <circle cx="${g.c}" cy="${g.c}" r="${g.innerR.toFixed(2)}" fill="none" stroke="${COLORS.primaryFg}" stroke-width="${g.circleStroke.toFixed(2)}" />
    <path d="${g.mPath}" fill="none" stroke="${COLORS.primaryFg}" stroke-width="${g.mStroke.toFixed(2)}" stroke-linecap="round" stroke-linejoin="round" />
  </g>
  <text x="${cx}" y="${cy + markSize / 2 + 40}" text-anchor="middle" font-family="Inter, -apple-system, BlinkMacSystemFont, sans-serif" font-size="72" font-weight="700" letter-spacing="-1" fill="${COLORS.foreground}">malai</text>
</svg>
`;
}

function manifest() {
  return JSON.stringify(
    {
      name: "malai",
      short_name: "malai",
      description: "malai — a UK retail bank for the modern era.",
      start_url: "/",
      scope: "/",
      display: "standalone",
      orientation: "portrait-primary",
      theme_color: COLORS.primary,
      background_color: COLORS.background,
      icons: [
        { src: "/icon-192.png", sizes: "192x192", type: "image/png" },
        {
          src: "/icon-512.png",
          sizes: "512x512",
          type: "image/png",
          purpose: "maskable",
        },
      ],
    },
    null,
    2,
  );
}

async function renderToPng(browser, svg, width, height, outPath) {
  const page = await browser.newPage({ viewport: { width, height } });
  await page.setContent(
    `<!doctype html><html><head><style>html,body{margin:0;padding:0;background:transparent}svg{display:block;width:${width}px;height:${height}px}</style></head><body>${svg}</body></html>`,
    { waitUntil: "load" },
  );
  await page.screenshot({
    path: outPath,
    omitBackground: true,
    type: "png",
    fullPage: false,
    clip: { x: 0, y: 0, width, height },
  });
  await page.close();
}

async function main() {
  await mkdir(PUBLIC, { recursive: true });

  await writeFile(path.join(PUBLIC, "favicon.svg"), faviconSvg());
  await writeFile(path.join(PUBLIC, "manifest.webmanifest"), manifest());

  const browser = await chromium.launch();
  try {
    await renderToPng(
      browser,
      appleTouchSvg(180),
      180,
      180,
      path.join(PUBLIC, "apple-touch-icon-180.png"),
    );
    await renderToPng(
      browser,
      anyIconSvg(192),
      192,
      192,
      path.join(PUBLIC, "icon-192.png"),
    );
    await renderToPng(
      browser,
      maskableIconSvg(512),
      512,
      512,
      path.join(PUBLIC, "icon-512.png"),
    );
    await renderToPng(
      browser,
      splashSvg(),
      1242,
      2688,
      path.join(PUBLIC, "splash.png"),
    );
  } finally {
    await browser.close();
  }

  console.log("PWA assets generated in", PUBLIC);
  console.log("theme_color =", COLORS.primary);
  console.log("background_color =", COLORS.background);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
