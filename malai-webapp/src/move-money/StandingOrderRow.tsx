import { Link } from "react-router-dom";
import { Ban } from "lucide-react";
import type { StandingOrderResponse } from "@/api/generated/types.gen";
import { Money } from "@/accounts/Money";
import { Button } from "@/components/ui/button";
import { formatFrequency } from "./formatFrequency";
import { formatNextDate } from "./formatNextDate";
import { InitialAvatar } from "./InitialAvatar";

type Props = {
  so: StandingOrderResponse;
  now: Date;
  payeeHref?: string;
  accountLabel?: string;
  onCancel?: (so: StandingOrderResponse) => void;
};

export function StandingOrderRow({
  so,
  now,
  payeeHref,
  accountLabel,
  onCancel,
}: Props) {
  const nextText = formatNextDate(so.nextPaymentAt ?? null, now);

  return (
    <li
      data-testid="so-row"
      id={so.standingOrderId}
      className="flex items-center gap-4 py-3"
    >
      <InitialAvatar label={so.payeeName} />
      <div className="min-w-0 flex-1">
        <p data-testid="so-primary" className="truncate font-medium">
          {payeeHref ? (
            <Link
              to={payeeHref}
              className="rounded-sm outline-none hover:underline focus-visible:underline focus-visible:ring-2 focus-visible:ring-ring/50"
            >
              {so.payeeName}
            </Link>
          ) : (
            so.payeeName
          )}
        </p>
        <p className="mt-0.5 flex flex-wrap items-center gap-x-2 text-xs text-muted-foreground">
          <span data-testid="so-frequency">
            {formatFrequency(so.frequency)}
          </span>
          <span aria-hidden="true">·</span>
          <span data-testid="so-next">{nextText}</span>
          {accountLabel ? (
            <>
              <span aria-hidden="true">·</span>
              <span
                data-testid="so-account-chip"
                className="rounded-md border border-border px-1.5 py-0.5"
              >
                {accountLabel}
              </span>
            </>
          ) : null}
        </p>
      </div>
      <div className="text-right">
        <p data-testid="so-amount" className="font-medium">
          <Money
            minorUnits={so.amountMinorUnits}
            currency={so.currency}
            signed="never"
          />
        </p>
      </div>
      {onCancel ? (
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          data-testid="so-cancel"
          aria-label={`Cancel standing order to ${so.payeeName}`}
          onClick={() => onCancel(so)}
        >
          <Ban aria-hidden="true" />
        </Button>
      ) : null}
    </li>
  );
}
