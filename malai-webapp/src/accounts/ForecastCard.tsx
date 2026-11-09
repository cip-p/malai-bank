import { Link } from "react-router-dom";
import { format, parseISO } from "date-fns";
import { ChevronRight, CloudOff, TriangleAlert } from "lucide-react";
import { cn } from "@/lib/utils";
import { Money } from "./Money";
import { useForecast } from "./useForecast";

type Props = {
  accountUid: string;
};

export function ForecastCard({ accountUid }: Props) {
  const { forecast, isPending, isError } = useForecast(accountUid);

  if (isPending) {
    return <ForecastCardSkeleton />;
  }

  if (isError || !forecast) {
    return (
      <div
        data-testid="forecast-card-unavailable"
        className="mt-4 flex items-center gap-3 rounded-xl border border-border bg-card p-5 text-muted-foreground shadow-xs"
      >
        <CloudOff aria-hidden="true" className="h-5 w-5" />
        <div>
          <p className="text-sm font-medium text-foreground">
            Balance forecast
          </p>
          <p className="text-sm">Unavailable just now</p>
        </div>
      </div>
    );
  }

  const hasPayments =
    forecast.completed.length > 0 || forecast.expected.length > 0;
  const isWarning = forecast.forecastBalanceMinorUnits < 0;
  const windowEndLabel = format(parseISO(forecast.windowEnd), "d MMMM");

  return (
    <Link
      to={`/accounts/${accountUid}/forecast`}
      data-testid="forecast-card"
      className="mt-4 block rounded-xl border border-border bg-card p-5 shadow-xs outline-none motion-safe:transition-colors hover:border-ring/40 focus-visible:ring-2 focus-visible:ring-ring/50"
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-sm font-medium text-muted-foreground">
            Balance forecast
          </p>
          <p className="text-xs text-muted-foreground">for {windowEndLabel}</p>
        </div>
        <span className="inline-flex shrink-0 items-center gap-0.5 text-sm text-muted-foreground">
          View forecast
          <ChevronRight aria-hidden="true" className="h-4 w-4" />
        </span>
      </div>

      <div className="mt-3 flex items-center gap-2">
        {isWarning ? (
          <TriangleAlert
            data-testid="forecast-warning-icon"
            aria-hidden="true"
            className="h-6 w-6 text-destructive"
          />
        ) : null}
        <span
          data-testid="forecast-balance"
          className={cn(
            "text-3xl font-semibold tracking-tight tabular-nums",
            isWarning && "text-destructive",
          )}
        >
          <Money
            minorUnits={forecast.forecastBalanceMinorUnits}
            currency={forecast.currency}
          />
        </span>
      </div>

      {!hasPayments ? (
        <p
          data-testid="forecast-no-payments"
          className="mt-2 text-sm text-muted-foreground"
        >
          No payments due this month
        </p>
      ) : null}
    </Link>
  );
}

function ForecastCardSkeleton() {
  return (
    <div
      data-testid="forecast-card-skeleton"
      aria-busy="true"
      className="mt-4 space-y-3 rounded-xl border border-border bg-card p-5 shadow-xs"
    >
      <span className="sr-only">Loading balance forecast</span>
      <div className="h-4 w-32 rounded-md bg-muted/40 motion-safe:animate-pulse" />
      <div className="h-9 w-40 rounded-md bg-muted/40 motion-safe:animate-pulse" />
    </div>
  );
}
