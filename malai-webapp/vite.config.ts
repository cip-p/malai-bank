import { defineConfig } from "vitest/config";
import path from "node:path";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

const SHELL_PACKAGES = [
  "react",
  "react-dom",
  "scheduler",
  "react-router",
  "react-router-dom",
  "@tanstack/query-core",
  "@tanstack/react-query",
  "zustand",
  "sonner",
  "next-themes",
  "tailwind-merge",
  "clsx",
  "class-variance-authority",
  "radix-ui",
  "react-remove-scroll",
  "react-remove-scroll-bar",
  "react-style-singleton",
  "aria-hidden",
  "use-callback-ref",
  "use-sidecar",
  "get-nonce",
  "tslib",
];
const SHELL_SCOPES = ["@radix-ui/", "@floating-ui/", "@hey-api/"];

function packageOf(id: string): string | null {
  const match = id.match(/\/node_modules\/((?:@[^/]+\/)?[^/]+)/);
  return match ? match[1]! : null;
}

function isShellPackage(name: string): boolean {
  return (
    SHELL_PACKAGES.includes(name) ||
    SHELL_SCOPES.some((scope) => name.startsWith(scope))
  );
}

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "src"),
    },
  },
  build: {
    outDir: "out",
    manifest: true,
    rollupOptions: {
      output: {
        manualChunks(id) {
          const name = packageOf(id);
          return name && isShellPackage(name) ? "vendor" : undefined;
        },
      },
    },
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./src/test/setup.ts"],
    exclude: ["node_modules", "out", "dist"],
  },
});
