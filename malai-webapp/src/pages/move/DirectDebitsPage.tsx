import { useState } from "react";
import { CalendarClock } from "lucide-react";
import type {
  AccountResponse,
  DirectDebitResponse,
} from "@/api/generated/types.gen";
import { useAccounts } from "@/accounts/useAccounts";
import { useDirectDebits } from "@/move-money/useDirectDebits";
import { useCancelDirectDebit } from "@/move-money/useCancelDirectDebit";
import { DirectDebitRow } from "@/move-money/DirectDebitRow";
import { CancelDirectDebitDialog } from "@/move-money/CancelDirectDebitDialog";
import { sortByNextDate } from "@/move-money/sortByNextDate";
import { StatusPanel } from "@/components/StatusPanel";
import { useFocusOnMount } from "@/lib/useFocusOnMount";
import { usePageTitle } from "@/lib/usePageTitle";

type Props = { now?: Date };

const EMPTY_HEADLINE = "No active direct debits";
const EMPTY_BODY =
  "A direct debit is a recurring pull of money initiated by the company being paid — set up via the company, not the bank.";

export function DirectDebitsPage({ now }: Props = {}) {
  usePageTitle("Direct Debits");
  const headingRef = useFocusOnMount<HTMLHeadingElement>();
  const { directDebits, isPending, isError } = useDirectDebits();
  const { accounts } = useAccounts();
  const { cancelDirectDebit } = useCancelDirectDebit();
  const [pendingCancel, setPendingCancel] =
    useState<DirectDebitResponse | null>(null);
  const effectiveNow = now ?? new Date();

  return (
    <section>
      <h1
        ref={headingRef}
        tabIndex={-1}
        className="text-2xl font-semibold tracking-tight outline-none"
      >
        Direct Debits
      </h1>
      <div className="mt-6">
        {renderBody(
          directDebits,
          isPending,
          isError,
          accounts,
          effectiveNow,
          setPendingCancel,
        )}
      </div>
      {pendingCancel ? (
        <CancelDirectDebitDialog
          open
          directDebit={pendingCancel}
          onCancel={() => setPendingCancel(null)}
          onConfirm={() => {
            cancelDirectDebit(pendingCancel.directDebitId);
            setPendingCancel(null);
          }}
        />
      ) : null}
    </section>
  );
}

function renderBody(
  directDebits: DirectDebitResponse[] | null,
  isPending: boolean,
  isError: boolean,
  accounts: AccountResponse[] | null,
  now: Date,
  onCancel: (dd: DirectDebitResponse) => void,
) {
  if (isPending) return <DirectDebitsSkeleton />;
  if (isError) {
    return (
      <StatusPanel
        testId="dd-error"
        role="alert"
        title="Couldn’t load direct debits"
        body="Please try again in a moment."
      />
    );
  }
  const items = directDebits ?? [];
  if (items.length === 0) {
    return (
      <div
        data-testid="dd-empty"
        className="flex flex-col items-center gap-4 rounded-2xl border border-dashed border-border px-6 py-12 text-center"
      >
        <CalendarClock
          aria-hidden="true"
          className="h-10 w-10 text-muted-foreground"
        />
        <div className="space-y-1.5">
          <p className="text-base font-medium">{EMPTY_HEADLINE}</p>
          <p className="max-w-md text-sm text-muted-foreground">{EMPTY_BODY}</p>
        </div>
      </div>
    );
  }

  const accountsList = accounts ?? [];
  const showChip = accountsList.length >= 2;
  const nameByUid = new Map(accountsList.map((a) => [a.accountUid, a.name]));

  const sorted = sortByNextDate(
    items.map((dd) => ({
      ...dd,
      nextDate:
        dd.status === "CANCELLED" ? null : (dd.nextCollectionAt ?? null),
      name: dd.creditorName,
    })),
  );

  return (
    <ul data-testid="dd-list" className="divide-y divide-border">
      {sorted.map((dd) => (
        <DirectDebitRow
          key={dd.directDebitId}
          dd={dd}
          now={now}
          accountLabel={
            showChip ? (nameByUid.get(dd.accountId) ?? undefined) : undefined
          }
          onCancel={onCancel}
        />
      ))}
    </ul>
  );
}

function DirectDebitsSkeleton() {
  return (
    <div data-testid="dd-skeleton" aria-busy="true" className="space-y-3">
      <span className="sr-only">Loading direct debits</span>
      <div className="h-12 rounded-md bg-muted/40 motion-safe:animate-pulse" />
      <div className="h-12 rounded-md bg-muted/40 motion-safe:animate-pulse" />
      <div className="h-12 rounded-md bg-muted/40 motion-safe:animate-pulse" />
    </div>
  );
}
