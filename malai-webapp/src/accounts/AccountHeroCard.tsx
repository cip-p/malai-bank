import { useState } from "react";
import type { AccountIdentifiersResponse } from "@/api/generated/types.gen";
import { CopyButton } from "@/components/CopyButton";
import { formatCurrency } from "@/lib/formatCurrency";
import { useFocusOnMount } from "@/lib/useFocusOnMount";
import { BalanceVisibilityToggle } from "./BalanceVisibilityToggle";
import { Money } from "./Money";
import { useAccount } from "./useAccount";
import { useBalance } from "./useBalance";
import { useBalanceVisibility } from "./useBalanceVisibility";
import { useIdentifiers } from "./useIdentifiers";

type Props = {
  accountUid: string;
};

export function AccountHeroCard({ accountUid }: Props) {
  const headingRef = useFocusOnMount<HTMLHeadingElement>();
  const { account } = useAccount(accountUid);
  const balanceQuery = useBalance(accountUid);
  const identifiersQuery = useIdentifiers(accountUid);
  const masterHidden = useBalanceVisibility((s) => s.hidden);
  const [localHidden, setLocalHidden] = useState<boolean>(masterHidden);

  if (!account) return null;

  return (
    <section className="mt-4 rounded-xl border border-border bg-card p-6 shadow-xs">
      <h1
        ref={headingRef}
        tabIndex={-1}
        data-testid="account-detail-name"
        className="text-2xl font-semibold tracking-tight break-words outline-none"
      >
        {account.name}
      </h1>
      <p className="mt-1 text-sm text-muted-foreground">
        {account.accountType} · {account.currency}
      </p>

      <div className="mt-6 flex flex-wrap items-center gap-2">
        {balanceQuery.isPending ? (
          <BalanceSkeleton />
        ) : balanceQuery.isError || !balanceQuery.balance ? (
          <span
            data-testid="hero-balance-error"
            className="text-3xl font-semibold text-muted-foreground sm:text-4xl"
          >
            —
          </span>
        ) : localHidden ? (
          <span
            data-testid="hero-balance"
            className="text-3xl font-semibold tracking-tight tabular-nums sm:text-4xl"
          >
            £·····
          </span>
        ) : (
          <span
            data-testid="hero-balance"
            aria-label={formatCurrency(
              balanceQuery.balance.availableBalanceMinorUnits,
              balanceQuery.balance.currency,
            )}
            className="text-3xl font-semibold tracking-tight tabular-nums sm:text-4xl"
          >
            <Money
              minorUnits={balanceQuery.balance.availableBalanceMinorUnits}
              currency={balanceQuery.balance.currency}
            />
          </span>
        )}
        <BalanceVisibilityToggle
          hidden={localHidden}
          onToggle={() => setLocalHidden((h) => !h)}
          labels={{ show: "Show balance", hide: "Hide balance" }}
          testId="hero-balance-visibility-toggle"
        />
      </div>

      {balanceQuery.balance && balanceQuery.balance.heldAmountMinorUnits > 0 ? (
        <p
          data-testid="hero-balance-held"
          className="mt-2 text-sm text-muted-foreground"
        >
          {localHidden ? (
            "£····· on hold"
          ) : (
            <>
              <Money
                minorUnits={balanceQuery.balance.heldAmountMinorUnits}
                currency={balanceQuery.balance.currency}
              />{" "}
              on hold
            </>
          )}
        </p>
      ) : null}

      <div className="mt-3">
        {identifiersQuery.isPending ? (
          <IdentifiersSkeleton />
        ) : identifiersQuery.isError || !identifiersQuery.identifiers ? (
          <span
            data-testid="hero-identifiers-error"
            className="text-sm text-muted-foreground"
          >
            —
          </span>
        ) : (
          <Identifiers identifiers={identifiersQuery.identifiers} />
        )}
      </div>
    </section>
  );
}

function BalanceSkeleton() {
  return (
    <div
      data-testid="hero-balance-skeleton"
      aria-busy="true"
      className="h-10 w-48 rounded-md bg-muted/40 motion-safe:animate-pulse"
    >
      <span className="sr-only">Loading balance</span>
    </div>
  );
}

function IdentifiersSkeleton() {
  return (
    <div
      data-testid="hero-identifiers-skeleton"
      aria-busy="true"
      className="h-4 w-56 rounded-md bg-muted/40 motion-safe:animate-pulse"
    >
      <span className="sr-only">Loading identifiers</span>
    </div>
  );
}

function Identifiers({
  identifiers,
}: {
  identifiers: AccountIdentifiersResponse;
}) {
  const primary = identifiers.accountIdentifiers[0];
  const sortCode = identifiers.bankIdentifier ?? primary?.bankIdentifier ?? "";
  const accountNumber =
    identifiers.accountIdentifier ?? primary?.accountIdentifier ?? "";

  return (
    <p className="flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-muted-foreground">
      <span className="inline-flex items-center gap-1.5">
        <span data-testid="hero-sort-code">{sortCode}</span>
        <CopyButton
          value={sortCode}
          label="Copy sort code"
          testId="copy-sort-code"
        />
      </span>
      <span className="inline-flex items-center gap-1.5">
        <span data-testid="hero-account-number">{accountNumber}</span>
        <CopyButton
          value={accountNumber}
          label="Copy account number"
          testId="copy-account-number"
        />
      </span>
    </p>
  );
}
