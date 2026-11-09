import { useQuery } from "@tanstack/react-query";
import { getApiV1AccountsByAccountUidCreditSummaryOptions } from "@/api/generated/@tanstack/react-query.gen";
import type { CreditSummaryResponse } from "@/api/generated/types.gen";
import { useActiveAccountHolder } from "@/account-holder/useActiveAccountHolder";

export type UseCreditSummaryResult = {
  summary: CreditSummaryResponse | null;
  isPending: boolean;
  isError: boolean;
  error: unknown;
};

export function useCreditSummary(accountUid: string): UseCreditSummaryResult {
  const holder = useActiveAccountHolder();
  const activeHolderUid =
    holder.status === "ready" ? holder.activeHolder.accountHolderUid : "";

  const query = useQuery({
    ...getApiV1AccountsByAccountUidCreditSummaryOptions({
      path: { accountUid },
      headers: { "MAL-Account-Holder-Id": activeHolderUid },
    }),
    enabled: holder.status === "ready" && accountUid !== "",
  });

  if (holder.status === "loading") {
    return { summary: null, isPending: true, isError: false, error: null };
  }
  if (holder.status === "error") {
    return {
      summary: null,
      isPending: false,
      isError: true,
      error: holder.error,
    };
  }

  if (accountUid === "") {
    return { summary: null, isPending: false, isError: false, error: null };
  }

  if (query.isPending) {
    return { summary: null, isPending: true, isError: false, error: null };
  }
  if (query.isError) {
    return {
      summary: null,
      isPending: false,
      isError: true,
      error: query.error,
    };
  }

  return {
    summary: query.data ?? null,
    isPending: false,
    isError: false,
    error: null,
  };
}
