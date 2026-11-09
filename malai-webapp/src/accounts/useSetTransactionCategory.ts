import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  deleteApiV1AccountsByAccountUidTransactionsByEntryIdCategoryMutation,
  getApiV1AccountsByAccountUidSpendingQueryKey,
  getApiV1AccountsByAccountUidTransactionsQueryKey,
  putApiV1AccountsByAccountUidTransactionsByEntryIdCategoryMutation,
} from "@/api/generated/@tanstack/react-query.gen";
import type { Category } from "@/api/generated/types.gen";
import { useActiveAccountHolder } from "@/account-holder/useActiveAccountHolder";

export type UseSetTransactionCategoryResult = {
  setCategory: (entryId: string, category: Category) => void;
  clearCategory: (entryId: string) => void;
  isPending: boolean;
};

export function useSetTransactionCategory(
  accountUid: string,
): UseSetTransactionCategoryResult {
  const holder = useActiveAccountHolder();
  const activeHolderUid =
    holder.status === "ready" ? holder.activeHolder.accountHolderUid : "";
  const queryClient = useQueryClient();

  function invalidateFeedAndBreakdown() {
    const headers = { "MAL-Account-Holder-Id": activeHolderUid };
    void queryClient.invalidateQueries({
      queryKey: getApiV1AccountsByAccountUidTransactionsQueryKey({
        path: { accountUid },
        headers,
      }),
    });
    const spendingId = getApiV1AccountsByAccountUidSpendingQueryKey({
      path: { accountUid },
      query: { month: "" },
      headers,
    })[0]._id;
    void queryClient.invalidateQueries({
      predicate: (query) => {
        const key = query.queryKey[0] as
          | { _id?: string; path?: { accountUid?: string } }
          | undefined;
        return key?._id === spendingId && key?.path?.accountUid === accountUid;
      },
    });
  }

  const setMutation = useMutation({
    ...putApiV1AccountsByAccountUidTransactionsByEntryIdCategoryMutation(),
    onSuccess: () => {
      invalidateFeedAndBreakdown();
      toast.success("Category updated.");
    },
  });

  const clearMutation = useMutation({
    ...deleteApiV1AccountsByAccountUidTransactionsByEntryIdCategoryMutation(),
    onSuccess: () => {
      invalidateFeedAndBreakdown();
      toast.success("Reverted to automatic categorisation.");
    },
  });

  return {
    setCategory: (entryId, category) =>
      setMutation.mutate({
        path: { accountUid, entryId },
        body: { category },
        headers: { "MAL-Account-Holder-Id": activeHolderUid },
      }),
    clearCategory: (entryId) =>
      clearMutation.mutate({
        path: { accountUid, entryId },
        headers: { "MAL-Account-Holder-Id": activeHolderUid },
      }),
    isPending: setMutation.isPending || clearMutation.isPending,
  };
}
