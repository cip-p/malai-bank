import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import { describe, expect, it } from "vitest";

const __dirname = dirname(fileURLToPath(import.meta.url));
const CSS = readFileSync(join(__dirname, "themes.css"), "utf8");

const REQUIRED_TOKENS = [
  "--background",
  "--foreground",
  "--card",
  "--card-foreground",
  "--popover",
  "--popover-foreground",
  "--primary",
  "--primary-foreground",
  "--secondary",
  "--secondary-foreground",
  "--muted",
  "--muted-foreground",
  "--accent",
  "--accent-foreground",
  "--destructive",
  "--destructive-foreground",
  "--border",
  "--input",
  "--ring",
];

const REQUIRED_LIGHT_ONLY = ["--radius"];

function extractBlock(selector: string): string {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const re = new RegExp(`${escaped}\\s*\\{([^}]*)\\}`);
  const match = CSS.match(re);
  if (!match) throw new Error(`Block not found: ${selector}`);
  return match[1];
}

function extractCombinedBlock(...selectors: string[]): string {
  const joined = selectors
    .map((s) => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"))
    .join(",\\s*");
  const re = new RegExp(`${joined}\\s*\\{([^}]*)\\}`);
  const match = CSS.match(re);
  if (!match) throw new Error(`Block not found: ${selectors.join(", ")}`);
  return match[1];
}

const THEMES = ["malai", "halcyon", "linden"] as const;

describe("themes.css", () => {
  for (const theme of THEMES) {
    describe(`[data-theme="${theme}"]`, () => {
      it("defines every shadcn token in light mode", () => {
        const block = extractBlock(`[data-theme="${theme}"]`);
        for (const token of [...REQUIRED_TOKENS, ...REQUIRED_LIGHT_ONLY]) {
          expect(block).toContain(`${token}:`);
        }
      });

      it("defines every shadcn token in dark mode", () => {
        const block = extractCombinedBlock(
          `[data-theme="${theme}"].dark`,
          `.dark[data-theme="${theme}"]`,
        );
        for (const token of REQUIRED_TOKENS) {
          expect(block).toContain(`${token}:`);
        }
      });
    });
  }

  it("each theme keeps its own --radius value (rebrand includes shape, not just colour)", () => {
    const radii = new Set<string>();
    for (const theme of THEMES) {
      const block = extractBlock(`[data-theme="${theme}"]`);
      const match = block.match(/--radius:\s*([^;]+);/);
      if (!match) throw new Error(`No --radius in ${theme}`);
      radii.add(match[1].trim());
    }
    expect(radii.size).toBe(THEMES.length);
  });
});
