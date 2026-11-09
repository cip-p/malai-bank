import { useQuery } from "@tanstack/react-query";
import { getApiV1AccountsByAccountUidForecastOptions } from "@/api/generated/@tanstack/react-query.gen";
import type { BalanceForecastResponse } from "@/api/generated/types.gen";
import { useActiveAccountHolder } from "@/account-holder/useActiveAccountHolder";
import { isHttpStatus } from "@/api/http";

export type UseForecastResult = {
  forecast: BalanceForecastResponse | null;
  isPending: boolean;
  isError: boolean;
  isUnavailable: boolean;
  error: unknown;
};

export function useForecast(accountUid: string): UseForecastResult {
  const holder = useActiveAccountHolder();
  const activeHolderUid =
    holder.status === "ready" ? holder.activeHolder.accountHolderUid : "";

  const query = useQuery({
    ...getApiV1AccountsByAccountUidForecastOptions({
      path: { accountUid },
      headers: { "MAL-Account-Holder-Id": activeHolderUid },
    }),
    enabled: holder.status === "ready",
  });

  if (holder.status === "loading") {
    return {
      forecast: null,
      isPending: true,
      isError: false,
      isUnavailable: false,
      error: null,
    };
  }
  if (holder.status === "error") {
    return {
      forecast: null,
      isPending: false,
      isError: true,
      isUnavailable: false,
      error: holder.error,
    };
  }

  if (query.isPending) {
    return {
      forecast: null,
      isPending: true,
      isError: false,
      isUnavailable: false,
      error: null,
    };
  }
  if (query.isError) {
    return {
      forecast: null,
      isPending: false,
      isError: true,
      isUnavailable: isHttpStatus(query.error, 503),
      error: query.error,
    };
  }

  return {
    forecast: query.data ?? null,
    isPending: false,
    isError: false,
    isUnavailable: false,
    error: null,
  };
}
