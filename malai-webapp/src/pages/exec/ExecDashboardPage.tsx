import { useState } from "react";
import { RefreshCw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { formatCurrency } from "@/lib/formatCurrency";
import { useFocusOnMount } from "@/lib/useFocusOnMount";
import { usePageTitle } from "@/lib/usePageTitle";
import { formatAsOf, formatWindowStart } from "@/exec/formatAsOf";
import {
  useExecMetrics,
  type ExecMetricsView,
  type ExecWindow,
  type MetricTileState,
} from "@/exec/useExecMetrics";

const COUNT = new Intl.NumberFormat("en-GB");

type TileSpec = { label: string; metric: keyof MetricSlots };
type MetricSlots = Pick<
  ExecMetricsView,
  "newCustomers" | "deposits" | "cardsIssued" | "paymentsStopped"
>;

const TILES: readonly TileSpec[] = [
  { label: "New customers", metric: "newCustomers" },
  { label: "Deposits", metric: "deposits" },
  { label: "Cards issued", metric: "cardsIssued" },
  { label: "Payments stopped", metric: "paymentsStopped" },
];

const WINDOWS: readonly {
  window: ExecWindow;
  label: string;
  testId: string;
}[] = [
  { window: "TODAY", label: "Today", testId: "exec-window-today" },
  { window: "LAST_7_DAYS", label: "Last 7 days", testId: "exec-window-7d" },
  { window: "LAST_30_DAYS", label: "Last 30 days", testId: "exec-window-30d" },
  {
    window: "LAST_12_MONTHS",
    label: "Last 12 months",
    testId: "exec-window-12m",
  },
];

const WINDOW_COPY: Record<ExecWindow, string> = {
  TODAY: "The day’s headline numbers across the bank.",
  LAST_7_DAYS: "The last 7 days’ headline numbers across the bank.",
  LAST_30_DAYS: "The last 30 days’ headline numbers across the bank.",
  LAST_12_MONTHS: "The last 12 months’ headline numbers across the bank.",
};

export function ExecDashboardPage() {
  usePageTitle("Executive dashboard");
  const headingRef = useFocusOnMount<HTMLHeadingElement>();
  const [metricsWindow, setMetricsWindow] = useState<ExecWindow>("TODAY");
  const metrics = useExecMetrics(metricsWindow);

  return (
    <section className="mx-auto w-full max-w-6xl px-6 py-10">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1
            ref={headingRef}
            tabIndex={-1}
            className="text-3xl font-semibold tracking-tight outline-none"
          >
            Executive dashboard
          </h1>
          <p
            data-testid="exec-window-copy"
            className="mt-2 max-w-prose text-sm text-muted-foreground"
          >
            {WINDOW_COPY[metricsWindow]}
          </p>
        </div>
        <div className="flex flex-col items-end gap-2">
          <Button
            type="button"
            variant="outline"
            size="sm"
            data-testid="exec-refresh"
            onClick={metrics.refetch}
          >
            <RefreshCw aria-hidden="true" className="size-4" />
            Refresh
          </Button>
          {metrics.asOf && (
            <p
              data-testid="exec-as-of"
              className="text-xs text-muted-foreground"
            >
              {metricsWindow !== "TODAY" && metrics.windowStart
                ? `Since ${formatWindowStart(metrics.windowStart)} · `
                : ""}
              As of {formatAsOf(metrics.asOf)}
            </p>
          )}
        </div>
      </div>
      <div
        role="group"
        aria-label="Reporting window"
        data-testid="exec-window"
        className="mt-6 flex flex-wrap gap-2"
      >
        {WINDOWS.map((choice) => {
          const selected = choice.window === metricsWindow;
          return (
            <Button
              key={choice.window}
              type="button"
              size="sm"
              variant={selected ? "default" : "outline"}
              aria-pressed={selected}
              data-testid={choice.testId}
              onClick={() => setMetricsWindow(choice.window)}
            >
              {choice.label}
            </Button>
          );
        })}
      </div>
      <ul className="mt-6 grid list-none gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {TILES.map(({ label, metric }) => (
          <MetricTile key={label} label={label} state={metrics[metric]} />
        ))}
      </ul>
    </section>
  );
}

function MetricTile({
  label,
  state,
}: {
  label: string;
  state: MetricTileState;
}) {
  return (
    <li
      data-testid="metric-tile"
      className="min-w-0 rounded-2xl border border-border bg-card p-6 shadow-xs"
    >
      <h2 className="text-sm font-medium text-muted-foreground">{label}</h2>
      <p
        data-testid="metric-tile-value"
        aria-busy={state.kind === "loading" || undefined}
        className="mt-4 text-3xl font-semibold tracking-tight text-foreground sm:text-4xl"
      >
        <MetricValue state={state} />
      </p>
    </li>
  );
}

function MetricValue({ state }: { state: MetricTileState }) {
  switch (state.kind) {
    case "loading":
      return (
        <>
          <span
            aria-hidden="true"
            className="inline-block h-9 w-24 rounded-md bg-muted align-middle motion-safe:animate-pulse"
          />
          <span className="sr-only">Loading</span>
        </>
      );
    case "count":
      return <span className="tabular-nums">{COUNT.format(state.count)}</span>;
    case "money":
      return (
        <span className="tabular-nums">
          {formatCurrency(state.amountMinorUnits, state.currency)}
        </span>
      );
    case "unavailable":
      return (
        <>
          <span aria-hidden="true">&mdash;</span>
          <span className="sr-only">Unavailable</span>
        </>
      );
  }
}
