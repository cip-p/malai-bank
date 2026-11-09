import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  deleteApiV1PayeesByPayeeIdMutation,
  getApiV1PayeesQueryKey,
} from "@/api/generated/@tanstack/react-query.gen";
import type { PayeesResponse } from "@/api/generated/types.gen";
import { HttpError, isHttpStatus } from "@/api/http";
import { useActiveAccountHolder } from "@/account-holder/useActiveAccountHolder";

export type UseDeletePayeeResult = {
  deletePayee: (payeeId: string) => void;
  isPending: boolean;
};

const GENERIC_ERROR_MESSAGE = "Something went wrong. Please try again.";
const PAYEE_HAS_ACTIVE_STANDING_ORDER_CODE = "6994";
const ACTIVE_STANDING_ORDER_MESSAGE =
  "Cancel the standing order that pays this payee before removing them.";

function isBlockedByActiveStandingOrder(error: unknown): boolean {
  if (!isHttpStatus(error, 409)) return false;
  const body = (error as HttpError).body as { message?: unknown } | undefined;
  return body?.message === PAYEE_HAS_ACTIVE_STANDING_ORDER_CODE;
}

type OptimisticContext = {
  previous: PayeesResponse | undefined;
  queryKey: ReturnType<typeof getApiV1PayeesQueryKey>;
};

export function useDeletePayee(): UseDeletePayeeResult {
  const holder = useActiveAccountHolder();
  const queryClient = useQueryClient();
  const activeHolderUid =
    holder.status === "ready" ? holder.activeHolder.accountHolderUid : "";

  const payeesKey = (): ReturnType<typeof getApiV1PayeesQueryKey> =>
    getApiV1PayeesQueryKey({
      headers: { "MAL-Account-Holder-Id": activeHolderUid },
    });

  const succeed = (): void => {
    void queryClient.invalidateQueries({ queryKey: payeesKey() });
    toast.success("Payee removed.");
  };

  const mutation = useMutation({
    ...deleteApiV1PayeesByPayeeIdMutation(),
    meta: { handledLocally: true },
    onMutate: (variables): OptimisticContext => {
      const payeeId = (variables as { path: { payeeId: string } }).path.payeeId;
      const queryKey = payeesKey();
      const previous = queryClient.getQueryData<PayeesResponse>(queryKey);
      if (previous) {
        queryClient.setQueryData<PayeesResponse>(queryKey, {
          ...previous,
          payees: previous.payees.filter((p) => p.payeeId !== payeeId),
        });
      }
      return { previous, queryKey };
    },
    onSuccess: () => {
      succeed();
    },
    onError: (error, _variables, context) => {
      if (isHttpStatus(error, 404)) {
        succeed();
        return;
      }
      if (isHttpStatus(error, 401)) return;
      const ctx = context as OptimisticContext | undefined;
      if (ctx?.previous) {
        queryClient.setQueryData<PayeesResponse>(ctx.queryKey, ctx.previous);
      }
      if (isBlockedByActiveStandingOrder(error)) {
        toast.error(ACTIVE_STANDING_ORDER_MESSAGE);
        return;
      }
      toast.error(GENERIC_ERROR_MESSAGE);
    },
  });

  return {
    deletePayee: (payeeId: string) =>
      mutation.mutate({
        path: { payeeId },
        headers: { "MAL-Account-Holder-Id": activeHolderUid },
      }),
    isPending: mutation.isPending,
  };
}
