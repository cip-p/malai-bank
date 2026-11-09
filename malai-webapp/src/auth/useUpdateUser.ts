import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  getApiV1UserQueryKey,
  putApiV1UserMutation,
} from "@/api/generated/@tanstack/react-query.gen";
import type { UpdateUserRequest } from "@/api/generated/types.gen";

export type UseUpdateUserResult = {
  updateUser: (input: UpdateUserRequest) => void;
  isPending: boolean;
};

export function useUpdateUser(): UseUpdateUserResult {
  const queryClient = useQueryClient();

  const mutation = useMutation({
    ...putApiV1UserMutation(),
    onSuccess: (data) => {
      queryClient.setQueryData(getApiV1UserQueryKey(), data);
      toast.success("Profile updated.");
    },
  });

  return {
    updateUser: (input) => mutation.mutate({ body: input }),
    isPending: mutation.isPending,
  };
}
