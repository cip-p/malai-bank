import { beforeEach, describe, expect, it, vi } from "vitest";
import { act, renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import type { ExplanationResponse } from "@/api/generated/types.gen";

vi.mock("@/api/generated/sdk.gen", () => ({
  postApiV1AdminCopilotExplanations: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const explainMock =
  sdk.postApiV1AdminCopilotExplanations as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;

const { useExplanation } = await import("./useExplanation");

const SUBJECT_ID = "33333333-3333-3333-3333-333333333333";
const DECISION_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
const UUID_V4 =
  /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

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

function grounded(overrides: Partial<ExplanationResponse> = {}) {
  return {
    data: {
      decisionId: DECISION_ID,
      outcome: "COMPLETED",
      claims: [
        {
          text: "The payment was held pending screening.",
          sources: [{ type: "PAYMENT", id: SUBJECT_ID }],
        },
      ],
      ...overrides,
    } satisfies ExplanationResponse,
  };
}

function renderExplanation() {
  return renderHook(() => useExplanation(SUBJECT_ID), {
    wrapper: wrapperOf(makeClient()),
  });
}

function firstCall() {
  return explainMock.mock.calls[0][0] as {
    body?: { subjectType?: string; subjectId?: string };
    headers?: Record<string, string>;
  };
}

describe("useExplanation", () => {
  beforeEach(() => {
    explainMock.mockReset();
  });

  it("asks nothing until the reviewer asks", () => {
    const { result } = renderExplanation();
    expect(result.current.state).toEqual({ kind: "idle" });
    expect(explainMock).not.toHaveBeenCalled();
  });

  it("names the payment as the subject and serves the grounded claims", async () => {
    explainMock.mockResolvedValue(grounded());
    const { result } = renderExplanation();

    act(() => result.current.explain());

    await waitFor(() =>
      expect(result.current.state).toEqual({
        kind: "grounded",
        decisionId: DECISION_ID,
        claims: [
          {
            text: "The payment was held pending screening.",
            sources: [{ type: "PAYMENT", id: SUBJECT_ID }],
          },
        ],
      }),
    );
    expect(firstCall().body).toEqual({
      subjectType: "PAYMENT",
      subjectId: SUBJECT_ID,
    });
  });

  it("carries an Idempotency-Key, because an explanation appends to the chain and spends", async () => {
    explainMock.mockResolvedValue(grounded());
    const { result } = renderExplanation();

    act(() => result.current.explain());

    await waitFor(() => expect(explainMock).toHaveBeenCalled());
    expect(firstCall().headers?.["Idempotency-Key"]).toMatch(UUID_V4);
  });

  it("does not fork the record when the reviewer presses twice", async () => {
    explainMock.mockImplementation(() => new Promise(() => {}));
    const { result } = renderExplanation();

    act(() => result.current.explain());
    act(() => result.current.explain());

    await waitFor(() =>
      expect(result.current.state).toEqual({ kind: "asking" }),
    );
    expect(explainMock).toHaveBeenCalledTimes(1);
  });

  it("reports an answer that was produced and then refused for failing grounding", async () => {
    explainMock.mockResolvedValue({
      data: {
        decisionId: DECISION_ID,
        outcome: "COMPLETED",
        claims: [],
        refusalCode: "GROUNDING_FAILED",
      } satisfies ExplanationResponse,
    });
    const { result } = renderExplanation();

    act(() => result.current.explain());

    await waitFor(() =>
      expect(result.current.state).toEqual({
        kind: "refused",
        decisionId: DECISION_ID,
      }),
    );
  });

  it.each(["TIMED_OUT", "ERRORED"] as const)(
    "reports %s as no answer produced, against a record that exists",
    async (outcome) => {
      explainMock.mockResolvedValue({
        data: {
          decisionId: DECISION_ID,
          outcome,
          claims: [],
        } satisfies ExplanationResponse,
      });
      const { result } = renderExplanation();

      act(() => result.current.explain());

      await waitFor(() =>
        expect(result.current.state).toEqual({
          kind: "no-answer",
          decisionId: DECISION_ID,
          outcome,
        }),
      );
    },
  );

  it("reports an ask that never reached the record, which is not a refusal", async () => {
    explainMock.mockRejectedValue(new Error("boom"));
    const { result } = renderExplanation();

    act(() => result.current.explain());

    await waitFor(() =>
      expect(result.current.state).toEqual({ kind: "ask-failed" }),
    );
  });

  it("re-sends the same Idempotency-Key after a failed ask, so a landed invocation replays", async () => {
    explainMock.mockRejectedValueOnce(new Error("boom"));
    const { result } = renderExplanation();

    act(() => result.current.explain());
    await waitFor(() =>
      expect(result.current.state).toEqual({ kind: "ask-failed" }),
    );

    explainMock.mockResolvedValue(grounded());
    act(() => result.current.explain());
    await waitFor(() => expect(explainMock).toHaveBeenCalledTimes(2));

    const secondCall = explainMock.mock.calls[1][0] as {
      headers?: Record<string, string>;
    };
    expect(secondCall.headers?.["Idempotency-Key"]).toBe(
      firstCall().headers?.["Idempotency-Key"],
    );
  });
});
