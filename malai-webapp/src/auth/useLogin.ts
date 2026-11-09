import { useMutation } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { postApiV1AuthLoginMutation } from "@/api/generated/@tanstack/react-query.gen";
import type { LoginRequest } from "@/api/generated/types.gen";
import type { VerificationOrigin } from "./verificationOrigin";

export type UseLoginResult = {
  login: (input: LoginRequest) => void;
  isPending: boolean;
};

export function useLogin(): UseLoginResult {
  const navigate = useNavigate();

  const mutation = useMutation({
    ...postApiV1AuthLoginMutation(),
    onSuccess: (_data, variables) => {
      const request = variables.body as LoginRequest;
      const origin: VerificationOrigin = { kind: "login", request };
      navigate("/verify", { state: { origin } });
    },
  });

  return {
    login: (input) => mutation.mutate({ body: input }),
    isPending: mutation.isPending,
  };
}
