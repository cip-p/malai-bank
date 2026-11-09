import { useQuery } from "@tanstack/react-query";
import { getApiV1AccountHolderAllOptions } from "@/api/generated/@tanstack/react-query.gen";
import type { AccountHolderSummaryResponse } from "@/api/generated/types.gen";
import { InvariantViolation } from "@/lib/invariant";
import { useActiveHolderOverrideStore } from "./useActiveHolderOverrideStore";

export const INDIVIDUAL_HOLDER_TYPE = "INDIVIDUAL";

export type UseActiveAccountHolderResult =
  | { status: "loading"; activeHolder: null; error: null }
  | { status: "error"; activeHolder: null; error: Error }
  | {
      status: "ready";
      activeHolder: AccountHolderSummaryResponse;
      error: null;
    };

export function useActiveAccountHolder(): UseActiveAccountHolderResult {
  const override = useActiveHolderOverrideStore((s) => s.override);
  const query = useQuery(getApiV1AccountHolderAllOptions());

  if (query.isPending) {
    return { status: "loading", activeHolder: null, error: null };
  }
  if (query.isError) {
    return { status: "error", activeHolder: null, error: query.error };
  }

  const holders = query.data?.accountHolders ?? [];

  if (override) {
    const overridden = holders.find((h) => h.accountHolderUid === override);
    if (!overridden) {
      throw new InvariantViolation(
        `Active-holder override ${override} not present in /api/v1/account-holder/all response.`,
      );
    }
    return { status: "ready", activeHolder: overridden, error: null };
  }

  const individual = holders.find(
    (h) => h.accountHolderType === INDIVIDUAL_HOLDER_TYPE,
  );
  if (!individual) {
    throw new InvariantViolation(
      "Signed-in user has no INDIVIDUAL AccountHolder. Per CONTEXT.md every signed-in User has exactly one Individual holder by Registration.",
    );
  }
  return { status: "ready", activeHolder: individual, error: null };
}
