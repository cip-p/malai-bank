import type { TransactionItemResponse } from "@/api/generated/types.gen";
import { formatDeclineReason } from "@/cards/formatDeclineReason";
import { CategoryIcon } from "@/lib/CategoryIcon";
import { CategoryPill } from "@/lib/CategoryPill";
import { cn } from "@/lib/utils";
import { Money } from "./Money";

type Props = {
  transaction: TransactionItemResponse;
  onOpen: (transaction: TransactionItemResponse) => void;
};

export function TransactionRow({ transaction, onOpen }: Props) {
  const pending = transaction.status === "PENDING";
  const declined = transaction.status === "DECLINED";
  const primary =
    transaction.merchantName ?? transaction.description ?? "Transaction";
  const secondary = declined
    ? formatDeclineReason(transaction.declineReason)
    : transaction.merchantName && transaction.description
      ? transaction.description
      : undefined;

  return (
    <li>
      <button
        type="button"
        onClick={() => onOpen(transaction)}
        className="flex w-full items-center gap-3 rounded-md px-2 py-3 text-left outline-none hover:bg-muted/40 focus-visible:bg-muted/40 focus-visible:ring-2 focus-visible:ring-ring/50"
      >
        <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-muted text-muted-foreground">
          <CategoryIcon category={transaction.category} className="h-5 w-5" />
        </span>
        <span className="min-w-0 flex-1">
          <span className="flex flex-wrap items-center gap-x-2 gap-y-1">
            <span
              data-testid="transaction-primary"
              className={cn(
                "block truncate font-medium",
                declined && "text-muted-foreground",
              )}
            >
              {primary}
            </span>
            {pending ? (
              <span
                data-testid="transaction-pending-badge"
                className="shrink-0 rounded-md border border-border px-1.5 py-0.5 text-xs font-medium text-muted-foreground"
              >
                Pending
              </span>
            ) : null}
            {declined ? (
              <span
                data-testid="transaction-declined-badge"
                className="shrink-0 rounded-md border border-destructive/40 px-1.5 py-0.5 text-xs font-medium text-destructive"
              >
                Declined
              </span>
            ) : null}
          </span>
          {transaction.category || secondary ? (
            <span className="mt-1 flex items-center gap-2">
              {!declined && transaction.category ? (
                <CategoryPill category={transaction.category} />
              ) : null}
              {secondary ? (
                <span
                  data-testid="transaction-secondary"
                  className="truncate text-xs text-muted-foreground"
                >
                  {secondary}
                </span>
              ) : null}
            </span>
          ) : null}
        </span>
        <span
          data-testid="transaction-amount"
          className={cn(
            "shrink-0 text-right font-medium",
            pending && "text-muted-foreground",
            declined && "text-muted-foreground line-through",
          )}
        >
          <Money
            minorUnits={transaction.amountMinorUnits}
            currency={transaction.currency}
            signed={declined ? "never" : "always"}
          />
        </span>
      </button>
    </li>
  );
}
