import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { postApiV1AuthSignoutMutation } from "@/api/generated/@tanstack/react-query.gen";
import { useBalanceVisibility } from "@/accounts/useBalanceVisibility";

export type UseSignoutResult = {
  signout: () => void;
  isPending: boolean;
};

export function useSignout(): UseSignoutResult {
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  const mutation = useMutation({
    ...postApiV1AuthSignoutMutation(),
    onSuccess: () => {
      queryClient.clear();
      useBalanceVisibility.setState({ hidden: false });
      navigate("/login", { replace: true });
    },
  });

  return {
    signout: () => mutation.mutate({}),
    isPending: mutation.isPending,
  };
}
