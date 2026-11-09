import { Ban, Building2 } from "lucide-react";
import type { DirectDebitResponse } from "@/api/generated/types.gen";
import { Money } from "@/accounts/Money";
import { Button } from "@/components/ui/button";
import { formatNextDate } from "./formatNextDate";

type Props = {
  dd: DirectDebitResponse;
  now: Date;
  accountLabel?: string;
  onCancel?: (dd: DirectDebitResponse) => void;
};

export function DirectDebitRow({ dd, now, accountLabel, onCancel }: Props) {
  const cancelled = dd.status === "CANCELLED";
  const variable = dd.amountMinorUnits === undefined;
  const nextText = dd.nextCollectionAt
    ? formatNextDate(dd.nextCollectionAt, now)
    : "Awaiting first collection";

  return (
    <li
      data-testid="dd-row"
      id={dd.directDebitId}
      className="flex items-center gap-4 py-3"
    >
      <span
        aria-hidden="true"
        className={`flex h-10 w-10 flex-none items-center justify-center rounded-full ${
          cancelled
            ? "bg-muted text-muted-foreground"
            : "bg-accent text-accent-foreground"
        }`}
      >
        <Building2 className="h-5 w-5" />
      </span>
      <div className="min-w-0 flex-1">
        <p
          data-testid="dd-primary"
          className={`truncate font-medium ${
            cancelled ? "text-muted-foreground" : ""
          }`}
        >
          {dd.creditorName}
        </p>
        <p className="mt-0.5 flex flex-wrap items-center gap-x-2 text-xs text-muted-foreground">
          {cancelled ? (
            <span
              data-testid="dd-cancelled-badge"
              className="rounded-md border border-border px-1.5 py-0.5"
            >
              Cancelled
            </span>
          ) : (
            <span data-testid="dd-next">{nextText}</span>
          )}
          {accountLabel ? (
            <>
              <span aria-hidden="true">·</span>
              <span
                data-testid="dd-account-chip"
                className="rounded-md border border-border px-1.5 py-0.5"
              >
                {accountLabel}
              </span>
            </>
          ) : null}
        </p>
      </div>
      <div className="text-right">
        {variable ? (
          <p
            data-testid="dd-amount"
            className="font-medium tabular-nums text-muted-foreground"
          >
            Variable
          </p>
        ) : (
          <p
            data-testid="dd-amount"
            className={`font-medium ${
              cancelled ? "text-muted-foreground" : ""
            }`}
          >
            <Money
              minorUnits={dd.amountMinorUnits as number}
              currency={dd.currency}
              signed="never"
            />
          </p>
        )}
      </div>
      {onCancel && !cancelled ? (
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          data-testid="dd-cancel"
          aria-label={`Cancel direct debit to ${dd.creditorName}`}
          onClick={() => onCancel(dd)}
        >
          <Ban aria-hidden="true" />
        </Button>
      ) : null}
    </li>
  );
}
