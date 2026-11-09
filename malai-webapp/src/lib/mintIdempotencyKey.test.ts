import { describe, expect, it } from "vitest";
import { mintIdempotencyKey } from "./mintIdempotencyKey";

const UUID_V4 =
  /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

describe("mintIdempotencyKey", () => {
  it("returns a string matching the UUID v4 shape", () => {
    expect(mintIdempotencyKey()).toMatch(UUID_V4);
  });

  it("returns a unique value across many calls", () => {
    const N = 1000;
    const seen = new Set<string>();
    for (let i = 0; i < N; i += 1) {
      seen.add(mintIdempotencyKey());
    }
    expect(seen.size).toBe(N);
  });
});
