import { useQuery } from "@tanstack/react-query";
import { getApiV1UserOptions } from "@/api/generated/@tanstack/react-query.gen";
import type { UserResponse } from "@/api/generated/types.gen";
import { isHttpStatus } from "@/api/http";

export type AuthStatus = "unknown" | "authenticated" | "unauthenticated";

export type UseUserResult = {
  user: UserResponse | null;
  status: AuthStatus;
};

export function useUser(): UseUserResult {
  const query = useQuery(getApiV1UserOptions());

  if (query.isSuccess) {
    return { user: query.data, status: "authenticated" };
  }
  if (query.isError && isHttpStatus(query.error, 401)) {
    return { user: null, status: "unauthenticated" };
  }
  return { user: null, status: "unknown" };
}
