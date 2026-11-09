import { Link, useParams } from "react-router-dom";
import { format, parseISO } from "date-fns";
import { TriangleAlert } from "lucide-react";
import { StatusPanel } from "@/components/StatusPanel";
import type {
  BalanceForecastResponse,
  Currency,
  ForecastOccurrence,
} from "@/api/generated/types.gen";
import { Money } from "@/accounts/Money";
import { ForecastOccurrenceRow } from "@/accounts/ForecastOccurrenceRow";
import { useForecast } from "@/accounts/useForecast";
import { cn } from "@/lib/utils";
import { usePageTitle } from "@/lib/usePageTitle";
import { useFocusOnMount } from "@/lib/useFocusOnMount";

type Props = {
  now?: Date;
};

export function ForecastPage({ now: _now }: Props = {}) {
  const { accountUid = "" } = useParams<{ accountUid: string }>();
  usePageTitle("Balance forecast");
  const headingRef = useFocusOnMount<HTMLHeadingElement>();
  const { forecast, isPending, isError, isUnavailable } =
    useForecast(accountUid);

  return (
    <div className="mx-auto w-full max-w-3xl p-6">
      <Link
        to={`/accounts/${accountUid}`}
        className="text-sm text-muted-foreground hover:underline focus-visible:underline"
      >
        ← Account
      </Link>

      <section aria-labelledby="forecast-heading" className="mt-4">
        <h1
          id="forecast-heading"
          ref={headingRef}
          tabIndex={-1}
          className="text-2xl font-semibold tracking-tight outline-none"
        >
          Balance forecast
        </h1>

        {isPending ? (
          <ForecastSkeleton />
        ) : isUnavailable ? (
          <StatusPanel
            testId="forecast-unavailable"
            role="status"
            className="mt-6"
            title="Balance forecast is unavailable"
            body="We couldn't work out your forecast just now. Please try again shortly."
          />
        ) : isError || !forecast ? (
          <StatusPanel
            testId="forecast-error"
            role="alert"
            className="mt-6"
            title="Couldn't load your forecast"
            body="Something went wrong working out your balance forecast."
          />
        ) : (
          <ForecastView forecast={forecast} />
        )}
      </section>
    </div>
  );
}

function ForecastView({ forecast }: { forecast: BalanceForecastResponse }) {
  const isWarning = forecast.forecastBalanceMinorUnits < 0;
  const windowEndLabel = format(parseISO(forecast.windowEnd), "d MMMM");
  const rangeLabel = `${format(parseISO(forecast.windowStart), "d MMMM")} to ${format(
    parseISO(forecast.windowEnd),
    "d MMMM",
  )}`;

  return (
    <>
      <div
        data-testid="forecast-hero"
        className="mt-6 rounded-xl border border-border bg-card p-6 shadow-xs"
      >
        <p className="text-sm font-medium text-muted-foreground">
          for {windowEndLabel}
        </p>
        <div className="mt-2 flex items-center gap-2">
          {isWarning ? (
            <TriangleAlert
              data-testid="forecast-hero-warning-icon"
              aria-hidden="true"
              className="h-7 w-7 shrink-0 text-destructive"
            />
          ) : null}
          <span
            data-testid="forecast-hero-balance"
            className={cn(
              "text-4xl font-semibold tracking-tight tabular-nums",
              isWarning && "text-destructive",
            )}
          >
            <Money
              minorUnits={forecast.forecastBalanceMinorUnits}
              currency={forecast.currency}
            />
          </span>
        </div>
        <p className="mt-2 text-sm text-muted-foreground">{rangeLabel}</p>
      </div>

      <dl
        data-testid="forecast-breakdown"
        className="mt-4 rounded-xl border border-border bg-card p-6 shadow-xs"
      >
        <div className="flex items-baseline justify-between gap-4">
          <dt className="text-sm text-muted-foreground">Available balance</dt>
          <dd
            data-testid="forecast-available-balance"
            className="text-sm font-medium"
          >
            <Money
              minorUnits={forecast.availableBalanceMinorUnits}
              currency={forecast.currency}
            />
          </dd>
        </div>
        <div className="mt-2 flex items-baseline justify-between gap-4">
          <dt className="text-sm text-muted-foreground">Expected payments</dt>
          <dd
            data-testid="forecast-expected-total"
            className="text-sm font-medium"
          >
            <Money
              minorUnits={forecast.expectedTotalMinorUnits}
              currency={forecast.currency}
            />
          </dd>
        </div>
        <div className="mt-3 flex items-baseline justify-between gap-4 border-t border-border pt-3">
          <dt className="text-sm font-medium">Forecast balance</dt>
          <dd
            data-testid="forecast-breakdown-total"
            className={cn(
              "text-sm font-semibold tabular-nums",
              isWarning && "text-destructive",
            )}
          >
            <Money
              minorUnits={forecast.forecastBalanceMinorUnits}
              currency={forecast.currency}
            />
          </dd>
        </div>
      </dl>

      <div className="mt-4 space-y-3">
        <ForecastSection
          testId="forecast-expected"
          title="Expected"
          occurrences={forecast.expected}
          currency={forecast.currency}
          open
          emptyCopy="Nothing expected this month."
        />
        <ForecastSection
          testId="forecast-completed"
          title="Completed"
          occurrences={forecast.completed}
          currency={forecast.currency}
          emptyCopy="Nothing completed this month."
        />
      </div>
    </>
  );
}

