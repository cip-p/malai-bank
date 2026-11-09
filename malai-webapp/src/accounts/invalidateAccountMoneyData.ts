import type { QueryClient } from "@tanstack/react-query";
import {
  getApiV1AccountsByAccountUidBalanceQueryKey,
  getApiV1AccountsByAccountUidTransactionsQueryKey,
  getApiV1AccountsQueryKey,
} from "@/api/generated/@tanstack/react-query.gen";

export function invalidateAccountMoneyData(
  queryClient: QueryClient,
  activeHolderUid: string,
  accountUid: string,
): void {
  const headers = { "MAL-Account-Holder-Id": activeHolderUid };
  void queryClient.invalidateQueries({
    queryKey: getApiV1AccountsQueryKey({ headers }),
  });
  void queryClient.invalidateQueries({
    queryKey: getApiV1AccountsByAccountUidBalanceQueryKey({
      path: { accountUid },
      headers,
    }),
  });
  void queryClient.invalidateQueries({
    queryKey: getApiV1AccountsByAccountUidTransactionsQueryKey({
      path: { accountUid },
      headers,
    }),
  });
}
