import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { postApiV1SandboxCardPresentmentsMutation } from "@/api/generated/@tanstack/react-query.gen";
import { isHttpStatus } from "@/api/http";
import { invalidateAllAccountMoneyData } from "./invalidateAllAccountMoneyData";

export type UseCardPresentmentMutationResult = {
  presentNow: () => void;
  isPending: boolean;
};

const GENERIC_ERROR_MESSAGE = "Something went wrong. Please try again.";

export function useCardPresentmentMutation(): UseCardPresentmentMutationResult {
  const queryClient = useQueryClient();

  const mutation = useMutation({
    ...postApiV1SandboxCardPresentmentsMutation(),
    meta: { handledLocally: true },
    onSuccess: (response) => {
      invalidateAllAccountMoneyData(queryClient);
      toast.success(
        response.presented === 1
          ? "1 pending capture presented."
          : `${response.presented} pending captures presented.`,
      );
    },
    onError: (error) => {
      if (isHttpStatus(error, 401)) return;
      toast.error(GENERIC_ERROR_MESSAGE);
    },
  });

  return {
    presentNow: () => mutation.mutate({}),
    isPending: mutation.isPending,
  };
}
