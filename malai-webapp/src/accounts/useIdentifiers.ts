import { useQuery } from "@tanstack/react-query";
import { getApiV1AccountsByAccountUidIdentifiersOptions } from "@/api/generated/@tanstack/react-query.gen";
import type { AccountIdentifiersResponse } from "@/api/generated/types.gen";
import { useActiveAccountHolder } from "@/account-holder/useActiveAccountHolder";

export type UseIdentifiersResult = {
  identifiers: AccountIdentifiersResponse | null;
  isPending: boolean;
  isError: boolean;
  error: unknown;
};

export function useIdentifiers(accountUid: string): UseIdentifiersResult {
  const holder = useActiveAccountHolder();
  const activeHolderUid =
    holder.status === "ready" ? holder.activeHolder.accountHolderUid : "";

  const query = useQuery({
    ...getApiV1AccountsByAccountUidIdentifiersOptions({
      path: { accountUid },
      headers: { "MAL-Account-Holder-Id": activeHolderUid },
    }),
    enabled: holder.status === "ready" && accountUid !== "",
  });

  if (holder.status === "loading") {
    return { identifiers: null, isPending: true, isError: false, error: null };
  }
  if (holder.status === "error") {
    return {
      identifiers: null,
      isPending: false,
      isError: true,
      error: holder.error,
    };
  }

  if (accountUid === "") {
    return { identifiers: null, isPending: false, isError: false, error: null };
  }

  if (query.isPending) {
    return { identifiers: null, isPending: true, isError: false, error: null };
  }
  if (query.isError) {
    return {
      identifiers: null,
      isPending: false,
      isError: true,
      error: query.error,
    };
  }

  return {
    identifiers: query.data ?? null,
    isPending: false,
    isError: false,
    error: null,
  };
}
