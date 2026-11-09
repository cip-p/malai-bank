import { beforeEach, describe, expect, it, vi } from "vitest";
import { act, renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { HttpError } from "@/api/http";
import { createQueryClient } from "@/lib/queryClient";
import {
  getApiV1AdminAmlCasesByCaseIdQueryKey,
  getApiV1AdminAmlCasesQueryKey,
} from "@/api/generated/@tanstack/react-query.gen";

vi.mock("@/api/generated/sdk.gen", () => ({
  postApiV1AdminAmlCasesByCaseIdCloseDismissed: vi.fn(),
  postApiV1AdminAmlCasesByCaseIdCloseReported: vi.fn(),
}));

const toastErrorMock = vi.fn();
vi.mock("sonner", () => ({
  toast: { error: (...args: unknown[]) => toastErrorMock(...args) },
}));

const sdk = await import("@/api/generated/sdk.gen");
const dismissMock =
  sdk.postApiV1AdminAmlCasesByCaseIdCloseDismissed as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;
const reportMock =
  sdk.postApiV1AdminAmlCasesByCaseIdCloseReported as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;

const { useCloseCase } = await import("./useCloseCase");

const CASE_ID = "1a2b3c4d-1111-1111-1111-111111111111";
const KEY = "22222222-2222-4222-8222-222222222222";

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

function renderCloseCase(client = makeClient()) {
  return renderHook(() => useCloseCase(CASE_ID), {
    wrapper: wrapperOf(client),
  });
}

function closed(status: string) {
  return { data: { caseId: CASE_ID, status } };
}

function callAt(
  mock: ReturnType<typeof vi.fn<(...args: unknown[]) => unknown>>,
  index: number,
) {
  return mock.mock.calls[index][0] as {
    path?: { caseId?: string };
    headers?: Record<string, string>;
  };
}

describe("useCloseCase", () => {
  beforeEach(() => {
    dismissMock.mockReset();
    reportMock.mockReset();
    toastErrorMock.mockReset();
  });

  it("closes nothing until the officer fires", () => {
    const { result } = renderCloseCase();

    expect(result.current.pending).toBeNull();
    expect(result.current.outcome).toBeNull();
    expect(dismissMock).not.toHaveBeenCalled();
    expect(reportMock).not.toHaveBeenCalled();
  });

  it("dismisses the case named in the route, carrying the armed key", async () => {
    dismissMock.mockResolvedValue(closed("CLOSED_DISMISSED"));
    const { result } = renderCloseCase();

    act(() => result.current.close("dismissed", KEY));

    await waitFor(() => expect(dismissMock).toHaveBeenCalledTimes(1));
    expect(callAt(dismissMock, 0).path).toEqual({ caseId: CASE_ID });
    expect(callAt(dismissMock, 0).headers?.["Idempotency-Key"]).toBe(KEY);
    expect(reportMock).not.toHaveBeenCalled();
  });

  it("reports through its own door, where the Gate reads the SAR ruling", async () => {
    reportMock.mockResolvedValue(closed("CLOSED_REPORTED"));
    const { result } = renderCloseCase();

    act(() => result.current.close("reported", KEY));

    await waitFor(() => expect(reportMock).toHaveBeenCalledTimes(1));
    expect(callAt(reportMock, 0).path).toEqual({ caseId: CASE_ID });
    expect(callAt(reportMock, 0).headers?.["Idempotency-Key"]).toBe(KEY);
    expect(dismissMock).not.toHaveBeenCalled();
  });

  it("names the close in flight, and only that one", async () => {
    reportMock.mockImplementation(() => new Promise(() => {}));
    const { result } = renderCloseCase();

    act(() => result.current.close("reported", KEY));

    await waitFor(() => expect(result.current.pending).toBe("reported"));
  });

  it("serves a domain-coded refusal to the screen, mapped", async () => {
    dismissMock.mockRejectedValue(new HttpError(409, { message: "6990" }));
    const { result } = renderCloseCase();

    act(() => result.current.close("dismissed", KEY));

    await waitFor(() =>
      expect(result.current.outcome).toEqual({
        kind: "refused",
        reason: "accepted-alert-stands",
      }),
    );
  });

  it("keeps the refusal on the screen — the app-level toast stays silent", async () => {
    reportMock.mockRejectedValue(new HttpError(409, { message: "6989" }));
    const { result } = renderCloseCase(createQueryClient());

    act(() => result.current.close("reported", KEY));

    await waitFor(() =>
      expect(result.current.outcome).toEqual({
        kind: "refused",
        reason: "no-sar-ruling-in-force",
      }),
    );
    expect(toastErrorMock).not.toHaveBeenCalled();
  });

  it("clears the last refusal when the officer tries the other door", async () => {
    reportMock.mockRejectedValue(new HttpError(409, { message: "6989" }));
    dismissMock.mockImplementation(() => new Promise(() => {}));
    const { result } = renderCloseCase();

    act(() => result.current.close("reported", KEY));
    await waitFor(() => expect(result.current.outcome).not.toBeNull());

    act(() => result.current.close("dismissed", KEY));

    await waitFor(() => expect(result.current.pending).toBe("dismissed"));
    expect(result.current.outcome).toBeNull();
  });

  it("calls a failed request a failure, not a refusal", async () => {
    dismissMock.mockRejectedValue(new HttpError(500, { message: "9999" }));
    const { result } = renderCloseCase();

    act(() => result.current.close("dismissed", KEY));

    await waitFor(() =>
      expect(result.current.outcome).toEqual({ kind: "failed" }),
    );
  });

  it("refreshes the case the moment it closes, so the terminal state is not polled for", async () => {
    dismissMock.mockResolvedValue(closed("CLOSED_DISMISSED"));
    const client = makeClient();
    const invalidate = vi.spyOn(client, "invalidateQueries");
    const { result } = renderCloseCase(client);

    act(() => result.current.close("dismissed", KEY));

    await waitFor(() =>
      expect(invalidate).toHaveBeenCalledWith({
        queryKey: getApiV1AdminAmlCasesByCaseIdQueryKey({
          path: { caseId: CASE_ID },
        }),
      }),
    );
  });

  it("refreshes the Case list too — a terminal Case moves between its chips", async () => {
    reportMock.mockResolvedValue(closed("CLOSED_REPORTED"));
    const client = makeClient();
    const listingId = (getApiV1AdminAmlCasesQueryKey()[0] as { _id: string })
      ._id;
    const invalidate = vi.spyOn(client, "invalidateQueries");
    const { result } = renderCloseCase(client);

    act(() => result.current.close("reported", KEY));

    await waitFor(() => expect(invalidate).toHaveBeenCalled());
    const predicates = invalidate.mock.calls
      .map(([options]) => options as { predicate?: (q: unknown) => boolean })
      .filter((options) => options.predicate !== undefined);
    expect(
      predicates.some((options) =>
        options.predicate?.({ queryKey: [{ _id: listingId }] }),
      ),
    ).toBe(true);
  });
});
