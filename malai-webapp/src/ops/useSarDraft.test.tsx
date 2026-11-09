import { beforeEach, describe, expect, it, vi } from "vitest";
import { act, renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import type { AmlSarDraftResponse } from "@/api/generated/types.gen";
import { getApiV1AdminAmlCasesByCaseIdQueryKey } from "@/api/generated/@tanstack/react-query.gen";

vi.mock("@/api/generated/sdk.gen", () => ({
  postApiV1AdminAmlCasesByCaseIdSarDraft: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const draftMock =
  sdk.postApiV1AdminAmlCasesByCaseIdSarDraft as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;

const { useSarDraft } = await import("./useSarDraft");

const CASE_ID = "1a2b3c4d-1111-1111-1111-111111111111";
const DECISION_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
const NARRATIVE = "Three payments of £3,400 to one beneficiary in two days.";
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

function drafted(overrides: Partial<AmlSarDraftResponse> = {}) {
  return {
    data: {
      decisionId: DECISION_ID,
      outcome: "COMPLETED",
      narrative: NARRATIVE,
      ...overrides,
    } satisfies AmlSarDraftResponse,
  };
}

function renderSarDraft(client = makeClient()) {
  return renderHook(() => useSarDraft(CASE_ID), { wrapper: wrapperOf(client) });
}

function callAt(index: number) {
  return draftMock.mock.calls[index][0] as {
    path?: { caseId?: string };
    headers?: Record<string, string>;
  };
}

describe("useSarDraft", () => {
  beforeEach(() => {
    draftMock.mockReset();
  });

  it("drafts nothing until the officer asks", () => {
    const { result } = renderSarDraft();
    expect(result.current.invocation).toEqual({ kind: "idle" });
    expect(draftMock).not.toHaveBeenCalled();
  });

  it("names the case in the path and serves the invocation response whole", async () => {
    draftMock.mockResolvedValue(drafted());
    const { result } = renderSarDraft();

    act(() => result.current.requestDraft());

    await waitFor(() =>
      expect(result.current.invocation).toEqual({
        kind: "served",
        response: {
          decisionId: DECISION_ID,
          outcome: "COMPLETED",
          narrative: NARRATIVE,
        },
      }),
    );
    expect(callAt(0).path).toEqual({ caseId: CASE_ID });
  });

  it("carries an Idempotency-Key, because a draft appends to the chain and spends", async () => {
    draftMock.mockResolvedValue(drafted());
    const { result } = renderSarDraft();

    act(() => result.current.requestDraft());

    await waitFor(() => expect(draftMock).toHaveBeenCalled());
    expect(callAt(0).headers?.["Idempotency-Key"]).toMatch(UUID_V4);
  });

  it("reports the invocation in flight", async () => {
    draftMock.mockImplementation(() => new Promise(() => {}));
    const { result } = renderSarDraft();

    act(() => result.current.requestDraft());

    await waitFor(() =>
      expect(result.current.invocation).toEqual({ kind: "drafting" }),
    );
  });

  it("does not fork the record when the officer presses twice", async () => {
    draftMock.mockImplementation(() => new Promise(() => {}));
    const { result } = renderSarDraft();

    act(() => result.current.requestDraft());
    act(() => result.current.requestDraft());

    await waitFor(() =>
      expect(result.current.invocation).toEqual({ kind: "drafting" }),
    );
    expect(draftMock).toHaveBeenCalledTimes(1);
  });

  it("reports a request that never reached a record", async () => {
    draftMock.mockRejectedValue(new Error("boom"));
    const { result } = renderSarDraft();

    act(() => result.current.requestDraft());

    await waitFor(() =>
      expect(result.current.invocation).toEqual({ kind: "failed" }),
    );
  });

  it("re-sends the same Idempotency-Key after a failed request, so a landed draft replays", async () => {
    draftMock.mockRejectedValueOnce(new Error("boom"));
    const { result } = renderSarDraft();

    act(() => result.current.requestDraft());
    await waitFor(() =>
      expect(result.current.invocation).toEqual({ kind: "failed" }),
    );

    draftMock.mockResolvedValue(drafted());
    act(() => result.current.requestDraft());
    await waitFor(() => expect(draftMock).toHaveBeenCalledTimes(2));

    expect(callAt(1).headers?.["Idempotency-Key"]).toBe(
      callAt(0).headers?.["Idempotency-Key"],
    );
  });

  it("mints a fresh key for a new invocation on a new mount", async () => {
    draftMock.mockResolvedValue(drafted());
    const first = renderSarDraft();
    act(() => first.result.current.requestDraft());
    await waitFor(() => expect(draftMock).toHaveBeenCalledTimes(1));
    first.unmount();

    const second = renderSarDraft();
    act(() => second.result.current.requestDraft());
    await waitFor(() => expect(draftMock).toHaveBeenCalledTimes(2));

    expect(callAt(1).headers?.["Idempotency-Key"]).not.toBe(
      callAt(0).headers?.["Idempotency-Key"],
    );
  });

  it("refreshes the case, because requesting a draft moves the investigation on", async () => {
    draftMock.mockResolvedValue(drafted());
    const client = makeClient();
    const invalidate = vi.spyOn(client, "invalidateQueries");
    const { result } = renderSarDraft(client);

    act(() => result.current.requestDraft());

    await waitFor(() =>
      expect(invalidate).toHaveBeenCalledWith({
        queryKey: getApiV1AdminAmlCasesByCaseIdQueryKey({
          path: { caseId: CASE_ID },
        }),
      }),
    );
  });
});
