import { useQuery } from "@tanstack/react-query";
import { getApiV1DirectDebitsOptions } from "@/api/generated/@tanstack/react-query.gen";
import type { DirectDebitResponse } from "@/api/generated/types.gen";
import { useActiveAccountHolder } from "@/account-holder/useActiveAccountHolder";

export type UseDirectDebitsResult = {
  directDebits: DirectDebitResponse[] | null;
  isPending: boolean;
  isError: boolean;
  error: unknown;
};

export function useDirectDebits(): UseDirectDebitsResult {
  const holder = useActiveAccountHolder();
  const activeHolderUid =
    holder.status === "ready" ? holder.activeHolder.accountHolderUid : "";

  const query = useQuery({
    ...getApiV1DirectDebitsOptions({
      headers: { "MAL-Account-Holder-Id": activeHolderUid },
    }),
    enabled: holder.status === "ready",
  });

  if (holder.status === "loading") {
    return { directDebits: null, isPending: true, isError: false, error: null };
  }
  if (holder.status === "error") {
    return {
      directDebits: null,
      isPending: false,
      isError: true,
      error: holder.error,
    };
  }

  if (query.isPending) {
    return { directDebits: null, isPending: true, isError: false, error: null };
  }
  if (query.isError) {
    return {
      directDebits: null,
      isPending: false,
      isError: true,
      error: query.error,
    };
  }

  return {
    directDebits: query.data?.directDebits ?? [],
    isPending: false,
    isError: false,
    error: null,
  };
}
