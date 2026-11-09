import { beforeEach, describe, expect, it, vi } from "vitest";
import { act, renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { Query } from "@tanstack/react-query";
import type { ReactNode } from "react";
import {
  getApiV1AdminMdrDecisionsByDecisionIdQueryKey,
  getApiV1AdminMdrDecisionsQueryKey,
} from "@/api/generated/@tanstack/react-query.gen";
import { HttpError } from "@/api/http";
import { createQueryClient } from "@/lib/queryClient";

vi.mock("@/api/generated/sdk.gen", () => ({
  postApiV1AdminMdrDecisionsByDecisionIdDispositions: vi.fn(),
}));

const toastErrorMock = vi.fn();
vi.mock("sonner", () => ({
  toast: { error: (...args: unknown[]) => toastErrorMock(...args) },
}));

const sdk = await import("@/api/generated/sdk.gen");
const appendMock =
  sdk.postApiV1AdminMdrDecisionsByDecisionIdDispositions as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;

const { useRulingDraft } = await import("./useRulingDraft");

const DECISION_ID = "11111111-1111-1111-1111-111111111111";
const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

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

describe("useRulingDraft", () => {
  beforeEach(() => {
    appendMock.mockReset();
  });

  it("mints an Entry id when the draft opens and holds it while the draft lives", () => {
    const { result, rerender } = renderHook(() => useRulingDraft(DECISION_ID), {
      wrapper: wrapperOf(makeClient()),
    });

    expect(result.current.isOpen).toBe(false);
    expect(result.current.entryId).toBeNull();

    act(() => result.current.open());

    expect(result.current.isOpen).toBe(true);
    expect(result.current.entryId).toMatch(UUID_PATTERN);
    const minted = result.current.entryId;

    rerender();
    expect(result.current.entryId).toBe(minted);
  });

  it("appends an accept ruling carrying the ruling alone, then closes the draft", async () => {
    appendMock.mockResolvedValue({
      data: { entryId: "x", decisionId: DECISION_ID },
    });
    const { result } = renderHook(() => useRulingDraft(DECISION_ID), {
      wrapper: wrapperOf(makeClient()),
    });

    act(() => result.current.open());
    const minted = result.current.entryId;
    act(() => result.current.appendAccept());

    await waitFor(() => expect(result.current.isOpen).toBe(false));
    expect(result.current.entryId).toBeNull();

    const call = appendMock.mock.calls[0]?.[0] as {
      path?: Record<string, string>;
      body?: Record<string, unknown>;
    };
    expect(call.path).toMatchObject({ decisionId: DECISION_ID });
    expect(call.body).toEqual({ entryId: minted, disposition: "ACCEPT" });
  });

  it("appends an edit ruling carrying the full replacement output, and only then", async () => {
    appendMock.mockResolvedValue({ data: {} });
    const { result } = renderHook(() => useRulingDraft(DECISION_ID), {
      wrapper: wrapperOf(makeClient()),
    });

    act(() => result.current.open());
    const minted = result.current.entryId;
    act(() => result.current.appendEdit("Two anomalies found."));

    await waitFor(() => expect(result.current.isOpen).toBe(false));
    const editCall = appendMock.mock.calls[0]?.[0] as {
      body?: Record<string, unknown>;
    };
    expect(editCall.body).toEqual({
      entryId: minted,
      disposition: "EDIT",
      editedOutput: "Two anomalies found.",
    });

    act(() => result.current.open());
    const reminted = result.current.entryId;
    act(() => result.current.appendReject());

    await waitFor(() => expect(result.current.isOpen).toBe(false));
    const rejectCall = appendMock.mock.calls[1]?.[0] as {
      body?: Record<string, unknown>;
    };
    expect(rejectCall.body).toEqual({
      entryId: reminted,
      disposition: "REJECT",
    });
  });

  it("keeps the draft open on failure and re-sends the same Entry id on retry", async () => {
    appendMock.mockRejectedValueOnce(new HttpError(500, { message: "boom" }));
    appendMock.mockResolvedValueOnce({ data: {} });
    const { result } = renderHook(() => useRulingDraft(DECISION_ID), {
      wrapper: wrapperOf(makeClient()),
    });

    act(() => result.current.open());
    const minted = result.current.entryId;
    act(() => result.current.appendAccept());

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.isOpen).toBe(true);
    expect(result.current.entryId).toBe(minted);

    act(() => result.current.appendAccept());

    await waitFor(() => expect(result.current.isOpen).toBe(false));
    expect(result.current.isError).toBe(false);
    const bodies = appendMock.mock.calls.map(
      (call) => (call[0] as { body?: { entryId?: string } }).body?.entryId,
    );
    expect(bodies).toEqual([minted, minted]);
  });

  it("refetches the decision's thread and every worklist slice after a successful append", async () => {
    appendMock.mockResolvedValue({ data: {} });
    const client = makeClient();
    const invalidateSpy = vi.spyOn(client, "invalidateQueries");
    const { result } = renderHook(() => useRulingDraft(DECISION_ID), {
      wrapper: wrapperOf(client),
    });

    act(() => result.current.open());
    act(() => result.current.appendAccept());
    await waitFor(() => expect(result.current.isOpen).toBe(false));

    const threadKey = getApiV1AdminMdrDecisionsByDecisionIdQueryKey({
      path: { decisionId: DECISION_ID },
    });
    expect(
      invalidateSpy.mock.calls.some(
        ([opts]) =>
          JSON.stringify(
            (opts as { queryKey?: unknown } | undefined)?.queryKey,
          ) === JSON.stringify(threadKey),
      ),
    ).toBe(true);

    const predicateCall = invalidateSpy.mock.calls.find(
      ([opts]) =>
        typeof (opts as { predicate?: unknown } | undefined)?.predicate ===
        "function",
    );
    expect(predicateCall).toBeDefined();
    const predicate = (
      predicateCall?.[0] as { predicate: (query: Query) => boolean }
    ).predicate;
    for (const rulingState of [
      "AWAITING_RULING",
      "ACCEPT",
      "EDIT",
      "REJECT",
    ] as const) {
      const worklistKey = getApiV1AdminMdrDecisionsQueryKey({
        query: { rulingState },
      });
      expect(predicate({ queryKey: worklistKey } as unknown as Query)).toBe(
        true,
      );
    }
    expect(predicate({ queryKey: threadKey } as unknown as Query)).toBe(false);
  });

  it("surfaces a failed append to the draft alone — the app-level toast stays silent", async () => {
    appendMock.mockRejectedValue(new HttpError(500, { message: "boom" }));
    const client = createQueryClient();
    const { result } = renderHook(() => useRulingDraft(DECISION_ID), {
      wrapper: wrapperOf(client),
    });

    act(() => result.current.open());
    act(() => result.current.appendAccept());

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(toastErrorMock).not.toHaveBeenCalled();
  });

  it("discards a draft without appending; the next draft is a fresh Entry with no stale failure", async () => {
    appendMock.mockRejectedValue(new HttpError(500, { message: "boom" }));
    const { result } = renderHook(() => useRulingDraft(DECISION_ID), {
      wrapper: wrapperOf(makeClient()),
    });

    act(() => result.current.open());
    const first = result.current.entryId;
    act(() => result.current.appendAccept());
    await waitFor(() => expect(result.current.isError).toBe(true));

    act(() => result.current.discard());
    expect(result.current.isOpen).toBe(false);
    expect(result.current.entryId).toBeNull();
    expect(result.current.isError).toBe(false);

    act(() => result.current.open());
    expect(result.current.entryId).toMatch(UUID_PATTERN);
    expect(result.current.entryId).not.toBe(first);
    expect(appendMock).toHaveBeenCalledTimes(1);
  });

  it("is pending while an append is in flight, with the draft still open", async () => {
    appendMock.mockImplementation(() => new Promise(() => {}));
    const { result } = renderHook(() => useRulingDraft(DECISION_ID), {
      wrapper: wrapperOf(makeClient()),
    });

    act(() => result.current.open());
    expect(result.current.isPending).toBe(false);
    act(() => result.current.appendReject());

    await waitFor(() => expect(result.current.isPending).toBe(true));
    expect(result.current.isOpen).toBe(true);
  });
});
