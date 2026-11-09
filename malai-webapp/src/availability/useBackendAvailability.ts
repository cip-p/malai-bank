import { useQuery } from "@tanstack/react-query";
import { backendReadyQueryOptions } from "./backendAvailability";

export type BackendAvailability = "checking" | "reachable" | "unreachable";

export type UseBackendAvailabilityResult = {
  status: BackendAvailability;
  rechecking: boolean;
  recheck: () => void;
};

export function useBackendAvailability(): UseBackendAvailabilityResult {
  const query = useQuery(backendReadyQueryOptions());

  const status: BackendAvailability = query.isError
    ? "unreachable"
    : query.isSuccess
      ? "reachable"
      : "checking";

  return {
    status,
    rechecking: query.isFetching,
    recheck: () => {
      void query.refetch();
    },
  };
}
