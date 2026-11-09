import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import type { MdrDecisionSummary } from "@/api/generated/types.gen";

vi.mock("@/api/generated/sdk.gen", () => ({
  getApiV1AdminMdrDecisions: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const getDecisionsMock = sdk.getApiV1AdminMdrDecisions as unknown as ReturnType<
  typeof vi.fn<(...args: unknown[]) => unknown>
>;

const { useMdrDecisions } = await import("./useMdrDecisions");

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

function makeDecision(
  overrides: Partial<MdrDecisionSummary>,
): MdrDecisionSummary {
  return {
    decisionId: "11111111-1111-1111-1111-111111111111",
    entryId: "22222222-2222-2222-2222-222222222222",
    seq: 12,
    ts: "2026-07-18T09:30:00Z",
    outcome: "COMPLETED",
    modelId: "claude-sonnet-5",
    modelVersion: "20260301",
    correlationType: "PAYMENT",
    correlationId: "33333333-3333-3333-3333-333333333333",
    dispositionCount: 0,
    latencyMs: 1204,
    costMicros: 1250,
    costCurrency: "USD",
    advisory: false,
    ...overrides,
  };
}

describe("useMdrDecisions", () => {
  beforeEach(() => {
    getDecisionsMock.mockReset();
  });

  it("asks the worklist for the given ruling state and returns decisions as served", async () => {
    const newest = makeDecision({ seq: 40 });
    const older = makeDecision({
      decisionId: "44444444-4444-4444-4444-444444444444",
      seq: 12,
    });
    getDecisionsMock.mockResolvedValue({
      data: { decisions: [newest, older] },
    });

    const { result } = renderHook(
      () => useMdrDecisions({ rulingState: "AWAITING_RULING" }),
      { wrapper: wrapperOf(makeClient()) },
    );

    await waitFor(() =>
      expect(result.current.decisions).toEqual([newest, older]),
    );
    const firstArg = getDecisionsMock.mock.calls[0][0] as {
      query?: { rulingState?: string };
    };
    expect(firstArg.query).toMatchObject({ rulingState: "AWAITING_RULING" });
  });

  it("narrows to non-advisory decisions when asked, so read-only entries never read as work", async () => {
    getDecisionsMock.mockResolvedValue({ data: { decisions: [] } });

    renderHook(
      () =>
        useMdrDecisions({ rulingState: "AWAITING_RULING", advisory: false }),
      { wrapper: wrapperOf(makeClient()) },
    );

    await waitFor(() => expect(getDecisionsMock).toHaveBeenCalled());
    const firstArg = getDecisionsMock.mock.calls[0][0] as {
      query?: { rulingState?: string; advisory?: boolean };
    };
    expect(firstArg.query).toEqual({
      rulingState: "AWAITING_RULING",
      advisory: false,
    });
  });

  it("omits the advisory filter when unstated, so both kinds are served", async () => {
    getDecisionsMock.mockResolvedValue({ data: { decisions: [] } });

    renderHook(() => useMdrDecisions({ rulingState: "ACCEPT" }), {
      wrapper: wrapperOf(makeClient()),
    });

    await waitFor(() => expect(getDecisionsMock).toHaveBeenCalled());
    const firstArg = getDecisionsMock.mock.calls[0][0] as {
      query?: Record<string, unknown>;
    };
    expect(firstArg.query).not.toHaveProperty("advisory");
  });

  it("re-asks when the advisory filter changes", async () => {
    getDecisionsMock.mockResolvedValue({ data: { decisions: [] } });

    const { result, rerender } = renderHook(
      ({ advisory }: { advisory: boolean | undefined }) =>
        useMdrDecisions({ rulingState: "AWAITING_RULING", advisory }),
      {
        wrapper: wrapperOf(makeClient()),
        initialProps: { advisory: false as boolean | undefined },
      },
    );
    await waitFor(() => expect(result.current.decisions).toEqual([]));

    rerender({ advisory: undefined });

    await waitFor(() => expect(getDecisionsMock).toHaveBeenCalledTimes(2));
  });

  it("re-asks when the ruling state changes", async () => {
    getDecisionsMock.mockResolvedValue({ data: { decisions: [] } });

    const { result, rerender } = renderHook(
      ({ state }: { state: "AWAITING_RULING" | "ACCEPT" }) =>
        useMdrDecisions({ rulingState: state }),
      {
        wrapper: wrapperOf(makeClient()),
        initialProps: { state: "AWAITING_RULING" as const },
      },
    );
    await waitFor(() => expect(result.current.decisions).toEqual([]));

    rerender({ state: "ACCEPT" as never });

    await waitFor(() => expect(getDecisionsMock).toHaveBeenCalledTimes(2));
    const secondArg = getDecisionsMock.mock.calls[1][0] as {
      query?: { rulingState?: string };
    };
    expect(secondArg.query).toMatchObject({ rulingState: "ACCEPT" });
  });

  it("is pending while the worklist is in flight", () => {
    getDecisionsMock.mockImplementation(() => new Promise(() => {}));
    const { result } = renderHook(
      () => useMdrDecisions({ rulingState: "AWAITING_RULING" }),
      { wrapper: wrapperOf(makeClient()) },
    );
    expect(result.current.isPending).toBe(true);
    expect(result.current.decisions).toBeNull();
  });

  it("returns isError when the worklist request fails", async () => {
    getDecisionsMock.mockRejectedValue(new Error("boom"));
    const { result } = renderHook(
      () => useMdrDecisions({ rulingState: "AWAITING_RULING" }),
      { wrapper: wrapperOf(makeClient()) },
    );
    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.decisions).toBeNull();
  });
});
