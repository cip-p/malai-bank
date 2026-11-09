import { execFileSync } from "node:child_process";
import { readdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

// Regenerates the API client from ./openapi.yaml and fails if the committed client differs.
// `prebuild` regenerates before every build, so without this check CI would silently rewrite
// a stale client and build green over the mismatch.
//
// Non-destructive: whatever the outcome, the working tree is restored to the committed
// client before exiting.

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const GENERATED = path.join(ROOT, "src", "api", "generated");

function snapshot(dir) {
  const files = new Map();
  for (const entry of readdirSync(dir, {
    recursive: true,
    withFileTypes: true,
  })) {
    if (!entry.isFile()) continue;
    const absolute = path.join(entry.parentPath ?? entry.path, entry.name);
    files.set(path.relative(dir, absolute), readFileSync(absolute, "utf8"));
  }
  return files;
}

function restore(dir, files, extras) {
  for (const name of extras) rmSync(path.join(dir, name), { force: true });
  for (const [name, contents] of files)
    writeFileSync(path.join(dir, name), contents);
}

function regenerate() {
  try {
    execFileSync("npx", ["openapi-ts"], {
      cwd: ROOT,
      encoding: "utf8",
      stdio: ["ignore", "pipe", "pipe"],
    });
    return { ok: true, output: "" };
  } catch (error) {
    return {
      ok: false,
      output: `${error.stdout ?? ""}${error.stderr ?? ""}`,
    };
  }
}

function discardCrashLogs() {
  for (const name of readdirSync(ROOT)) {
    if (name.startsWith("openapi-ts-error-") && name.endsWith(".log")) {
      rmSync(path.join(ROOT, name), { force: true });
    }
  }
}

const before = snapshot(GENERATED);
const generation = regenerate();

if (!generation.ok) {
  discardCrashLogs();
  restore(GENERATED, before, []);
  console.error("");
  console.error("Could not generate the client from ./openapi.yaml:");
  console.error("");
  console.error(generation.output.trimEnd());
  console.error("");
  process.exit(1);
}

const after = snapshot(GENERATED);

const changed = [...after.keys()].filter(
  (f) => before.has(f) && before.get(f) !== after.get(f),
);
const added = [...after.keys()].filter((f) => !before.has(f));
const removed = [...before.keys()].filter((f) => !after.has(f));

restore(GENERATED, before, added);

if (changed.length === 0 && added.length === 0 && removed.length === 0) {
  console.log(
    `The committed client matches ./openapi.yaml (${after.size} files).`,
  );
  process.exit(0);
}

console.error("");
console.error(
  "The committed API client does not match what ./openapi.yaml generates:",
);
console.error("");
for (const f of changed) console.error(`  differs:            ${f}`);
for (const f of added) console.error(`  only when generated: ${f}`);
for (const f of removed) console.error(`  only in the commit:  ${f}`);
console.error("");
console.error(
  "Run 'npm run openapi:gen' and commit the result. If you hand-edited anything under\n" +
    "src/api/generated, don't — the contract is ./openapi.yaml.",
);
console.error("");
process.exit(1);
