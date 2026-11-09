import { useQuery } from "@tanstack/react-query";
import { getApiV1AdminMdrDecisionsByDecisionIdOptions } from "@/api/generated/@tanstack/react-query.gen";
import type { MdrDecisionThreadResponse } from "@/api/generated/types.gen";
import { isHttpStatus } from "@/api/http";

export type UseMdrDecisionThreadResult = {
  thread: MdrDecisionThreadResponse | null;
  isPending: boolean;
  isNotFound: boolean;
  isError: boolean;
  error: unknown;
  refetch: () => void;
};

export function useMdrDecisionThread(
  decisionId: string,
): UseMdrDecisionThreadResult {
  const query = useQuery(
    getApiV1AdminMdrDecisionsByDecisionIdOptions({ path: { decisionId } }),
  );
  const refetch = () => {
    void query.refetch();
  };

  if (query.isError) {
    const notFound = isHttpStatus(query.error, 404);
    return {
      thread: null,
      isPending: false,
      isNotFound: notFound,
      isError: !notFound,
      error: notFound ? null : query.error,
      refetch,
    };
  }

  return {
    thread: query.data ?? null,
    isPending: query.isPending,
    isNotFound: false,
    isError: false,
    error: null,
    refetch,
  };
}
