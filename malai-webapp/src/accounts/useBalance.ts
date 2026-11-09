import { useQuery } from "@tanstack/react-query";
import { getApiV1AccountsByAccountUidBalanceOptions } from "@/api/generated/@tanstack/react-query.gen";
import type { BalanceResponse } from "@/api/generated/types.gen";
import { useActiveAccountHolder } from "@/account-holder/useActiveAccountHolder";

export type UseBalanceResult = {
  balance: BalanceResponse | null;
  isPending: boolean;
  isError: boolean;
  error: unknown;
};

export function useBalance(accountUid: string): UseBalanceResult {
  const holder = useActiveAccountHolder();
  const activeHolderUid =
    holder.status === "ready" ? holder.activeHolder.accountHolderUid : "";

  const query = useQuery({
    ...getApiV1AccountsByAccountUidBalanceOptions({
      path: { accountUid },
      headers: { "MAL-Account-Holder-Id": activeHolderUid },
    }),
    enabled: holder.status === "ready" && accountUid !== "",
  });

  if (holder.status === "loading") {
    return { balance: null, isPending: true, isError: false, error: null };
  }
  if (holder.status === "error") {
    return {
      balance: null,
      isPending: false,
      isError: true,
      error: holder.error,
    };
  }

  if (accountUid === "") {
    return { balance: null, isPending: false, isError: false, error: null };
  }

  if (query.isPending) {
    return { balance: null, isPending: true, isError: false, error: null };
  }
  if (query.isError) {
    return {
      balance: null,
      isPending: false,
      isError: true,
      error: query.error,
    };
  }

  return {
    balance: query.data ?? null,
    isPending: false,
    isError: false,
    error: null,
  };
}
