import type { QueryClient } from "@tanstack/react-query";
import { getApiV1AccountsByAccountUidCreditSummaryQueryKey } from "@/api/generated/@tanstack/react-query.gen";
import { invalidateAccountMoneyData } from "@/accounts/invalidateAccountMoneyData";

export function invalidateCreditAccountData(
  queryClient: QueryClient,
  activeHolderUid: string,
  accountUid: string,
): void {
  invalidateAccountMoneyData(queryClient, activeHolderUid, accountUid);
  void queryClient.invalidateQueries({
    queryKey: getApiV1AccountsByAccountUidCreditSummaryQueryKey({
      path: { accountUid },
      headers: { "MAL-Account-Holder-Id": activeHolderUid },
    }),
  });
}
