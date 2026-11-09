import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { getApiV1AdminMdrDecisionsByDecisionIdActionableOutputOptions } from "@/api/generated/@tanstack/react-query.gen";
import type { MdrActionableOutputResponse } from "@/api/generated/types.gen";
import { isHttpStatus } from "@/api/http";

export type UseMdrActionableOutputResult = {
  isIdle: boolean;
  isAsking: boolean;
  inForce: MdrActionableOutputResponse | null;
  isNoRuling: boolean;
  isError: boolean;
  ask: () => void;
};

export function useMdrActionableOutput(
  decisionId: string,
): UseMdrActionableOutputResult {
  const [asked, setAsked] = useState(false);
  const query = useQuery({
    ...getApiV1AdminMdrDecisionsByDecisionIdActionableOutputOptions({
      path: { decisionId },
    }),
    enabled: asked,
  });

  const ask = () => {
    if (asked) void query.refetch();
    else setAsked(true);
  };

  const asking = asked && query.isFetching;
  const answered = asked && !asking;
  const refused = answered && query.isError && isHttpStatus(query.error, 409);

  return {
    isIdle: !asked,
    isAsking: asking,
    inForce: answered && !query.isError ? (query.data ?? null) : null,
    isNoRuling: refused,
    isError: answered && query.isError && !refused,
    ask,
  };
}
