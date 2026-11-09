import { useQuery } from "@tanstack/react-query";
import { getApiV1AccountsByAccountUidTransactionsOptions } from "@/api/generated/@tanstack/react-query.gen";
import type { TransactionItemResponse } from "@/api/generated/types.gen";
import { useActiveAccountHolder } from "@/account-holder/useActiveAccountHolder";

export type UseTransactionsResult = {
  transactions: TransactionItemResponse[] | null;
  isPending: boolean;
  isError: boolean;
  error: unknown;
};

export function useTransactions(accountUid: string): UseTransactionsResult {
  const holder = useActiveAccountHolder();
  const activeHolderUid =
    holder.status === "ready" ? holder.activeHolder.accountHolderUid : "";

  const query = useQuery({
    ...getApiV1AccountsByAccountUidTransactionsOptions({
      path: { accountUid },
      headers: { "MAL-Account-Holder-Id": activeHolderUid },
    }),
    enabled: holder.status === "ready",
  });

  if (holder.status === "loading") {
    return {
      transactions: null,
      isPending: true,
      isError: false,
      error: null,
    };
  }
  if (holder.status === "error") {
    return {
      transactions: null,
      isPending: false,
      isError: true,
      error: holder.error,
    };
  }

  if (query.isPending) {
    return {
      transactions: null,
      isPending: true,
      isError: false,
      error: null,
    };
  }
  if (query.isError) {
    return {
      transactions: null,
      isPending: false,
      isError: true,
      error: query.error,
    };
  }

  return {
    transactions: query.data?.transactions ?? [],
    isPending: false,
    isError: false,
    error: null,
  };
}
