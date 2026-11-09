import { beforeEach, describe, expect, it, vi } from "vitest";
import { act, renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { HttpError } from "@/api/http";
import { useCardPresentmentMutation } from "./useCardPresentmentMutation";

vi.mock("@/api/generated/sdk.gen", () => ({
  postApiV1SandboxCardPresentments: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const presentMock =
  sdk.postApiV1SandboxCardPresentments as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;

const toastSuccessMock = vi.fn();
const toastErrorMock = vi.fn();
vi.mock("sonner", () => ({
  toast: {
    success: (...args: unknown[]) => toastSuccessMock(...args),
    error: (...args: unknown[]) => toastErrorMock(...args),
  },
}));

function makeClient(): QueryClient {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
}

function wrapperOf(client: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
  Wrapper.displayName = "QueryClientWrapper";
  return Wrapper;
}

describe("useCardPresentmentMutation", () => {
  beforeEach(() => {
    presentMock.mockReset();
    toastSuccessMock.mockReset();
    toastErrorMock.mockReset();
  });

  it("toasts the presented count and invalidates account money queries", async () => {
    presentMock.mockResolvedValue({ data: { presented: 3 } });
    const client = makeClient();
    const invalidateSpy = vi.spyOn(client, "invalidateQueries");

    const { result } = renderHook(() => useCardPresentmentMutation(), {
      wrapper: wrapperOf(client),
    });

    act(() => {
      result.current.presentNow();
    });

    await waitFor(() => {
      expect(toastSuccessMock).toHaveBeenCalledWith(
        "3 pending captures presented.",
      );
    });
    expect(invalidateSpy).toHaveBeenCalled();
  });

  it("uses the singular copy for a single capture", async () => {
    presentMock.mockResolvedValue({ data: { presented: 1 } });

    const { result } = renderHook(() => useCardPresentmentMutation(), {
      wrapper: wrapperOf(makeClient()),
    });

    act(() => {
      result.current.presentNow();
    });

    await waitFor(() => {
      expect(toastSuccessMock).toHaveBeenCalledWith(
        "1 pending capture presented.",
      );
    });
  });

  it("fires the generic toast on failure", async () => {
    presentMock.mockRejectedValue(new HttpError(500, { message: "boom" }));

    const { result } = renderHook(() => useCardPresentmentMutation(), {
      wrapper: wrapperOf(makeClient()),
    });

    act(() => {
      result.current.presentNow();
    });

    await waitFor(() => {
      expect(toastErrorMock).toHaveBeenCalledWith(
        "Something went wrong. Please try again.",
      );
    });
  });
});
