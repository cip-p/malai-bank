import { beforeEach, describe, expect, it, vi } from "vitest";
import { act, renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import type { MdrChainVerificationResponse } from "@/api/generated/types.gen";
import { HttpError } from "@/api/http";

vi.mock("@/api/generated/sdk.gen", () => ({
  getApiV1AdminMdrChainVerification: vi.fn(),
}));

const toastErrorMock = vi.fn();
vi.mock("sonner", () => ({
  toast: { error: (...args: unknown[]) => toastErrorMock(...args) },
}));

const { createQueryClient } = await import("@/lib/queryClient");

const sdk = await import("@/api/generated/sdk.gen");
const verifierMock =
  sdk.getApiV1AdminMdrChainVerification as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;

const { useMdrChainVerification } = await import("./useMdrChainVerification");

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

const INTACT: MdrChainVerificationResponse = {
  result: "OK",
  rowsVerified: 128,
};

describe("useMdrChainVerification", () => {
  beforeEach(() => {
    verifierMock.mockReset();
    toastErrorMock.mockReset();
  });

  it("walks nothing until asked, then returns the verdict", async () => {
    verifierMock.mockResolvedValue({ data: INTACT });

    const { result } = renderHook(() => useMdrChainVerification(), {
      wrapper: wrapperOf(makeClient()),
    });

    expect(result.current.isIdle).toBe(true);
    expect(result.current.verdict).toBeNull();
    expect(verifierMock).not.toHaveBeenCalled();

    act(() => result.current.verify());

    await waitFor(() => expect(result.current.verdict).toEqual(INTACT));
    expect(result.current.isIdle).toBe(false);
    expect(result.current.isError).toBe(false);
    expect(verifierMock).toHaveBeenCalledTimes(1);
  });

  it("keeps a failed walk an error — the check failing is not a verdict", async () => {
    verifierMock.mockRejectedValue(new HttpError(500, { message: "boom" }));

    const { result } = renderHook(() => useMdrChainVerification(), {
      wrapper: wrapperOf(makeClient()),
    });

    act(() => result.current.verify());

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.verdict).toBeNull();
    expect(result.current.isIdle).toBe(false);
  });

  it("re-walks on demand, flipping a failed check to the verdict", async () => {
    verifierMock.mockRejectedValueOnce(new HttpError(500, { message: "boom" }));
    verifierMock.mockResolvedValueOnce({ data: INTACT });

    const { result } = renderHook(() => useMdrChainVerification(), {
      wrapper: wrapperOf(makeClient()),
    });

    act(() => result.current.verify());
    await waitFor(() => expect(result.current.isError).toBe(true));

    act(() => result.current.verify());

    await waitFor(() => expect(result.current.verdict).toEqual(INTACT));
    expect(result.current.isError).toBe(false);
    expect(verifierMock).toHaveBeenCalledTimes(2);
  });

  it("never serves a stale verdict beside a failed re-walk", async () => {
    verifierMock.mockResolvedValueOnce({ data: INTACT });
    verifierMock.mockRejectedValueOnce(new HttpError(500, { message: "boom" }));

    const { result } = renderHook(() => useMdrChainVerification(), {
      wrapper: wrapperOf(makeClient()),
    });

    act(() => result.current.verify());
    await waitFor(() => expect(result.current.verdict).toEqual(INTACT));

    act(() => result.current.verify());

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.verdict).toBeNull();
  });

  it("is verifying while the walk is in flight, holding no verdict meanwhile", async () => {
    let answer: (value: unknown) => void = () => {};
    verifierMock.mockImplementation(
      () => new Promise((resolve) => (answer = resolve)),
    );

    const { result } = renderHook(() => useMdrChainVerification(), {
      wrapper: wrapperOf(makeClient()),
    });

    expect(result.current.isVerifying).toBe(false);
    act(() => result.current.verify());

    await waitFor(() => expect(result.current.isVerifying).toBe(true));
    expect(result.current.isIdle).toBe(false);
    expect(result.current.verdict).toBeNull();

    act(() => answer({ data: INTACT }));
    await waitFor(() => expect(result.current.isVerifying).toBe(false));
    expect(result.current.verdict).toEqual(INTACT);

    act(() => result.current.verify());
    await waitFor(() => expect(result.current.isVerifying).toBe(true));
    expect(result.current.verdict).toBeNull();
  });

  it("never toasts about the walk — even a failed check stays on the page", async () => {
    verifierMock.mockRejectedValue(new HttpError(500, { message: "boom" }));

    const { result } = renderHook(() => useMdrChainVerification(), {
      wrapper: wrapperOf(createQueryClient()),
    });

    act(() => result.current.verify());

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(toastErrorMock).not.toHaveBeenCalled();
  });
});
