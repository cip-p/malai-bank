import type { QueryClient } from "@tanstack/react-query";
import {
  getApiV1AccountsByAccountUidBalanceQueryKey,
  getApiV1AccountsByAccountUidCreditSummaryQueryKey,
  getApiV1AccountsByAccountUidTransactionsQueryKey,
  getApiV1AccountsQueryKey,
} from "@/api/generated/@tanstack/react-query.gen";

const PROBE = {
  path: { accountUid: "probe" },
  headers: { "MAL-Account-Holder-Id": "probe" },
};

const MONEY_QUERY_IDS = new Set([
  getApiV1AccountsQueryKey({ headers: PROBE.headers })[0]._id,
  getApiV1AccountsByAccountUidBalanceQueryKey(PROBE)[0]._id,
  getApiV1AccountsByAccountUidTransactionsQueryKey(PROBE)[0]._id,
  getApiV1AccountsByAccountUidCreditSummaryQueryKey(PROBE)[0]._id,
]);

export function invalidateAllAccountMoneyData(queryClient: QueryClient): void {
  void queryClient.invalidateQueries({
    predicate: (query) => {
      const key = query.queryKey[0] as { _id?: string } | undefined;
      return key?._id !== undefined && MONEY_QUERY_IDS.has(key._id);
    },
  });
}
