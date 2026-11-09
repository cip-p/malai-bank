import { describe, expect, it, vi } from "vitest";
import { createRefreshingFetch } from "./refreshMiddleware";

const BASE = "http://localhost:8080";
const PROTECTED_URL = `${BASE}/api/v1/user`;
const REFRESH_PATH = "/api/v1/auth/refresh-token";
const LOGIN_URL = `${BASE}/api/v1/auth/login`;

function pathOf(request: Request): string {
  return new URL(request.url).pathname;
}

describe("createRefreshingFetch", () => {
  it("refreshes once and retries the original request after a 401", async () => {
    let protectedCalls = 0;
    let refreshCalls = 0;
    const inner = vi.fn(async (request: Request) => {
      if (pathOf(request) === REFRESH_PATH) {
        refreshCalls += 1;
        return new Response(null, { status: 200 });
      }
      protectedCalls += 1;
      return new Response(JSON.stringify({ ok: true }), {
        status: protectedCalls === 1 ? 401 : 200,
        headers: { "Content-Type": "application/json" },
      });
    });

    const response = await createRefreshingFetch(inner)(
      new Request(PROTECTED_URL),
    );

    expect(response.status).toBe(200);
    expect(refreshCalls).toBe(1);
    expect(protectedCalls).toBe(2);
  });

  it("sends a JSON credentialed POST to the refresh endpoint", async () => {
    let protectedCalls = 0;
    const inner = vi.fn(async (request: Request) => {
      if (pathOf(request) === REFRESH_PATH) {
        return new Response(null, { status: 200 });
      }
      protectedCalls += 1;
      return new Response(null, { status: protectedCalls === 1 ? 401 : 200 });
    });

    await createRefreshingFetch(inner)(new Request(PROTECTED_URL));

    const refreshRequest = inner.mock.calls
      .map(([request]) => request)
      .find((request) => pathOf(request) === REFRESH_PATH)!;
    expect(refreshRequest.method).toBe("POST");
    expect(refreshRequest.credentials).toBe("include");
    expect(refreshRequest.url).toBe(`${BASE}${REFRESH_PATH}`);
    expect(refreshRequest.headers.get("Content-Type")).toBe("application/json");
    expect(await refreshRequest.clone().text()).toBe("{}");
  });

  it("coalesces concurrent 401s onto a single refresh", async () => {
    let refreshCalls = 0;
    const protectedCalls = new Map<string, number>();
    const inner = vi.fn(async (request: Request) => {
      const path = pathOf(request);
      if (path === REFRESH_PATH) {
        refreshCalls += 1;
        return new Response(null, { status: 200 });
      }
      const seen = (protectedCalls.get(path) ?? 0) + 1;
      protectedCalls.set(path, seen);
      return new Response(null, { status: seen === 1 ? 401 : 200 });
    });

    const refreshingFetch = createRefreshingFetch(inner);
    const urls = ["a", "b", "c", "d", "e"].map(
      (id) => `${BASE}/api/v1/accounts/${id}`,
    );
    const responses = await Promise.all(
      urls.map((url) => refreshingFetch(new Request(url))),
    );

    expect(responses.every((response) => response.status === 200)).toBe(true);
    expect(refreshCalls).toBe(1);
  });

  it("starts a fresh refresh for a 401 that arrives after the previous refresh settled", async () => {
    let refreshCalls = 0;
    const protectedCalls = new Map<string, number>();
    const inner = vi.fn(async (request: Request) => {
      const path = pathOf(request);
      if (path === REFRESH_PATH) {
        refreshCalls += 1;
        return new Response(null, { status: 200 });
      }
      const seen = (protectedCalls.get(path) ?? 0) + 1;
      protectedCalls.set(path, seen);
      return new Response(null, { status: seen === 1 ? 401 : 200 });
    });

    const refreshingFetch = createRefreshingFetch(inner);
    await refreshingFetch(new Request(`${BASE}/api/v1/accounts/first`));
    await refreshingFetch(new Request(`${BASE}/api/v1/accounts/second`));

    expect(refreshCalls).toBe(2);
  });

  it("returns the original 401 without retrying when the refresh responds 401", async () => {
    let protectedCalls = 0;
    const inner = vi.fn(async (request: Request) => {
      if (pathOf(request) === REFRESH_PATH) {
        return new Response(null, { status: 401 });
      }
      protectedCalls += 1;
      return new Response(null, { status: 401 });
    });

    const response = await createRefreshingFetch(inner)(
      new Request(PROTECTED_URL),
    );

    expect(response.status).toBe(401);
    expect(protectedCalls).toBe(1);
  });

  it("treats a thrown refresh as a failure and returns the original 401", async () => {
    let protectedCalls = 0;
    const inner = vi.fn(async (request: Request) => {
      if (pathOf(request) === REFRESH_PATH) {
        throw new TypeError("network down");
      }
      protectedCalls += 1;
      return new Response(null, { status: 401 });
    });

    const response = await createRefreshingFetch(inner)(
      new Request(PROTECTED_URL),
    );

    expect(response.status).toBe(401);
    expect(protectedCalls).toBe(1);
  });

  it("passes through successful responses without refreshing", async () => {
    const inner = vi.fn(async () => new Response(null, { status: 200 }));

    const response = await createRefreshingFetch(inner)(
      new Request(PROTECTED_URL),
    );

    expect(response.status).toBe(200);
    expect(inner).toHaveBeenCalledTimes(1);
  });

  it("passes through non-401 errors without refreshing", async () => {
    const inner = vi.fn(async () => new Response(null, { status: 500 }));

    const response = await createRefreshingFetch(inner)(
      new Request(PROTECTED_URL),
    );

    expect(response.status).toBe(500);
    expect(inner).toHaveBeenCalledTimes(1);
  });

  it("never refreshes when an auth endpoint itself returns 401", async () => {
    let refreshCalls = 0;
    const inner = vi.fn(async (request: Request) => {
      if (pathOf(request) === REFRESH_PATH) {
        refreshCalls += 1;
      }
      return new Response(null, { status: 401 });
    });

    const response = await createRefreshingFetch(inner)(
      new Request(LOGIN_URL, { method: "POST" }),
    );

    expect(response.status).toBe(401);
    expect(refreshCalls).toBe(0);
    expect(inner).toHaveBeenCalledTimes(1);
  });

  it("retries at most once even if the retry also returns 401", async () => {
    let protectedCalls = 0;
    let refreshCalls = 0;
    const inner = vi.fn(async (request: Request) => {
      if (pathOf(request) === REFRESH_PATH) {
        refreshCalls += 1;
        return new Response(null, { status: 200 });
      }
      protectedCalls += 1;
      return new Response(null, { status: 401 });
    });

    const response = await createRefreshingFetch(inner)(
      new Request(PROTECTED_URL),
    );

    expect(response.status).toBe(401);
    expect(refreshCalls).toBe(1);
    expect(protectedCalls).toBe(2);
  });

  it("retries the original request preserving its method and body", async () => {
    const seen: Array<{ method: string; body: string }> = [];
    let protectedCalls = 0;
    const inner = vi.fn(async (request: Request) => {
      if (pathOf(request) === REFRESH_PATH) {
        return new Response(null, { status: 200 });
      }
      protectedCalls += 1;
      seen.push({ method: request.method, body: await request.text() });
      return new Response(null, { status: protectedCalls === 1 ? 401 : 200 });
    });

    const payload = JSON.stringify({ name: "Acme" });
    const response = await createRefreshingFetch(inner)(
      new Request(`${BASE}/api/v1/payees`, {
        method: "POST",
        body: payload,
        headers: { "Content-Type": "application/json" },
      }),
    );

    expect(response.status).toBe(200);
    expect(seen).toEqual([
      { method: "POST", body: payload },
      { method: "POST", body: payload },
    ]);
  });
});
