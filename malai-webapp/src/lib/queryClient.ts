import { MutationCache, QueryCache, QueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  getApiV1UserQueryKey,
  getReadyQueryKey,
} from "@/api/generated/@tanstack/react-query.gen";
import { isHttpStatus } from "@/api/http";
import { isBackendUnreachable } from "@/availability/backendAvailability";

const GENERIC_ERROR_MESSAGE = "Something went wrong. Please try again.";
export const DEFAULT_STALE_TIME_MS = 30_000;

export type MutationMeta = {
  handledLocally?: boolean;
};

export function createQueryClient(): QueryClient {
  const userQueryKey = getApiV1UserQueryKey();
  const userQueryId = (userQueryKey[0] as { _id?: string })._id;
  const readyQueryKey = getReadyQueryKey();
  const readyQueryId = (readyQueryKey[0] as { _id?: string })._id;

  const client = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        refetchOnWindowFocus: false,
        staleTime: DEFAULT_STALE_TIME_MS,
      },
    },
    queryCache: new QueryCache({
      onError: (error, query) => {
        const failingId = (query.queryKey[0] as { _id?: string } | undefined)
          ?._id;

        if (isBackendUnreachable(error) && failingId !== readyQueryId) {
          void client.invalidateQueries({ queryKey: readyQueryKey });
          return;
        }

        if (!isHttpStatus(error, 401)) return;
        if (failingId === userQueryId) return;
        void client.invalidateQueries({ queryKey: userQueryKey });
      },
    }),
    mutationCache: new MutationCache({
      onError: (error, _variables, _context, mutation) => {
        const handledLocally =
          (mutation.options.meta as MutationMeta | undefined)
            ?.handledLocally === true;

        if (isBackendUnreachable(error)) {
          void client.invalidateQueries({ queryKey: readyQueryKey });
        }

        if (isHttpStatus(error, 401)) {
          const userQueryStatus = client
            .getQueryCache()
            .find({ queryKey: userQueryKey })?.state.status;
          if (userQueryStatus === "success") {
            void client.invalidateQueries({ queryKey: userQueryKey });
            return;
          }
        }

        if (handledLocally) return;
        toast.error(GENERIC_ERROR_MESSAGE);
      },
    }),
  });

  return client;
}
