import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import type { CounterpartyResponse } from "@/api/generated/types.gen";
import { useCounterparties } from "./useCounterparties";

vi.mock("@/api/generated/sdk.gen", () => ({
  getApiV1SandboxCounterparties: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const getApiV1SandboxCounterpartiesMock =
  sdk.getApiV1SandboxCounterparties as unknown as ReturnType<
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

const SARAH: CounterpartyResponse = {
  bank: "Stub Bank",
  nameOnRecord: "Sarah Mitchell",
  sortCode: "12-34-56",
  accountNumber: "12345678",
  outboundOutcome: "SETTLES",
};

describe("useCounterparties", () => {
  beforeEach(() => {
    getApiV1SandboxCounterpartiesMock.mockReset();
  });

  it("returns the catalogue rows once the query resolves", async () => {
    getApiV1SandboxCounterpartiesMock.mockResolvedValue({
      data: { counterparties: [SARAH] },
    });

    const { result } = renderHook(() => useCounterparties(), {
      wrapper: wrapperOf(makeClient()),
    });

    await waitFor(() => expect(result.current.counterparties).toEqual([SARAH]));
    expect(getApiV1SandboxCounterpartiesMock).toHaveBeenCalledTimes(1);
  });

  it("reports isPending while the query is in-flight", () => {
    getApiV1SandboxCounterpartiesMock.mockImplementation(
      () => new Promise(() => {}),
    );

    const { result } = renderHook(() => useCounterparties(), {
      wrapper: wrapperOf(makeClient()),
    });

    expect(result.current.isPending).toBe(true);
    expect(result.current.counterparties).toBeNull();
  });

  it("reports isError when the catalogue request fails", async () => {
    getApiV1SandboxCounterpartiesMock.mockRejectedValue(new Error("boom"));

    const { result } = renderHook(() => useCounterparties(), {
      wrapper: wrapperOf(makeClient()),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.counterparties).toBeNull();
  });
});