function ForecastSection({
  testId,
  title,
  occurrences,
  currency,
  emptyCopy,
  open = false,
}: {
  testId: string;
  title: string;
  occurrences: ForecastOccurrence[];
  currency: Currency;
  emptyCopy: string;
  open?: boolean;
}) {
  return (
    <details
      data-testid={testId}
      open={open}
      className="rounded-xl border border-border bg-card px-6 shadow-xs [&[open]>summary]:border-b [&[open]>summary]:border-border"
    >
      <summary
        data-testid={`${testId}-summary`}
        className="-mx-6 flex cursor-pointer items-center gap-2 rounded-xl px-6 py-4 text-sm font-medium outline-none focus-visible:ring-2 focus-visible:ring-ring/50"
      >
        {title} ({occurrences.length})
      </summary>
      {occurrences.length > 0 ? (
        <ul className="divide-y divide-border py-1">
          {occurrences.map((occurrence, index) => (
            <ForecastOccurrenceRow
              key={`${occurrence.instrumentId}-${occurrence.date}-${index}`}
              occurrence={occurrence}
              currency={currency}
            />
          ))}
        </ul>
      ) : (
        <p
          data-testid={`${testId}-empty`}
          className="py-4 text-sm text-muted-foreground"
        >
          {emptyCopy}
        </p>
      )}
    </details>
  );
}

function ForecastSkeleton() {
  return (
    <div
      data-testid="forecast-skeleton"
      aria-busy="true"
      className="mt-6 space-y-4"
    >
      <span className="sr-only">Loading balance forecast</span>
      <div className="space-y-3 rounded-xl border border-border bg-card p-6 shadow-xs">
        <div className="h-4 w-24 rounded-md bg-muted/40 motion-safe:animate-pulse" />
        <div className="h-10 w-48 rounded-md bg-muted/40 motion-safe:animate-pulse" />
        <div className="h-4 w-36 rounded-md bg-muted/40 motion-safe:animate-pulse" />
      </div>
      <div className="space-y-3 rounded-xl border border-border bg-card p-6 shadow-xs">
        <div className="h-4 rounded-md bg-muted/40 motion-safe:animate-pulse" />
        <div className="h-4 rounded-md bg-muted/40 motion-safe:animate-pulse" />
      </div>
    </div>
  );
}
