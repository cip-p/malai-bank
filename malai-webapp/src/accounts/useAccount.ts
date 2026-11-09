import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  getApiV1AccountsOptions,
  getApiV1AccountsQueryKey,
} from "@/api/generated/@tanstack/react-query.gen";
import type {
  AccountResponse,
  AccountsResponse,
} from "@/api/generated/types.gen";
import { useActiveAccountHolder } from "@/account-holder/useActiveAccountHolder";

export type UseAccountStatus = "cache-hit" | "fetching" | "ready" | "not-found";

export type UseAccountResult = {
  account: AccountResponse | null;
  status: UseAccountStatus;
};

export function useAccount(accountUid: string): UseAccountResult {
  const holder = useActiveAccountHolder();
  const activeHolderUid =
    holder.status === "ready" ? holder.activeHolder.accountHolderUid : "";

  const queryClient = useQueryClient();
  const headers = { "MAL-Account-Holder-Id": activeHolderUid };

  const [snapshot, setSnapshot] = useState<{
    cachedAccount: AccountResponse | null;
  } | null>(null);
  if (snapshot === null && holder.status === "ready") {
    const cached = queryClient.getQueryData<AccountsResponse>(
      getApiV1AccountsQueryKey({ headers }),
    );
    const cachedAccount =
      cached?.accounts.find((a) => a.accountUid === accountUid) ?? null;
    setSnapshot({ cachedAccount });
  }

  const cachedAccount = snapshot?.cachedAccount ?? null;

  const query = useQuery({
    ...getApiV1AccountsOptions({ headers }),
    enabled: holder.status === "ready" && !cachedAccount,
  });

  if (holder.status !== "ready") {
    return { account: null, status: "fetching" };
  }

  if (cachedAccount) {
    return { account: cachedAccount, status: "cache-hit" };
  }

  if (query.isPending || query.isError) {
    return { account: null, status: "fetching" };
  }

  const account =
    query.data?.accounts.find((a) => a.accountUid === accountUid) ?? null;

  if (!account) {
    return { account: null, status: "not-found" };
  }

  return { account, status: "ready" };
}
