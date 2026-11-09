import { useQuery } from "@tanstack/react-query";
import { getApiV1AccountsOptions } from "@/api/generated/@tanstack/react-query.gen";
import type { AccountResponse } from "@/api/generated/types.gen";
import { useActiveAccountHolder } from "@/account-holder/useActiveAccountHolder";

export type UseAccountsResult = {
  accounts: AccountResponse[] | null;
  isPending: boolean;
  isError: boolean;
  error: unknown;
};

export function useAccounts(): UseAccountsResult {
  const holder = useActiveAccountHolder();
  const activeHolderUid =
    holder.status === "ready" ? holder.activeHolder.accountHolderUid : "";

  const query = useQuery({
    ...getApiV1AccountsOptions({
      headers: { "MAL-Account-Holder-Id": activeHolderUid },
    }),
    enabled: holder.status === "ready",
  });

  if (holder.status === "loading") {
    return { accounts: null, isPending: true, isError: false, error: null };
  }
  if (holder.status === "error") {
    return {
      accounts: null,
      isPending: false,
      isError: true,
      error: holder.error,
    };
  }

  if (query.isPending) {
    return { accounts: null, isPending: true, isError: false, error: null };
  }
  if (query.isError) {
    return {
      accounts: null,
      isPending: false,
      isError: true,
      error: query.error,
    };
  }

  return {
    accounts: query.data?.accounts ?? [],
    isPending: false,
    isError: false,
    error: null,
  };
}
