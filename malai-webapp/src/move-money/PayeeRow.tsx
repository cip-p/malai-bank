import { Trash2 } from "lucide-react";
import type { PayeeResponse } from "@/api/generated/types.gen";
import { Button } from "@/components/ui/button";
import { InitialAvatar } from "./InitialAvatar";

type Props = {
  payee: PayeeResponse;
  onDelete?: (payee: PayeeResponse) => void;
};

export function PayeeRow({ payee, onDelete }: Props) {
  const primary = payee.nickname ?? payee.name;

  return (
    <li
      data-testid="payee-row"
      id={payee.payeeId}
      className="flex items-center gap-4 py-3"
    >
      <InitialAvatar label={primary} />
      <div className="min-w-0 flex-1">
        <p data-testid="payee-primary" className="truncate font-medium">
          {primary}
        </p>
        <p
          data-testid="payee-secondary"
          className="mt-0.5 flex flex-wrap items-center gap-x-2 truncate text-xs tabular-nums text-muted-foreground"
        >
          <span data-testid="payee-sort-code">{payee.sortCode}</span>
          <span aria-hidden="true">·</span>
          <span data-testid="payee-account-number">{payee.accountNumber}</span>
        </p>
      </div>
      {onDelete ? (
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          data-testid="payee-delete"
          aria-label={`Remove ${primary}`}
          onClick={() => onDelete(payee)}
        >
          <Trash2 aria-hidden="true" />
        </Button>
      ) : null}
    </li>
  );
}
