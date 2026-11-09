import { useQuery } from "@tanstack/react-query";
import { getApiV1PayeesOptions } from "@/api/generated/@tanstack/react-query.gen";
import type { PayeeResponse } from "@/api/generated/types.gen";
import { useActiveAccountHolder } from "@/account-holder/useActiveAccountHolder";

export type UsePayeesResult = {
  payees: PayeeResponse[] | null;
  isPending: boolean;
  isError: boolean;
  error: unknown;
};

export function usePayees(): UsePayeesResult {
  const holder = useActiveAccountHolder();
  const activeHolderUid =
    holder.status === "ready" ? holder.activeHolder.accountHolderUid : "";

  const query = useQuery({
    ...getApiV1PayeesOptions({
      headers: { "MAL-Account-Holder-Id": activeHolderUid },
    }),
    enabled: holder.status === "ready",
  });

  if (holder.status === "loading") {
    return { payees: null, isPending: true, isError: false, error: null };
  }
  if (holder.status === "error") {
    return {
      payees: null,
      isPending: false,
      isError: true,
      error: holder.error,
    };
  }

  if (query.isPending) {
    return { payees: null, isPending: true, isError: false, error: null };
  }
  if (query.isError) {
    return {
      payees: null,
      isPending: false,
      isError: true,
      error: query.error,
    };
  }

  return {
    payees: query.data?.payees ?? [],
    isPending: false,
    isError: false,
    error: null,
  };
}
