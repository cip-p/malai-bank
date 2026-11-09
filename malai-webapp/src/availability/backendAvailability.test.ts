import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { client } from "@/api/generated/client.gen";
import { HttpError, NetworkError } from "@/api/http";
import {
  PROBE_TIMEOUT_MS,
  REACHABLE_PROBE_INTERVAL_MS,
  UNREACHABLE_PROBE_INTERVAL_MS,
  backendReadyQueryOptions,
  isBackendUnreachable,
  probeBackend,
} from "./backendAvailability";

const BASE = "http://localhost:8080";

type Fetch = (request: Request) => Promise<Response>;

function withFetch(fetchImpl: Fetch) {
  client.setConfig({ baseUrl: BASE, fetch: fetchImpl as typeof fetch });
}

const aborted = () =>
  Object.assign(new Error("aborted"), { name: "AbortError" });

const hangingFetch: Fetch = (request) =>
  new Promise<Response>((_, reject) => {
    if (request.signal.aborted) {
      reject(aborted());
      return;
    }
    request.signal.addEventListener("abort", () => reject(aborted()));
  });

describe("isBackendUnreachable", () => {
  it("treats a network failure as the backend being unreachable", () => {
    expect(isBackendUnreachable(new NetworkError(new TypeError("x")))).toBe(
      true,
    );
  });

  it.each([502, 503, 504])(
    "treats %i as the backend being unreachable — nothing answered behind the edge",
    (status) => {
      expect(isBackendUnreachable(new HttpError(status, null))).toBe(true);
    },
  );

  it.each([400, 401, 404, 409, 500])(
    "treats %i as the backend answering — badly, but answering",
    (status) => {
      expect(isBackendUnreachable(new HttpError(status, null))).toBe(false);
    },
  );

  it("ignores errors that never reached the transport", () => {
    expect(isBackendUnreachable(new Error("boom"))).toBe(false);
  });
});

describe("probeBackend", () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it("resolves when /ready answers", async () => {
    const seen: string[] = [];
    withFetch(async (request) => {
      seen.push(new URL(request.url).pathname);
      return new Response(null, { status: 200 });
    });

    await expect(probeBackend()).resolves.toBe(true);
    expect(seen).toEqual(["/ready"]);
  });

  it("rejects when the request fails outright", async () => {
    withFetch(async () => {
      throw new TypeError("Failed to fetch");
    });

    await expect(probeBackend()).rejects.toBeDefined();
  });

  it("rejects when /ready answers with an error status", async () => {
    withFetch(async () => new Response(null, { status: 503 }));

    await expect(probeBackend()).rejects.toBeDefined();
  });

  it("gives up on a request that hangs, so a dead host cannot leave the app loading forever", async () => {
    vi.useFakeTimers();
    withFetch(hangingFetch);

    const probe = probeBackend();
    const settled = expect(probe).rejects.toBeDefined();
    await vi.advanceTimersByTimeAsync(PROBE_TIMEOUT_MS);

    await settled;
  });

  it("stops probing when the caller aborts", async () => {
    const controller = new AbortController();
    withFetch(hangingFetch);

    const probe = probeBackend(controller.signal);
    controller.abort();

    await expect(probe).rejects.toBeDefined();
  });
});

describe("backendReadyQueryOptions", () => {
  beforeEach(() => {
    client.setConfig({ baseUrl: BASE });
  });

  it("polls slowly while the backend answers", () => {
    const { refetchInterval } = backendReadyQueryOptions();
    const query = { state: { status: "success" } };

    expect(
      (refetchInterval as (q: typeof query) => number | false)(query),
    ).toBe(REACHABLE_PROBE_INTERVAL_MS);
  });

  it("polls quickly once the backend has gone, so the app comes back on its own", () => {
    const { refetchInterval } = backendReadyQueryOptions();
    const query = { state: { status: "error" } };

    expect(
      (refetchInterval as (q: typeof query) => number | false)(query),
    ).toBe(UNREACHABLE_PROBE_INTERVAL_MS);
  });
});
