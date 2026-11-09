import { useMutation } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { postApiV1AuthRegisterMutation } from "@/api/generated/@tanstack/react-query.gen";
import type { RegisterRequest } from "@/api/generated/types.gen";
import type { VerificationOrigin } from "./verificationOrigin";

export type UseRegisterResult = {
  register: (input: RegisterRequest) => void;
  isPending: boolean;
};

export function useRegister(): UseRegisterResult {
  const navigate = useNavigate();

  const mutation = useMutation({
    ...postApiV1AuthRegisterMutation(),
    onSuccess: (_data, variables) => {
      const request = variables.body as RegisterRequest;
      const origin: VerificationOrigin = { kind: "register", request };
      navigate("/verify", { state: { origin } });
    },
  });

  return {
    register: (input) => mutation.mutate({ body: input }),
    isPending: mutation.isPending,
  };
}
