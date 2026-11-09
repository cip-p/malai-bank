import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { HttpError } from "@/api/http";
import type { UserResponse } from "@/api/generated/types.gen";
import { useUser } from "./useUser";

vi.mock("@/api/generated/sdk.gen", () => ({
  getApiV1User: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const getApiV1UserMock = sdk.getApiV1User as unknown as ReturnType<
  typeof vi.fn<(...args: unknown[]) => unknown>
>;

function makeClient(): QueryClient {
  return new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
}

function wrapperOf(client: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
  Wrapper.displayName = "QueryClientWrapper";
  return Wrapper;
}

const SAMPLE_USER: UserResponse = {
  firstName: "Ada",
  lastName: "Lovelace",
  email: "ada@example.com",
  dateOfBirth: "1815-12-10",
  roles: ["CUSTOMER"],
};

describe("useUser", () => {
  beforeEach(() => {
    getApiV1UserMock.mockReset();
  });

  it("returns status 'unknown' while the underlying query is pending", () => {
    getApiV1UserMock.mockImplementation(() => new Promise(() => {}));
    const { result } = renderHook(() => useUser(), {
      wrapper: wrapperOf(makeClient()),
    });
    expect(result.current).toEqual({ user: null, status: "unknown" });
  });

  it("returns status 'authenticated' and the user when the query resolves", async () => {
    getApiV1UserMock.mockResolvedValue({ data: SAMPLE_USER });
    const { result } = renderHook(() => useUser(), {
      wrapper: wrapperOf(makeClient()),
    });
    await waitFor(() =>
      expect(result.current).toEqual({
        user: SAMPLE_USER,
        status: "authenticated",
      }),
    );
  });

  it("returns status 'unauthenticated' on HTTP 401", async () => {
    getApiV1UserMock.mockRejectedValue(new HttpError(401, { message: "5982" }));
    const { result } = renderHook(() => useUser(), {
      wrapper: wrapperOf(makeClient()),
    });
    await waitFor(() =>
      expect(result.current).toEqual({
        user: null,
        status: "unauthenticated",
      }),
    );
  });

  it("returns status 'unknown' on any non-401 error", async () => {
    getApiV1UserMock.mockRejectedValue(new HttpError(500, { message: "9999" }));
    const { result } = renderHook(() => useUser(), {
      wrapper: wrapperOf(makeClient()),
    });
    await waitFor(() => {
      expect(result.current.status).toBe("unknown");
      expect(result.current.user).toBeNull();
    });
  });
});
