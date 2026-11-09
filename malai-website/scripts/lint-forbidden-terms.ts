import { readFileSync, readdirSync } from "node:fs";
import { extname, join, relative } from "node:path";
import { findForbidden } from "../src/lib/forbidden-terms.ts";

const ROOT = process.cwd();
const SCAN_DIRS = ["src"];
const EXTENSIONS = new Set([".astro", ".ts", ".tsx", ".md", ".mdx"]);

const violations: Array<{ path: string; hits: string[] }> = [];

for (const dir of SCAN_DIRS) {
  const entries = readdirSync(join(ROOT, dir), {
    recursive: true,
    withFileTypes: true,
  });
  for (const entry of entries) {
    if (!entry.isFile()) continue;
    if (!EXTENSIONS.has(extname(entry.name))) continue;
    const abs = join(entry.parentPath, entry.name);
    const rel = relative(ROOT, abs);
    const text = readFileSync(abs, "utf8");
    const hits = findForbidden(text);
    if (hits.length > 0) violations.push({ path: rel, hits });
  }
}

if (violations.length > 0) {
  console.error("Forbidden terms found in source files:");
  for (const { path, hits } of violations) {
    console.error(`  ${path}: ${hits.join(", ")}`);
  }
  console.error(
    "\nUpdate the source or, if the term is intentional, add an exemption discussion before merging.",
  );
  process.exit(1);
}

console.log("No forbidden terms found in scanned sources.");
