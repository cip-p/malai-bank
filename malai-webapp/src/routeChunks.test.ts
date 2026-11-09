import { describe, expect, it, vi } from "vitest";

vi.mock("@/pages/LoginPage", () => ({ LoginPage: () => null }));
vi.mock("@/pages/ops/WorklistPage", () => ({ WorklistPage: () => null }));

import { entrySegmentOf, warmEntryChunk } from "./routeChunks";

describe("entrySegmentOf", () => {
  it("returns the first path segment", () => {
    expect(entrySegmentOf("/ops/cases/abc")).toBe("ops");
    expect(entrySegmentOf("/login")).toBe("login");
  });

  it("returns an empty segment for the root", () => {
    expect(entrySegmentOf("/")).toBe("");
  });
});

describe("warmEntryChunk", () => {
  it("starts loading the landing page of a lazy surface", async () => {
    const loading = warmEntryChunk("/ops/cases");
    expect(loading).not.toBeNull();
    await expect(loading).resolves.toHaveProperty("WorklistPage");
  });

  it("starts loading the auth page a first visit lands on", async () => {
    await expect(warmEntryChunk("/login")).resolves.toHaveProperty("LoginPage");
  });

  it("does nothing for the eager root and for unknown paths", () => {
    expect(warmEntryChunk("/")).toBeNull();
    expect(warmEntryChunk("/nowhere")).toBeNull();
  });
});
