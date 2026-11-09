import { beforeEach, describe, expect, it, vi } from "vitest";
import { toast } from "sonner";
import type { Query } from "@tanstack/react-query";
import { HttpError, NetworkError } from "@/api/http";
import {
  getApiV1UserQueryKey,
  getReadyQueryKey,
} from "@/api/generated/@tanstack/react-query.gen";
import { DEFAULT_STALE_TIME_MS, createQueryClient } from "./queryClient";

type OnErrorQuery = Query<unknown, unknown, unknown, readonly unknown[]>;
const mutationContext = (client: ReturnType<typeof createQueryClient>) => ({
  client,
  meta: undefined,
});

vi.mock("sonner", () => ({
  toast: { error: vi.fn() },
}));

beforeEach(() => {
  (toast.error as ReturnType<typeof vi.fn>).mockReset();
});

describe("createQueryClient", () => {
  it("keeps fetched data fresh across route remounts instead of refetching on every mount", () => {
    const client = createQueryClient();
    expect(client.getDefaultOptions().queries?.staleTime).toBe(
      DEFAULT_STALE_TIME_MS,
    );
  });

  it("invalidates the user query when a different query returns 401", () => {
    const client = createQueryClient();
    const cache = client.getQueryCache();
    const otherQuery = cache.build(client, {
      queryKey: [{ _id: "getApiV1Accounts", baseUrl: "" }],
    });
    const spy = vi.spyOn(client, "invalidateQueries");

    cache.config.onError?.(
      new HttpError(401, { message: "x" }),
      otherQuery as unknown as OnErrorQuery,
    );

    expect(spy).toHaveBeenCalledWith({ queryKey: getApiV1UserQueryKey() });
  });

  it("does not invalidate when the failing query is the user query itself", () => {
    const client = createQueryClient();
    const cache = client.getQueryCache();
    const userQuery = cache.build(client, {
      queryKey: getApiV1UserQueryKey(),
    });
    const spy = vi.spyOn(client, "invalidateQueries");

    cache.config.onError?.(
      new HttpError(401, { message: "x" }),
      userQuery as unknown as OnErrorQuery,
    );

    expect(spy).not.toHaveBeenCalled();
  });

  it("rechecks readiness when a query fails because the backend could not be reached", () => {
    const client = createQueryClient();
    const cache = client.getQueryCache();
    const otherQuery = cache.build(client, {
      queryKey: [{ _id: "getApiV1Accounts", baseUrl: "" }],
    });
    const spy = vi.spyOn(client, "invalidateQueries");

    cache.config.onError?.(
      new NetworkError(new TypeError("Failed to fetch")),
      otherQuery as unknown as OnErrorQuery,
    );

    expect(spy).toHaveBeenCalledWith({ queryKey: getReadyQueryKey() });
  });

  it("does not re-probe in a loop when the readiness probe itself is what failed", () => {
    const client = createQueryClient();
    const cache = client.getQueryCache();
    const readyQuery = cache.build(client, {
      queryKey: getReadyQueryKey(),
    });
    const spy = vi.spyOn(client, "invalidateQueries");

    cache.config.onError?.(
      new NetworkError(new TypeError("Failed to fetch")),
      readyQuery as unknown as OnErrorQuery,
    );

    expect(spy).not.toHaveBeenCalled();
  });

  it("ignores non-401 errors", () => {
    const client = createQueryClient();
    const cache = client.getQueryCache();
    const otherQuery = cache.build(client, {
      queryKey: [{ _id: "getApiV1Accounts", baseUrl: "" }],
    });
    const spy = vi.spyOn(client, "invalidateQueries");

    cache.config.onError?.(
      new HttpError(500, { message: "x" }),
      otherQuery as unknown as OnErrorQuery,
    );

    expect(spy).not.toHaveBeenCalled();
  });

  describe("MutationCache.onError", () => {
    function buildMutation(client: ReturnType<typeof createQueryClient>) {
      return client.getMutationCache().build(client, {});
    }

    function buildMutationWithMeta(
      client: ReturnType<typeof createQueryClient>,
      meta: Record<string, unknown>,
    ) {
      return client.getMutationCache().build(client, { meta });
    }

    function seedUserQueryAs(
      client: ReturnType<typeof createQueryClient>,
      data: unknown,
    ) {
      client.setQueryData(getApiV1UserQueryKey(), data);
    }

    function seedUserQueryAs401(client: ReturnType<typeof createQueryClient>) {
      const query = client.getQueryCache().build(client, {
        queryKey: getApiV1UserQueryKey(),
      });
      query.setState({
        ...query.state,
        status: "error",
        error: new HttpError(401, { message: "x" }),
      });
    }

    it("on 401 when the user IS authenticated, invalidates the user query and skips the generic toast (existing session-expiry flow)", () => {
      const client = createQueryClient();
      seedUserQueryAs(client, { firstName: "Ada", lastName: "Lovelace" });
      const spy = vi.spyOn(client, "invalidateQueries");
      const cache = client.getMutationCache();

      cache.config.onError?.(
        new HttpError(401, { message: "x" }),
        undefined,
        undefined,
        buildMutation(client),
        mutationContext(client),
      );

      expect(spy).toHaveBeenCalledWith({ queryKey: getApiV1UserQueryKey() });
      expect(toast.error).not.toHaveBeenCalled();
    });

    it("on 401 when the user is NOT authenticated, fires the generic toast and does NOT churn the user query", () => {
      const client = createQueryClient();
      seedUserQueryAs401(client);
      const spy = vi.spyOn(client, "invalidateQueries");
      const cache = client.getMutationCache();

      cache.config.onError?.(
        new HttpError(401, { message: "x" }),
        undefined,
        undefined,
        buildMutation(client),
        mutationContext(client),
      );

      expect(spy).not.toHaveBeenCalled();
      expect(toast.error).toHaveBeenCalledWith(
        "Something went wrong. Please try again.",
      );
    });

    it("with meta.handledLocally=true on a non-401 error, suppresses the generic toast (lets the caller surface its own message)", () => {
      const client = createQueryClient();
      seedUserQueryAs401(client);
      const cache = client.getMutationCache();

      cache.config.onError?.(
        new HttpError(500, { message: "x" }),
        undefined,
        undefined,
        buildMutationWithMeta(client, { handledLocally: true }),
        mutationContext(client),
      );

      expect(toast.error).not.toHaveBeenCalled();
    });

    it("with meta.handledLocally=true on a 401 from an unauth endpoint, suppresses the generic toast (useLogin's inline setError carries the message)", () => {
      const client = createQueryClient();
      seedUserQueryAs401(client);
      const cache = client.getMutationCache();

      cache.config.onError?.(
        new HttpError(401, { message: "x" }),
        undefined,
        undefined,
        buildMutationWithMeta(client, { handledLocally: true }),
        mutationContext(client),
      );

      expect(toast.error).not.toHaveBeenCalled();
    });

    it("rechecks readiness when a mutation fails because the backend could not be reached", () => {
      const client = createQueryClient();
      seedUserQueryAs401(client);
      const spy = vi.spyOn(client, "invalidateQueries");
      const cache = client.getMutationCache();

      cache.config.onError?.(
        new NetworkError(new TypeError("Failed to fetch")),
        undefined,
        undefined,
        buildMutation(client),
        mutationContext(client),
      );

      expect(spy).toHaveBeenCalledWith({ queryKey: getReadyQueryKey() });
    });

    it("on a non-401 error without meta.handledLocally, fires the generic toast", () => {
      const client = createQueryClient();
      seedUserQueryAs401(client);
      const cache = client.getMutationCache();

      cache.config.onError?.(
        new HttpError(500, { message: "x" }),
        undefined,
        undefined,
        buildMutation(client),
        mutationContext(client),
      );

      expect(toast.error).toHaveBeenCalledWith(
        "Something went wrong. Please try again.",
      );
    });
  });
});
