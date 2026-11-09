import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import type {
  MdrDecisionThreadResponse,
  MdrModelDecisionView,
} from "@/api/generated/types.gen";
import { HttpError } from "@/api/http";

vi.mock("@/api/generated/sdk.gen", () => ({
  getApiV1AdminMdrDecisionsByDecisionId: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const getThreadMock =
  sdk.getApiV1AdminMdrDecisionsByDecisionId as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;

const { useMdrDecisionThread } = await import("./useMdrDecisionThread");

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

function makeModelDecision(
  overrides: Partial<MdrModelDecisionView>,
): MdrModelDecisionView {
  return {
    entryId: "22222222-2222-2222-2222-222222222222",
    seq: 12,
    ts: "2026-07-18T09:30:00Z",
    outcome: "COMPLETED",
    modelId: "claude-sonnet-5",
    modelVersion: "20260301",
    promptTemplateId: "payment-triage",
    promptTemplateVersion: "3",
    promptTemplateHash: "ab12cd34",
    renderedPrompt: "Assess this payment.",
    rawOutput: "Looks fine.",
    latencyMs: 1204,
    costMicros: 1250,
    costCurrency: "USD",
    advisory: false,
    ...overrides,
  };
}

function makeThread(
  overrides: Partial<MdrDecisionThreadResponse>,
): MdrDecisionThreadResponse {
  return {
    decisionId: "11111111-1111-1111-1111-111111111111",
    correlationType: "PAYMENT",
    correlationId: "33333333-3333-3333-3333-333333333333",
    modelDecision: makeModelDecision({}),
    dispositions: [],
    ...overrides,
  };
}

describe("useMdrDecisionThread", () => {
  beforeEach(() => {
    getThreadMock.mockReset();
  });

  it("asks for the decision's thread by id and returns it as served", async () => {
    const thread = makeThread({});
    getThreadMock.mockResolvedValue({ data: thread });

    const { result } = renderHook(
      () => useMdrDecisionThread("11111111-1111-1111-1111-111111111111"),
      { wrapper: wrapperOf(makeClient()) },
    );

    await waitFor(() => expect(result.current.thread).toEqual(thread));
    const firstArg = getThreadMock.mock.calls[0][0] as {
      path?: { decisionId?: string };
    };
    expect(firstArg.path).toMatchObject({
      decisionId: "11111111-1111-1111-1111-111111111111",
    });
  });

  it("reports an unknown decision id as not found, not as an error", async () => {
    getThreadMock.mockRejectedValue(
      new HttpError(404, { message: "No such decision" }),
    );

    const { result } = renderHook(
      () => useMdrDecisionThread("99999999-9999-9999-9999-999999999999"),
      { wrapper: wrapperOf(makeClient()) },
    );

    await waitFor(() => expect(result.current.isNotFound).toBe(true));
    expect(result.current.isError).toBe(false);
    expect(result.current.thread).toBeNull();
  });

  it("is pending while the thread is in flight", () => {
    getThreadMock.mockImplementation(() => new Promise(() => {}));
    const { result } = renderHook(
      () => useMdrDecisionThread("11111111-1111-1111-1111-111111111111"),
      { wrapper: wrapperOf(makeClient()) },
    );
    expect(result.current.isPending).toBe(true);
    expect(result.current.thread).toBeNull();
  });

  it("stays an ordinary error for any other failure", async () => {
    getThreadMock.mockRejectedValue(new HttpError(500, { message: "boom" }));
    const { result } = renderHook(
      () => useMdrDecisionThread("11111111-1111-1111-1111-111111111111"),
      { wrapper: wrapperOf(makeClient()) },
    );
    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.isNotFound).toBe(false);
    expect(result.current.thread).toBeNull();
  });
});
