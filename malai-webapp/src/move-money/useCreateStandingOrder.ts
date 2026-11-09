import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { toast } from "sonner";
import {
  getApiV1StandingOrdersQueryKey,
  postApiV1StandingOrdersMutation,
} from "@/api/generated/@tanstack/react-query.gen";
import type {
  StandingOrderCreateRequest,
  StandingOrderResponse,
} from "@/api/generated/types.gen";
import { useActiveAccountHolder } from "@/account-holder/useActiveAccountHolder";
import { mintIdempotencyKey } from "@/lib/mintIdempotencyKey";

export type UseCreateStandingOrderOptions = {
  onSuccess?: (order: StandingOrderResponse) => void;
  navigateOnSuccess?: boolean;
};

export type UseCreateStandingOrderResult = {
  createStandingOrder: (input: StandingOrderCreateRequest) => void;
  isPending: boolean;
};

export function useCreateStandingOrder(
  options: UseCreateStandingOrderOptions = {},
): UseCreateStandingOrderResult {
  const holder = useActiveAccountHolder();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const activeHolderUid =
    holder.status === "ready" ? holder.activeHolder.accountHolderUid : "";
  const navigateOnSuccess = options.navigateOnSuccess !== false;

  const mutation = useMutation({
    ...postApiV1StandingOrdersMutation(),
    onSuccess: (order) => {
      void queryClient.invalidateQueries({
        queryKey: getApiV1StandingOrdersQueryKey({
          headers: { "MAL-Account-Holder-Id": activeHolderUid },
        }),
      });
      toast.success("Standing order created.");
      options.onSuccess?.(order);
      if (navigateOnSuccess) navigate("/move/standing-orders");
    },
  });

  return {
    createStandingOrder: (input) =>
      mutation.mutate({
        body: input,
        headers: {
          "MAL-Account-Holder-Id": activeHolderUid,
          "Idempotency-Key": mintIdempotencyKey(),
        },
      }),
    isPending: mutation.isPending,
  };
}
