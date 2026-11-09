import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { ChevronLeft, ChevronRight, PiggyBank } from "lucide-react";
import { StatusPanel } from "@/components/StatusPanel";
import { SpendingBars } from "@/accounts/SpendingBars";
import { useSpendingBreakdown } from "@/accounts/useSpendingBreakdown";
import { Button } from "@/components/ui/button";
import {
  currentMonthParam,
  formatMonthLabel,
  isFutureMonth,
  shiftMonthParam,
} from "@/lib/monthParam";
import { usePageTitle } from "@/lib/usePageTitle";
import { useFocusOnMount } from "@/lib/useFocusOnMount";

type Props = {
  now?: Date;
};

export function SpendingPage({ now }: Props = {}) {
  const { accountUid = "" } = useParams<{ accountUid: string }>();
  const [month, setMonth] = useState(() => currentMonthParam(now));
  usePageTitle("Spending");
  const headingRef = useFocusOnMount<HTMLHeadingElement>();
  const { breakdown, isPending, isError, isUnavailable } = useSpendingBreakdown(
    accountUid,
    month,
  );

  return (
    <div className="mx-auto w-full max-w-3xl p-6">
      <Link
        to={`/accounts/${accountUid}`}
        className="text-sm text-muted-foreground hover:underline focus-visible:underline"
      >
        ← Account
      </Link>

      <section aria-labelledby="spending-heading" className="mt-4">
        <h1
          id="spending-heading"
          ref={headingRef}
          tabIndex={-1}
          className="text-2xl font-semibold tracking-tight outline-none"
        >
          Spending
        </h1>

        <div
          data-testid="spending-month-nav"
          className="mt-4 flex items-center justify-between gap-2 rounded-lg border border-border bg-card p-1.5"
        >
          <Button
            variant="ghost"
            size="icon"
            data-testid="spending-prev-month"
            aria-label="Previous month"
            onClick={() => setMonth((current) => shiftMonthParam(current, -1))}
          >
            <ChevronLeft aria-hidden="true" />
          </Button>
          <span
            data-testid="spending-month-label"
            className="text-sm font-medium"
          >
            {formatMonthLabel(month)}
          </span>
          <Button
            variant="ghost"
            size="icon"
            data-testid="spending-next-month"
            aria-label="Next month"
            disabled={isFutureMonth(month, now)}
            onClick={() => setMonth((current) => shiftMonthParam(current, 1))}
          >
            <ChevronRight aria-hidden="true" />
          </Button>
        </div>

        <div className="mt-6 rounded-xl border border-border bg-card p-6 shadow-xs">
          {isPending ? (
            <BreakdownSkeleton />
          ) : isUnavailable ? (
            <StatusPanel
              testId="spending-unavailable"
              role="status"
              title="Spending insights are unavailable"
              body="We couldn't load the spending breakdown just now. Please try again shortly."
            />
          ) : isError ? (
            <StatusPanel
              testId="spending-error"
              role="alert"
              title="Couldn't load spending"
              body="Something went wrong loading this month's spending."
            />
          ) : breakdown && breakdown.categories.length > 0 ? (
            <SpendingBars
              categories={breakdown.categories}
              totalMinorUnits={breakdown.totalMinorUnits}
              currency={breakdown.currency}
            />
          ) : (
            <StatusMessage
              testId="spending-empty"
              Icon={PiggyBank}
              title="No spending this month"
              body={`Nothing was spent from this account in ${formatMonthLabel(month)}.`}
            />
          )}
        </div>
      </section>
    </div>
  );
}

function StatusMessage({
  testId,
  Icon,
  title,
  body,
}: {
  testId: string;
  Icon: typeof PiggyBank;
  title: string;
  body: string;
}) {
  return (
    <div
      data-testid={testId}
      className="flex flex-col items-center px-6 py-12 text-center"
    >
      <span className="flex h-12 w-12 items-center justify-center rounded-full bg-muted text-muted-foreground">
        <Icon aria-hidden="true" className="h-6 w-6" />
      </span>
      <p className="mt-4 text-base font-medium">{title}</p>
      <p className="mt-1 text-sm text-muted-foreground">{body}</p>
    </div>
  );
}

function BreakdownSkeleton() {
  return (
    <div data-testid="spending-skeleton" aria-busy="true" className="space-y-5">
      <span className="sr-only">Loading spending</span>
      <div className="h-7 w-32 rounded-md bg-muted/40 motion-safe:animate-pulse" />
      <div className="h-14 rounded-md bg-muted/40 motion-safe:animate-pulse" />
      <div className="h-14 rounded-md bg-muted/40 motion-safe:animate-pulse" />
      <div className="h-14 rounded-md bg-muted/40 motion-safe:animate-pulse" />
    </div>
  );
}
