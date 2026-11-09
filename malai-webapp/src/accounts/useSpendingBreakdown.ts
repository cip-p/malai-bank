import { useQuery } from "@tanstack/react-query";
import { getApiV1AccountsByAccountUidSpendingOptions } from "@/api/generated/@tanstack/react-query.gen";
import type { SpendingBreakdownResponse } from "@/api/generated/types.gen";
import { useActiveAccountHolder } from "@/account-holder/useActiveAccountHolder";
import { isHttpStatus } from "@/api/http";

export type UseSpendingBreakdownResult = {
  breakdown: SpendingBreakdownResponse | null;
  isPending: boolean;
  isError: boolean;
  isUnavailable: boolean;
  error: unknown;
};

export function useSpendingBreakdown(
  accountUid: string,
  month: string,
): UseSpendingBreakdownResult {
  const holder = useActiveAccountHolder();
  const activeHolderUid =
    holder.status === "ready" ? holder.activeHolder.accountHolderUid : "";

  const query = useQuery({
    ...getApiV1AccountsByAccountUidSpendingOptions({
      path: { accountUid },
      query: { month },
      headers: { "MAL-Account-Holder-Id": activeHolderUid },
    }),
    enabled: holder.status === "ready",
  });

  if (holder.status === "loading") {
    return {
      breakdown: null,
      isPending: true,
      isError: false,
      isUnavailable: false,
      error: null,
    };
  }
  if (holder.status === "error") {
    return {
      breakdown: null,
      isPending: false,
      isError: true,
      isUnavailable: false,
      error: holder.error,
    };
  }

  if (query.isPending) {
    return {
      breakdown: null,
      isPending: true,
      isError: false,
      isUnavailable: false,
      error: null,
    };
  }
  if (query.isError) {
    return {
      breakdown: null,
      isPending: false,
      isError: true,
      isUnavailable: isHttpStatus(query.error, 503),
      error: query.error,
    };
  }

  return {
    breakdown: query.data ?? null,
    isPending: false,
    isError: false,
    isUnavailable: false,
    error: null,
  };
}
