import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  deleteApiV1StandingOrdersByStandingOrderIdMutation,
  getApiV1StandingOrdersQueryKey,
} from "@/api/generated/@tanstack/react-query.gen";
import type { StandingOrdersResponse } from "@/api/generated/types.gen";
import { isHttpStatus } from "@/api/http";
import { useActiveAccountHolder } from "@/account-holder/useActiveAccountHolder";

export type UseCancelStandingOrderResult = {
  cancelStandingOrder: (standingOrderId: string) => void;
  isPending: boolean;
};

const GENERIC_ERROR_MESSAGE = "Something went wrong. Please try again.";

type OptimisticContext = {
  previous: StandingOrdersResponse | undefined;
  queryKey: ReturnType<typeof getApiV1StandingOrdersQueryKey>;
};

export function useCancelStandingOrder(): UseCancelStandingOrderResult {
  const holder = useActiveAccountHolder();
  const queryClient = useQueryClient();
  const activeHolderUid =
    holder.status === "ready" ? holder.activeHolder.accountHolderUid : "";

  const standingOrdersKey = (): ReturnType<
    typeof getApiV1StandingOrdersQueryKey
  > =>
    getApiV1StandingOrdersQueryKey({
      headers: { "MAL-Account-Holder-Id": activeHolderUid },
    });

  const succeed = (): void => {
    void queryClient.invalidateQueries({ queryKey: standingOrdersKey() });
    toast.success("Standing order cancelled.");
  };

  const mutation = useMutation({
    ...deleteApiV1StandingOrdersByStandingOrderIdMutation(),
    meta: { handledLocally: true },
    onMutate: (variables): OptimisticContext => {
      const standingOrderId = (
        variables as { path: { standingOrderId: string } }
      ).path.standingOrderId;
      const queryKey = standingOrdersKey();
      const previous =
        queryClient.getQueryData<StandingOrdersResponse>(queryKey);
      if (previous) {
        queryClient.setQueryData<StandingOrdersResponse>(queryKey, {
          ...previous,
          standingOrders: previous.standingOrders.map((so) =>
            so.standingOrderId === standingOrderId
              ? { ...so, status: "CANCELLED" }
              : so,
          ),
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
        queryClient.setQueryData<StandingOrdersResponse>(
          ctx.queryKey,
          ctx.previous,
        );
      }
      toast.error(GENERIC_ERROR_MESSAGE);
    },
  });

  return {
    cancelStandingOrder: (standingOrderId: string) =>
      mutation.mutate({
        path: { standingOrderId },
        headers: { "MAL-Account-Holder-Id": activeHolderUid },
      }),
    isPending: mutation.isPending,
  };
}
