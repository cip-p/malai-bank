import { queryOptions } from "@tanstack/react-query";
import { getReadyQueryKey } from "@/api/generated/@tanstack/react-query.gen";
import { getReady } from "@/api/generated/sdk.gen";
import { HttpError, NetworkError } from "@/api/http";

export const PROBE_TIMEOUT_MS = 6_000;
export const PROBE_RETRY_DELAY_MS = 400;
export const REACHABLE_PROBE_INTERVAL_MS = 60_000;
export const UNREACHABLE_PROBE_INTERVAL_MS = 10_000;

const UNREACHABLE_STATUSES = new Set([502, 503, 504]);

export function isBackendUnreachable(error: unknown): boolean {
  if (error instanceof NetworkError) return true;
  return error instanceof HttpError && UNREACHABLE_STATUSES.has(error.status);
}

export async function probeBackend(signal?: AbortSignal): Promise<true> {
  const controller = new AbortController();
  const abort = () => controller.abort();
  const timer = setTimeout(abort, PROBE_TIMEOUT_MS);
  signal?.addEventListener("abort", abort);

  try {
    await getReady({
      cache: "no-store",
      signal: controller.signal,
      throwOnError: true,
    });
    return true;
  } finally {
    clearTimeout(timer);
    signal?.removeEventListener("abort", abort);
  }
}

export function backendReadyQueryOptions() {
  return queryOptions({
    queryKey: getReadyQueryKey(),
    queryFn: ({ signal }) => probeBackend(signal),
    retry: 1,
    retryDelay: PROBE_RETRY_DELAY_MS,
    staleTime: 0,
    gcTime: Infinity,
    refetchOnWindowFocus: true,
    refetchInterval: (query) =>
      query.state.status === "error"
        ? UNREACHABLE_PROBE_INTERVAL_MS
        : REACHABLE_PROBE_INTERVAL_MS,
  });
}
