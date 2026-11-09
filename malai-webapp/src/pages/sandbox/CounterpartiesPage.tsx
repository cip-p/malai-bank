import { Link } from "react-router-dom";
import type { CounterpartyResponse } from "@/api/generated/types.gen";
import { Button } from "@/components/ui/button";
import {
  dataTableBodyClassName,
  dataTableCellClassName,
  dataTableClassName,
  dataTableHeadClassName,
  dataTableRowClassName,
} from "@/components/dataTable";
import { ScrollRegion } from "@/components/ScrollRegion";
import { StatusPanel } from "@/components/StatusPanel";
import { useFocusOnMount } from "@/lib/useFocusOnMount";
import { cn } from "@/lib/utils";
import { usePageTitle } from "@/lib/usePageTitle";
import { useCounterparties } from "@/sandbox/useCounterparties";
import { counterpartyDeepLink } from "@/sandbox/counterpartyDeepLink";
import { formatOutcome } from "@/sandbox/formatOutcome";

export function CounterpartiesPage() {
  usePageTitle("Counterparties");
  const headingRef = useFocusOnMount<HTMLHeadingElement>();
  const { counterparties, isPending, isError, error } = useCounterparties();

  return (
    <section>
      <h1
        ref={headingRef}
        tabIndex={-1}
        className="text-2xl font-semibold tracking-tight outline-none"
      >
        Counterparties
      </h1>
      <p
        data-testid="counterparties-header-sentence"
        className="mt-3 text-sm text-muted-foreground"
      >
        Type the name shown to get <strong>MATCH</strong>. Type a near-miss to
        get <strong>CLOSE_MATCH</strong>. Type anything else to get{" "}
        <strong>NO_MATCH</strong>.
      </p>
      <div className="mt-6">
        {renderBody(counterparties, isPending, isError, error)}
      </div>
    </section>
  );
}

function renderBody(
  counterparties: CounterpartyResponse[] | null,
  isPending: boolean,
  isError: boolean,
  error: unknown,
) {
  if (isPending) return <CounterpartiesSkeleton />;
  if (isError) {
    return <CounterpartiesError error={error} />;
  }
  const rows = counterparties ?? [];
  return <CounterpartiesTable rows={rows} />;
}

function CounterpartiesTable({ rows }: { rows: CounterpartyResponse[] }) {
  return (
    <ScrollRegion label="Counterparty directory">
      <table data-testid="counterparties-table" className={dataTableClassName}>
        <thead className={dataTableHeadClassName}>
          <tr className="border-b border-border text-left text-muted-foreground">
            <th scope="col" className="py-2 pr-4 font-medium">
              Bank
            </th>
            <th scope="col" className="py-2 pr-4 font-medium">
              Name on record
            </th>
            <th scope="col" className="py-2 pr-4 font-medium">
              Sort code
            </th>
            <th scope="col" className="py-2 pr-4 font-medium">
              Account number
            </th>
            <th scope="col" className="py-2 pr-4 font-medium">
              Outcome
            </th>
            <th scope="col" className="py-2 pr-4 font-medium">
              Actions
            </th>
          </tr>
        </thead>
        <tbody className={dataTableBodyClassName}>
          {rows.map((row) => (
            <tr
              key={`${row.sortCode}-${row.accountNumber}`}
              data-testid="counterparty-row"
              className={dataTableRowClassName}
            >
              <td
                data-testid="counterparty-bank"
                data-label="Bank"
                className={cn(dataTableCellClassName, "py-3 pr-4")}
              >
                {row.bank}
              </td>
              <td
                data-testid="counterparty-name-on-record"
                data-label="Name on record"
                className={cn(dataTableCellClassName, "py-3 pr-4")}
              >
                {row.nameOnRecord}
              </td>
              <td
                data-testid="counterparty-sort-code"
                data-label="Sort code"
                className={cn(dataTableCellClassName, "py-3 pr-4 tabular-nums")}
              >
                {row.sortCode}
              </td>
              <td
                data-testid="counterparty-account-number"
                data-label="Account number"
                className={cn(dataTableCellClassName, "py-3 pr-4 tabular-nums")}
              >
                {row.accountNumber}
              </td>
              <td
                data-testid="counterparty-outcome"
                data-label="Outcome"
                className={cn(dataTableCellClassName, "py-3 pr-4")}
              >
                {formatOutcome(row)}
              </td>
              <td
                data-label="Actions"
                className={cn(dataTableCellClassName, "py-3 pr-4")}
              >
                <div className="flex flex-wrap gap-2">
                  <Button
                    asChild
                    size="sm"
                    variant="outline"
                    data-testid="counterparty-save-as-payee"
                  >
                    <Link to={counterpartyDeepLink(row, "save-as-payee")}>
                      Save as payee
                    </Link>
                  </Button>
                  <Button
                    asChild
                    size="sm"
                    variant="outline"
                    data-testid="counterparty-send-a-payment"
                  >
                    <Link to={counterpartyDeepLink(row, "send-a-payment")}>
                      Send a payment
                    </Link>
                  </Button>
                  <Button
                    asChild
                    size="sm"
                    variant="outline"
                    data-testid="counterparty-receive-a-payment"
                  >
                    <Link to={counterpartyDeepLink(row, "receive-a-payment")}>
                      Receive a payment
                    </Link>
                  </Button>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </ScrollRegion>
  );
}

function CounterpartiesSkeleton() {
  return (
    <div
      data-testid="counterparties-skeleton"
      aria-busy="true"
      className="space-y-2"
    >
      <span className="sr-only">Loading counterparties</span>
      <div className="h-10 rounded-md bg-muted/40 motion-safe:animate-pulse" />
      <div className="h-10 rounded-md bg-muted/40 motion-safe:animate-pulse" />
      <div className="h-10 rounded-md bg-muted/40 motion-safe:animate-pulse" />
    </div>
  );
}

function CounterpartiesError({ error: _error }: { error: unknown }) {
  const reload = () => window.location.reload();
  return (
    <StatusPanel
      testId="counterparties-error"
      role="alert"
      title="Couldn’t load counterparties."
      action={
        <Button
          type="button"
          data-testid="counterparties-retry"
          onClick={reload}
        >
          Try again
        </Button>
      }
    />
  );
}
