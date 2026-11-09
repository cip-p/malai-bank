import { useState } from "react";
import { Receipt } from "lucide-react";
import type { TransactionItemResponse } from "@/api/generated/types.gen";
import { StatusPanel } from "@/components/StatusPanel";
import { groupTransactionsByDate } from "@/lib/groupTransactionsByDate";
import { TransactionRow } from "./TransactionRow";
import { TransactionDetailSheet } from "./TransactionDetailSheet";
import { useTransactions } from "./useTransactions";

type Props = {
  accountUid: string;
  now?: Date;
};

type OpenSheetState = {
  tx: TransactionItemResponse;
  trigger: HTMLElement | null;
};

export function TransactionsList({ accountUid, now }: Props) {
  const { transactions, isPending, isError } = useTransactions(accountUid);
  const [sheet, setSheet] = useState<OpenSheetState | null>(null);
  const [open, setOpen] = useState<boolean>(false);

  function handleOpen(tx: TransactionItemResponse) {
    const trigger = (document.activeElement as HTMLElement | null) ?? null;
    setSheet({ tx, trigger });
    setOpen(true);
  }

  function handleOpenChange(next: boolean) {
    setOpen(next);
    if (!next && sheet?.trigger) {
      const trigger = sheet.trigger;
      requestAnimationFrame(() => trigger.focus());
    }
  }

  return (
    <section
      aria-labelledby="transactions-heading"
      className="mt-6 rounded-xl border border-border bg-card p-6 shadow-xs"
    >
      <h2 id="transactions-heading" className="text-lg font-semibold">
        Transactions
      </h2>
      <div className="mt-4">
        {renderBody(transactions, isPending, isError, now, handleOpen)}
      </div>
      {sheet ? (
        <TransactionDetailSheet
          transaction={sheet.tx}
          accountUid={accountUid}
          open={open}
          onOpenChange={handleOpenChange}
        />
      ) : null}
    </section>
  );
}

function renderBody(
  transactions: ReturnType<typeof useTransactions>["transactions"],
  isPending: boolean,
  isError: boolean,
  now: Date | undefined,
  onOpen: (tx: TransactionItemResponse) => void,
) {
  if (isPending) return <TransactionsSkeleton />;
  if (isError) {
    return (
      <StatusPanel
        testId="transactions-error"
        role="alert"
        title="Couldn’t load transactions"
        body="Please try again in a moment."
      />
    );
  }
  if (!transactions || transactions.length === 0) {
    return <EmptyState />;
  }

  const groups = groupTransactionsByDate(transactions, now ?? new Date());
  const showHundredNote = transactions.length === 100;

  return (
    <div>
      <ul className="space-y-1">
        {groups.map((group) => (
          <li key={group.label}>
            <p
              data-testid="transaction-group-label"
              className="sticky top-0 z-10 bg-card py-2 text-xs font-medium uppercase tracking-wider text-muted-foreground"
            >
              {group.label}
            </p>
            <ul className="divide-y divide-border">
              {group.items.map((tx) => (
                <TransactionRow
                  key={tx.entryId}
                  transaction={tx}
                  onOpen={onOpen}
                />
              ))}
            </ul>
          </li>
        ))}
      </ul>
      {showHundredNote ? (
        <p
          data-testid="transactions-100-note"
          className="mt-4 text-center text-xs text-muted-foreground"
        >
          Showing the 100 most recent — older transactions are not yet available
          in malai.
        </p>
      ) : null}
    </div>
  );
}

function EmptyState() {
  return (
    <div
      data-testid="transactions-empty"
      className="flex flex-col items-center px-6 py-12 text-center"
    >
      <span
        data-testid="transactions-empty-icon"
        className="flex h-12 w-12 items-center justify-center rounded-full bg-muted text-muted-foreground"
      >
        <Receipt aria-hidden="true" className="h-6 w-6" />
      </span>
      <p className="mt-4 text-base font-medium">No transactions yet</p>
      <p className="mt-1 text-sm text-muted-foreground">
        Transactions on this account will appear here once they post.
      </p>
    </div>
  );
}

function TransactionsSkeleton() {
  return (
    <div
      data-testid="transactions-skeleton"
      aria-busy="true"
      className="space-y-3"
    >
      <span className="sr-only">Loading transactions</span>
      <div className="h-12 rounded-md bg-muted/40 motion-safe:animate-pulse" />
      <div className="h-12 rounded-md bg-muted/40 motion-safe:animate-pulse" />
      <div className="h-12 rounded-md bg-muted/40 motion-safe:animate-pulse" />
    </div>
  );
}
