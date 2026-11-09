import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import type {
  AccountHoldersResponse,
  BalanceForecastResponse,
} from "@/api/generated/types.gen";
import { HttpError } from "@/api/http";
import { useActiveHolderOverrideStore } from "@/account-holder/useActiveHolderOverrideStore";
import { useForecast } from "./useForecast";

vi.mock("@/api/generated/sdk.gen", () => ({
  getApiV1AccountHolderAll: vi.fn(),
  getApiV1AccountsByAccountUidForecast: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const getApiV1AccountHolderAllMock =
  sdk.getApiV1AccountHolderAll as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;
const getApiV1AccountsByAccountUidForecastMock =
  sdk.getApiV1AccountsByAccountUidForecast as unknown as ReturnType<
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

const ACCOUNT_UID = "22222222-2222-2222-2222-222222222222";
const INDIVIDUAL_UID = "11111111-1111-1111-1111-111111111111";

const HOLDERS: AccountHoldersResponse = {
  accountHolders: [
    {
      accountHolderUid: INDIVIDUAL_UID,
      accountHolderName: "Ada Lovelace",
      accountHolderType: "INDIVIDUAL",
    },
  ],
};

const FORECAST: BalanceForecastResponse = {
  accountUid: ACCOUNT_UID,
  month: "2026-07",
  windowStart: "2026-07-01",
  windowEnd: "2026-07-31",
  asOfDate: "2026-07-09",
  currency: "GBP",
  availableBalanceMinorUnits: 120000,
  completedTotalMinorUnits: -20000,
  expectedTotalMinorUnits: -50000,
  forecastBalanceMinorUnits: 70000,
  completed: [],
  expected: [],
};

describe("useForecast", () => {
  beforeEach(() => {
    getApiV1AccountHolderAllMock.mockReset();
    getApiV1AccountsByAccountUidForecastMock.mockReset();
    useActiveHolderOverrideStore.setState({ override: null });
  });

  it("requests the forecast with the account and active holder header, no month", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    getApiV1AccountsByAccountUidForecastMock.mockResolvedValue({
      data: FORECAST,
    });

    const { result } = renderHook(() => useForecast(ACCOUNT_UID), {
      wrapper: wrapperOf(makeClient()),
    });

    await waitFor(() => expect(result.current.forecast).toEqual(FORECAST));

    const firstArg = getApiV1AccountsByAccountUidForecastMock.mock
      .calls[0]?.[0] as {
      path?: Record<string, string>;
      query?: Record<string, string>;
      headers?: Record<string, string>;
    };
    expect(firstArg.path).toMatchObject({ accountUid: ACCOUNT_UID });
    expect(firstArg.query).toBeUndefined();
    expect(firstArg.headers).toMatchObject({
      "MAL-Account-Holder-Id": INDIVIDUAL_UID,
    });
  });

  it("does not query until the active holder is ready", () => {
    getApiV1AccountHolderAllMock.mockImplementation(
      () => new Promise(() => {}),
    );

    const { result } = renderHook(() => useForecast(ACCOUNT_UID), {
      wrapper: wrapperOf(makeClient()),
    });

    expect(result.current.isPending).toBe(true);
    expect(getApiV1AccountsByAccountUidForecastMock).not.toHaveBeenCalled();
  });

  it("flags a 503 as unavailable", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    getApiV1AccountsByAccountUidForecastMock.mockRejectedValue(
      new HttpError(503, { message: "forecast down" }),
    );

    const { result } = renderHook(() => useForecast(ACCOUNT_UID), {
      wrapper: wrapperOf(makeClient()),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.isUnavailable).toBe(true);
    expect(result.current.forecast).toBeNull();
  });

  it("does not flag other errors as unavailable", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    getApiV1AccountsByAccountUidForecastMock.mockRejectedValue(
      new HttpError(404, { message: "no account" }),
    );

    const { result } = renderHook(() => useForecast(ACCOUNT_UID), {
      wrapper: wrapperOf(makeClient()),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.isUnavailable).toBe(false);
  });
});
