// ESLint 9 flat config. RESEARCH §Code Examples + §Pitfall 8.
// WEB-14: forbid `$` literal in source per D-3.16 (en-GB everywhere).
import js from "@eslint/js";
import globals from "globals";
import tseslint from "typescript-eslint";
import react from "eslint-plugin-react";
import reactHooks from "eslint-plugin-react-hooks";

// ADR-0003: rebrand must be one-file-edit. Ban literal Tailwind palette utilities
// (`bg-blue-500`, `text-emerald-600`, `border-zinc-300`, `ring-red-400`, etc.) so
// no component leaks colour outside the token surface. Token utilities like
// `bg-primary`, `text-muted-foreground`, `border-border` remain allowed.
const TAILWIND_PALETTE = [
  "slate",
  "gray",
  "zinc",
  "neutral",
  "stone",
  "red",
  "orange",
  "amber",
  "yellow",
  "lime",
  "green",
  "emerald",
  "teal",
  "cyan",
  "sky",
  "blue",
  "indigo",
  "violet",
  "purple",
  "fuchsia",
  "pink",
  "rose",
];
const TAILWIND_COLOR_PREFIXES = [
  "bg",
  "text",
  "border",
  "ring",
  "outline",
  "fill",
  "stroke",
  "from",
  "via",
  "to",
  "divide",
  "placeholder",
  "caret",
  "accent",
  "decoration",
  "shadow",
];
const FORBIDDEN_PALETTE_CLASS_REGEX = new RegExp(
  `\\b(?:${TAILWIND_COLOR_PREFIXES.join("|")})-(?:${TAILWIND_PALETTE.join("|")})-(?:50|100|200|300|400|500|600|700|800|900|950)\\b`,
);

export default tseslint.config(
  {
    ignores: [
      "node_modules",
      "out",
      "dist",
      "public",
      "src/api/generated/**",
      "**/*.config.js",
      "**/*.config.ts",
    ],
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ["src/**/*.{ts,tsx}"],
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: "module",
      globals: { ...globals.browser, ...globals.es2022 },
      parserOptions: {
        ecmaFeatures: { jsx: true },
      },
    },
    plugins: {
      react,
      "react-hooks": reactHooks,
    },
    settings: {
      react: { version: "detect" },
    },
    rules: {
      ...react.configs.recommended.rules,
      ...reactHooks.configs.recommended.rules,
      "react/react-in-jsx-scope": "off",
      "react/prop-types": "off",
      // Leading-underscore params signal "intentionally unused" (e.g. PersistStorage
      // interface signature where we don't use the storage `name` arg). Match the
      // typescript-eslint convention.
      "@typescript-eslint/no-unused-vars": [
        "error",
        { argsIgnorePattern: "^_", varsIgnorePattern: "^_" },
      ],
      // WEB-14: forbid `$` anywhere in source (D-3.16). Use `£` and formatGBP().
      "no-restricted-syntax": [
        "error",
        {
          selector: "Literal[value=/\\$/]",
          message:
            "WEB-14: `$` is forbidden in source. Use `£` and formatGBP() per D-3.16.",
        },
        {
          selector: "TemplateElement[value.raw=/\\$/]",
          message:
            "WEB-14: `$` is forbidden in source (template literal). Use `£` and formatGBP() per D-3.16.",
        },
        {
          selector: `Literal[value=/${FORBIDDEN_PALETTE_CLASS_REGEX.source}/]`,
          message:
            "ADR-0003: literal Tailwind palette colours (e.g. `bg-blue-500`) are forbidden. Use token utilities like `bg-primary` or add a token in src/styles/themes.css.",
        },
        {
          selector: `TemplateElement[value.raw=/${FORBIDDEN_PALETTE_CLASS_REGEX.source}/]`,
          message:
            "ADR-0003: literal Tailwind palette colours (e.g. `bg-blue-500`) are forbidden in template literals. Use token utilities like `bg-primary` or add a token in src/styles/themes.css.",
        },
      ],
    },
  },
  {
    files: ["**/*.test.ts", "**/*.test.tsx"],
    rules: {
      // Test files may freely use `$` in selectors, fixture strings, etc.
      "no-restricted-syntax": "off",
    },
  },
  {
    files: ["scripts/**/*.mjs"],
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: "module",
      globals: { ...globals.node },
    },
  },
);
