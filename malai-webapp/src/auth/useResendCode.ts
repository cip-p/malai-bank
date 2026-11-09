import { useMutation } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  postApiV1AuthLoginMutation,
  postApiV1AuthRegisterMutation,
} from "@/api/generated/@tanstack/react-query.gen";
import type { VerificationOrigin } from "./verificationOrigin";

export const CODE_RESENT_MESSAGE = "We've sent a new confirmation code.";

export type UseResendCodeResult = {
  resend: () => void;
  isPending: boolean;
};

export function useResendCode(origin: VerificationOrigin): UseResendCodeResult {
  const registerMutation = useMutation({
    ...postApiV1AuthRegisterMutation(),
    onSuccess: () => toast.success(CODE_RESENT_MESSAGE),
  });
  const loginMutation = useMutation({
    ...postApiV1AuthLoginMutation(),
    onSuccess: () => toast.success(CODE_RESENT_MESSAGE),
  });

  const resend = () => {
    if (origin.kind === "register") {
      registerMutation.mutate({ body: origin.request });
    } else {
      loginMutation.mutate({ body: origin.request });
    }
  };

  return {
    resend,
    isPending: registerMutation.isPending || loginMutation.isPending,
  };
}
