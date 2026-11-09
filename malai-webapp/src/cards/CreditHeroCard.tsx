import { useState } from "react";
import { Link } from "react-router-dom";
import { format, parseISO } from "date-fns";
import { CreditCard } from "lucide-react";
import { BalanceVisibilityToggle } from "@/accounts/BalanceVisibilityToggle";
import { Money } from "@/accounts/Money";
import { useAccount } from "@/accounts/useAccount";
import { useBalanceVisibility } from "@/accounts/useBalanceVisibility";
import { Button } from "@/components/ui/button";
import { useFocusOnMount } from "@/lib/useFocusOnMount";
import { formatCurrency } from "@/lib/formatCurrency";
import { useCards } from "./useCards";
import { useCreditSummary } from "./useCreditSummary";

const MASKED = "£·····";

type Props = {
  accountUid: string;
};

export function CreditHeroCard({ accountUid }: Props) {
  const headingRef = useFocusOnMount<HTMLHeadingElement>();
  const { account } = useAccount(accountUid);
  const { summary, isPending, isError } = useCreditSummary(accountUid);
  const { cards } = useCards();
  const masterHidden = useBalanceVisibility((s) => s.hidden);
  const [localHidden, setLocalHidden] = useState<boolean>(masterHidden);

  if (!account) return null;

  const card = cards?.find(
    (c) => c.accountId === accountUid && c.status !== "CANCELLED",
  );

  return (
    <section
      data-testid="credit-hero-card"
      className="mt-4 rounded-xl border border-border bg-card p-6 shadow-xs"
    >
      <h1
        ref={headingRef}
        tabIndex={-1}
        data-testid="account-detail-name"
        className="text-2xl font-semibold tracking-tight break-words outline-none"
      >
        {account.name}
      </h1>
      <p className="mt-1 text-sm text-muted-foreground">
        Credit card · {account.currency}
      </p>

      {isPending ? (
        <SummarySkeleton />
      ) : isError || !summary ? (
        <p
          data-testid="credit-summary-error"
          className="mt-6 text-4xl font-semibold text-muted-foreground"
        >
          —
        </p>
      ) : (
        <>
          <div className="mt-6 flex items-center gap-2">
            {localHidden ? (
              <span
                data-testid="credit-owed"
                className="text-3xl font-semibold tracking-tight tabular-nums sm:text-4xl"
              >
                {MASKED}
              </span>
            ) : (
              <span
                data-testid="credit-owed"
                aria-label={formatCurrency(
                  -summary.owedMinorUnits,
                  summary.currency,
                )}
                className="text-3xl font-semibold tracking-tight tabular-nums sm:text-4xl"
              >
                <Money
                  minorUnits={-summary.owedMinorUnits}
                  currency={summary.currency}
                />
              </span>
            )}
            <BalanceVisibilityToggle
              hidden={localHidden}
              onToggle={() => setLocalHidden((h) => !h)}
              labels={{ show: "Show balance", hide: "Hide balance" }}
              testId="credit-owed-visibility-toggle"
            />
          </div>

          <p className="mt-3 text-sm text-muted-foreground">
            <span data-testid="credit-available">
              Available credit{" "}
              <span className="font-medium text-foreground">
                {localHidden ? (
                  MASKED
                ) : (
                  <Money
                    minorUnits={summary.availableCreditMinorUnits}
                    currency={summary.currency}
                  />
                )}
              </span>
            </span>
            {" · "}
            <span data-testid="credit-limit">
              Limit{" "}
              <span className="font-medium text-foreground">
                <Money
                  minorUnits={summary.creditLimitMinorUnits}
                  currency={summary.currency}
                />
              </span>
            </span>
          </p>

          {summary.statementBalanceMinorUnits !== undefined &&
          summary.minimumPaymentMinorUnits !== undefined &&
          summary.dueDate ? (
            <dl
              data-testid="credit-statement-summary"
              className="mt-6 grid grid-cols-1 gap-3 border-t border-border pt-4 text-sm sm:grid-cols-3"
            >
              <div>
                <dt className="text-muted-foreground">Statement balance</dt>
                <dd
                  data-testid="credit-statement-balance"
                  className="mt-0.5 font-medium"
                >
                  <Money
                    minorUnits={summary.statementBalanceMinorUnits}
                    currency={summary.currency}
                  />
                </dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Minimum payment</dt>
                <dd
                  data-testid="credit-minimum-payment"
                  className="mt-0.5 font-medium"
                >
                  <Money
                    minorUnits={summary.minimumPaymentMinorUnits}
                    currency={summary.currency}
                  />
                </dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Payment due</dt>
                <dd
                  data-testid="credit-due-date"
                  className="mt-0.5 font-medium"
                >
                  {format(parseISO(summary.dueDate), "d MMMM")}
                </dd>
              </div>
            </dl>
          ) : null}
        </>
      )}

      <div className="mt-6 flex flex-wrap gap-2">
        <Button asChild data-testid="pay-card-cta">
          <Link to={`/accounts/${accountUid}/pay-card`}>Pay card</Link>
        </Button>
        {card ? (
          <Button asChild variant="outline" data-testid="view-card-cta">
            <Link to={`/cards/${card.cardId}`}>
              <CreditCard aria-hidden="true" />
              View card
            </Link>
          </Button>
        ) : null}
      </div>
    </section>
  );
}

function SummarySkeleton() {
  return (
    <div
      data-testid="credit-summary-skeleton"
      aria-busy="true"
      className="mt-6 space-y-2"
    >
      <span className="sr-only">Loading credit summary</span>
      <div className="h-10 w-48 rounded-md bg-muted/40 motion-safe:animate-pulse" />
      <div className="h-4 w-64 rounded-md bg-muted/40 motion-safe:animate-pulse" />
    </div>
  );
}
