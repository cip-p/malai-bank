import { useMutation, useQueryClient } from "@tanstack/react-query";
import {
  getApiV1UserQueryKey,
  postApiV1AuthVerifyCodeMutation,
} from "@/api/generated/@tanstack/react-query.gen";
import type { VerifyCodeRequest } from "@/api/generated/types.gen";

export type UseVerifyCodeResult = {
  verify: (input: VerifyCodeRequest) => void;
  isPending: boolean;
  isError: boolean;
  reset: () => void;
};

export function useVerifyCode(): UseVerifyCodeResult {
  const queryClient = useQueryClient();
  const userQueryKey = getApiV1UserQueryKey();

  const mutation = useMutation({
    ...postApiV1AuthVerifyCodeMutation(),
    meta: { handledLocally: true },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: userQueryKey });
    },
  });

  return {
    verify: (input) => mutation.mutate({ body: input }),
    isPending: mutation.isPending,
    isError: mutation.isError,
    reset: mutation.reset,
  };
}
