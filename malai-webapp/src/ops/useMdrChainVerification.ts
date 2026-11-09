import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { getApiV1AdminMdrChainVerificationOptions } from "@/api/generated/@tanstack/react-query.gen";
import type { MdrChainVerificationResponse } from "@/api/generated/types.gen";

export type UseMdrChainVerificationResult = {
  isIdle: boolean;
  isVerifying: boolean;
  verdict: MdrChainVerificationResponse | null;
  isError: boolean;
  verify: () => void;
};

export function useMdrChainVerification(): UseMdrChainVerificationResult {
  const [asked, setAsked] = useState(false);
  const query = useQuery({
    ...getApiV1AdminMdrChainVerificationOptions(),
    enabled: asked,
  });

  const verify = () => {
    if (asked) void query.refetch();
    else setAsked(true);
  };

  const verifying = asked && query.isFetching;
  const answered = asked && !verifying;

  return {
    isIdle: !asked,
    isVerifying: verifying,
    verdict: answered && !query.isError ? (query.data ?? null) : null,
    isError: answered && query.isError,
    verify,
  };
}
