import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import { describe, expect, it } from "vitest";

const __dirname = dirname(fileURLToPath(import.meta.url));
const CSS = readFileSync(join(__dirname, "themes.css"), "utf8");

type RGB = { r: number; g: number; b: number };

function srgbCompandLinear(v: number): number {
  return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
}

function oklchToRgb(L: number, C: number, hDeg: number): RGB {
  const h = (hDeg * Math.PI) / 180;
  const a = Math.cos(h) * C;
  const b = Math.sin(h) * C;

  const l_ = L + 0.3963377774 * a + 0.2158037573 * b;
  const m_ = L - 0.1055613458 * a - 0.0638541728 * b;
  const s_ = L - 0.0894841775 * a - 1.291485548 * b;

  const l = l_ ** 3;
  const m = m_ ** 3;
  const s = s_ ** 3;

  let r = +4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s;
  let g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s;
  let bl = -0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s;

  r = Math.max(0, Math.min(1, r));
  g = Math.max(0, Math.min(1, g));
  bl = Math.max(0, Math.min(1, bl));

  return { r, g, b: bl };
}

function relativeLuminance({ r, g, b }: RGB): number {
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function srgbEncode(v: number): number {
  return v <= 0.0031308 ? v * 12.92 : 1.055 * Math.pow(v, 1 / 2.4) - 0.055;
}

function composite(top: RGB, alpha: number, under: RGB): RGB {
  const mix = (t: number, u: number) =>
    srgbCompandLinear(alpha * srgbEncode(t) + (1 - alpha) * srgbEncode(u));
  return {
    r: mix(top.r, under.r),
    g: mix(top.g, under.g),
    b: mix(top.b, under.b),
  };
}

function contrastRatio(fg: RGB, bg: RGB): number {
  const l1 = relativeLuminance(fg);
  const l2 = relativeLuminance(bg);
  const [hi, lo] = l1 >= l2 ? [l1, l2] : [l2, l1];
  return (hi + 0.05) / (lo + 0.05);
}

function parseOklch(value: string): RGB {
  const m = value.match(/oklch\(\s*([\d.]+)\s+([\d.]+)\s+([\d.]+)\s*\)/);
  if (!m) throw new Error(`Cannot parse oklch: ${value}`);
  return oklchToRgb(parseFloat(m[1]), parseFloat(m[2]), parseFloat(m[3]));
}

function extractBlock(selectorAlternates: string[]): string {
  const joined = selectorAlternates
    .map((s) => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"))
    .join("(?:,\\s*[^{]+)?\\s*");
  const re = new RegExp(`${joined}\\s*\\{([^}]*)\\}`);
  const match = CSS.match(re);
  if (!match) throw new Error(`Block not found: ${selectorAlternates[0]}`);
  return match[1];
}

function tokens(block: string): Map<string, string> {
  const map = new Map<string, string>();
  const re = /(--[a-z-]+):\s*([^;]+);/g;
  let m: RegExpExecArray | null;
  while ((m = re.exec(block)) !== null) {
    map.set(m[1], m[2].trim());
  }
  return map;
}

type Pair = {
  fg: string;
  bg: string;
  min: number;
  label: string;
  tint?: { of: string; alpha: number };
};

const TEXT_PAIRS: Pair[] = [
  { fg: "--foreground", bg: "--background", min: 4.5, label: "fg/bg" },
  {
    fg: "--card-foreground",
    bg: "--card",
    min: 4.5,
    label: "card-fg/card",
  },
  {
    fg: "--popover-foreground",
    bg: "--popover",
    min: 4.5,
    label: "popover-fg/popover",
  },
  {
    fg: "--primary-foreground",
    bg: "--primary",
    min: 4.5,
    label: "primary-fg/primary",
  },
  {
    fg: "--secondary-foreground",
    bg: "--secondary",
    min: 4.5,
    label: "secondary-fg/secondary",
  },
  {
    fg: "--accent-foreground",
    bg: "--accent",
    min: 4.5,
    label: "accent-fg/accent",
  },
  {
    fg: "--destructive-foreground",
    bg: "--destructive",
    min: 4.5,
    label: "destructive-fg/destructive",
  },
  {
    fg: "--muted-foreground",
    bg: "--background",
    min: 4.5,
    label: "muted-fg/bg",
  },
  {
    fg: "--muted-foreground",
    bg: "--muted",
    min: 4.5,
    label: "muted-fg/muted",
  },
  { fg: "--primary", bg: "--card", min: 4.5, label: "primary/card" },
  { fg: "--destructive", bg: "--card", min: 4.5, label: "destructive/card" },
  { fg: "--positive", bg: "--card", min: 4.5, label: "positive/card" },
  {
    fg: "--positive",
    bg: "--background",
    min: 4.5,
    label: "positive/bg",
  },
  {
    fg: "--destructive",
    bg: "--card",
    min: 4.5,
    label: "destructive/destructive-10-tint",
    tint: { of: "--destructive", alpha: 0.1 },
  },
  {
    fg: "--positive",
    bg: "--card",
    min: 4.5,
    label: "positive/positive-10-tint",
    tint: { of: "--positive", alpha: 0.1 },
  },
];

const THEMES = ["malai", "halcyon", "linden"] as const;

function blockFor(theme: string, mode: "light" | "dark"): Map<string, string> {
  const block =
    mode === "light"
      ? extractBlock([`[data-theme="${theme}"]`])
      : CSS.match(
          new RegExp(
            `\\[data-theme="${theme}"\\]\\.dark,\\s*\\.dark\\[data-theme="${theme}"\\]\\s*\\{([^}]*)\\}`,
          ),
        );

  if (mode === "light") return tokens(block as string);
  if (!block) throw new Error(`dark block missing for ${theme}`);
  const lightTokens = tokens(extractBlock([`[data-theme="${theme}"]`]));
  const darkTokens = tokens((block as RegExpMatchArray)[1]);
  const merged = new Map(lightTokens);
  for (const [k, v] of darkTokens) merged.set(k, v);
  return merged;
}

describe("themes WCAG AA contrast", () => {
  for (const theme of THEMES) {
    for (const mode of ["light", "dark"] as const) {
      describe(`${theme} (${mode})`, () => {
        const map = blockFor(theme, mode);
        for (const pair of TEXT_PAIRS) {
          it(`${pair.label} meets AA (>= ${pair.min}:1)`, () => {
            const fg = map.get(pair.fg);
            const bg = map.get(pair.bg);
            if (!fg || !bg) {
              throw new Error(
                `Missing token in ${theme}/${mode}: ${pair.fg} or ${pair.bg}`,
              );
            }
            const tint = pair.tint ? map.get(pair.tint.of) : undefined;
            const surface =
              pair.tint && tint
                ? composite(parseOklch(tint), pair.tint.alpha, parseOklch(bg))
                : parseOklch(bg);
            const ratio = contrastRatio(parseOklch(fg), surface);
            expect(ratio).toBeGreaterThanOrEqual(pair.min);
          });
        }
      });
    }
  }
});
