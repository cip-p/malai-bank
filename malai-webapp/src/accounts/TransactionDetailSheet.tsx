import { Dialog } from "radix-ui";
import { Link } from "react-router-dom";
import { format } from "date-fns";
import { X } from "lucide-react";
import type { TransactionItemResponse } from "@/api/generated/types.gen";
import { formatDeclineReason } from "@/cards/formatDeclineReason";
import { CategoryIcon } from "@/lib/CategoryIcon";
import { Money } from "./Money";
import { TransactionCategoryEditor } from "./TransactionCategoryEditor";
import { scrimClassName } from "@/components/scrim";

type Props = {
  transaction: TransactionItemResponse;
  accountUid: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
};

export function TransactionDetailSheet({
  transaction,
  accountUid,
  open,
  onOpenChange,
}: Props) {
  const title =
    transaction.merchantName ?? transaction.description ?? "Transaction";
  const posted = format(
    new Date(transaction.postedAt),
    "d MMMM yyyy 'at' HH:mm",
  );
  const pending = transaction.status === "PENDING";
  const declined = transaction.status === "DECLINED";
  const cardRow = pending || declined;

  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Overlay className={scrimClassName} />
        <Dialog.Content
          data-testid="transaction-sheet"
          className="fixed inset-x-0 bottom-0 z-50 max-h-[90dvh] overflow-y-auto rounded-t-2xl border border-border bg-background p-6 pb-[calc(env(safe-area-inset-bottom)+1.5rem)] shadow-lg motion-safe:data-[state=open]:animate-in motion-safe:data-[state=closed]:animate-out motion-safe:duration-300 motion-safe:data-[state=closed]:duration-200 motion-safe:ease-arrive motion-safe:data-[state=closed]:ease-in max-md:motion-safe:data-[state=open]:slide-in-from-bottom max-md:motion-safe:data-[state=closed]:slide-out-to-bottom md:inset-x-auto md:top-0 md:right-0 md:bottom-0 md:max-h-none md:w-[420px] md:rounded-none md:rounded-l-2xl md:pb-6 md:motion-safe:data-[state=open]:slide-in-from-right md:motion-safe:data-[state=closed]:slide-out-to-right"
        >
          <div className="flex items-start justify-between gap-4">
            <div className="flex min-w-0 items-start gap-3">
              <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-muted text-muted-foreground">
                <CategoryIcon
                  category={transaction.category}
                  className="h-5 w-5"
                />
              </span>
              <div className="min-w-0">
                <Dialog.Title
                  data-testid="transaction-sheet-title"
                  className="truncate text-lg font-semibold"
                >
                  {title}
                </Dialog.Title>
                <p
                  data-testid="transaction-sheet-posted"
                  className="mt-0.5 text-xs text-muted-foreground"
                >
                  {posted}
                </p>
              </div>
            </div>
            <Dialog.Close
              data-testid="transaction-sheet-close"
              aria-label="Close"
              className="-mt-3 -mr-3 rounded-md p-2 text-muted-foreground outline-none hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring/50 pointer-coarse:-mt-4 pointer-coarse:-mr-4 pointer-coarse:p-3.5"
            >
              <X aria-hidden="true" className="h-4 w-4" />
            </Dialog.Close>
          </div>

          <div className="mt-6">
            <p
              data-testid="transaction-sheet-amount"
              className={
                declined
                  ? "text-3xl font-semibold tracking-tight text-muted-foreground line-through"
                  : "text-3xl font-semibold tracking-tight"
              }
            >
              <Money
                minorUnits={transaction.amountMinorUnits}
                currency={transaction.currency}
                signed={declined ? "never" : "always"}
              />
            </p>
            {pending ? (
              <p
                data-testid="transaction-sheet-pending-note"
                className="mt-1 text-sm text-muted-foreground"
              >
                Pending — the money is set aside and this will settle shortly.
              </p>
            ) : declined ? (
              <p
                data-testid="transaction-sheet-declined-note"
                className="mt-1 text-sm text-muted-foreground"
              >
                Declined — no money left your account.
              </p>
            ) : (
              <p className="mt-1 text-sm text-muted-foreground">
                Balance after this transaction{" "}
                <span
                  data-testid="transaction-sheet-running-balance"
                  className="font-medium text-foreground"
                >
                  <Money
                    minorUnits={transaction.runningBalanceMinorUnits}
                    currency={transaction.currency}
                    signed="negative"
                  />
                </span>
              </p>
            )}
          </div>

          <Dialog.Description className="sr-only">
            Transaction details
          </Dialog.Description>

          <dl className="mt-6 grid grid-cols-1 gap-3 text-sm">
            {cardRow ? (
              <DetailRow label="Status" testId="transaction-sheet-status">
                {pending ? "Pending" : "Declined"}
              </DetailRow>
            ) : null}
            {declined ? (
              <DetailRow
                label="Reason"
                testId="transaction-sheet-decline-reason"
              >
                {formatDeclineReason(transaction.declineReason)}
              </DetailRow>
            ) : null}
            {transaction.merchantName ? (
              <DetailRow label="Merchant" testId="transaction-sheet-merchant">
                {transaction.merchantName}
              </DetailRow>
            ) : null}
            {transaction.description ? (
              <DetailRow
                label="Description"
                testId="transaction-sheet-description"
              >
                {transaction.description}
              </DetailRow>
            ) : null}
            {transaction.counterpartyId ? (
              <DetailRow
                label="Counterparty"
                testId="transaction-sheet-counterparty"
              >
                {transaction.counterpartyId}
              </DetailRow>
            ) : null}
            {transaction.cardId ? (
              <DetailRow label="Card" testId="transaction-sheet-card">
                <Link
                  to={`/cards/${transaction.cardId}`}
                  data-testid="transaction-sheet-card-link"
                  className="hover:underline focus-visible:underline"
                >
                  View card
                </Link>
              </DetailRow>
            ) : null}
            {!cardRow ? (
              <TransactionCategoryEditor
                key={transaction.entryId}
                accountUid={accountUid}
                entryId={transaction.entryId}
                category={transaction.category}
              />
            ) : null}
          </dl>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

function DetailRow({
  label,
  children,
  testId,
}: {
  label: string;
  children: React.ReactNode;
  testId: string;
}) {
  return (
    <div className="flex items-baseline justify-between gap-4">
      <dt className="text-muted-foreground">{label}</dt>
      <dd data-testid={testId} className="text-right font-medium">
        {children}
      </dd>
    </div>
  );
}
