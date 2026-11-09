import { useRef } from "react";
import { AlertDialog } from "radix-ui";
import type { DirectDebitResponse } from "@/api/generated/types.gen";
import { Button } from "@/components/ui/button";
import {
  alertDialogContentClassName,
  scrimClassName,
} from "@/components/scrim";

type Props = {
  open: boolean;
  directDebit: DirectDebitResponse;
  onCancel: () => void;
  onConfirm: () => void;
};

export function CancelDirectDebitDialog({
  open,
  directDebit,
  onCancel,
  onConfirm,
}: Props) {
  const confirming = useRef(false);

  return (
    <AlertDialog.Root
      open={open}
      onOpenChange={(next) => {
        if (next) return;
        if (confirming.current) {
          confirming.current = false;
          return;
        }
        onCancel();
      }}
    >
      <AlertDialog.Portal>
        <AlertDialog.Overlay className={scrimClassName} />
        <AlertDialog.Content
          data-testid="cancel-dd-dialog"
          className={alertDialogContentClassName}
        >
          <AlertDialog.Title className="text-lg font-semibold">
            Cancel direct debit
          </AlertDialog.Title>
          <AlertDialog.Description
            data-testid="cancel-dd-description"
            className="mt-2 text-sm text-muted-foreground"
          >
            {`Cancel the direct debit to ${directDebit.creditorName}? Future collections will stop, and past collections stay in your transaction history. Cancelling with the bank doesn’t cancel your agreement with ${directDebit.creditorName} — tell them too.`}
          </AlertDialog.Description>
          <div className="mt-6 flex justify-end gap-2">
            <AlertDialog.Cancel asChild>
              <Button
                type="button"
                variant="outline"
                data-testid="cancel-dd-keep"
              >
                Keep direct debit
              </Button>
            </AlertDialog.Cancel>
            <AlertDialog.Action asChild>
              <Button
                type="button"
                variant="destructive"
                data-testid="cancel-dd-confirm"
                onClick={() => {
                  confirming.current = true;
                  onConfirm();
                }}
              >
                Cancel direct debit
              </Button>
            </AlertDialog.Action>
          </div>
        </AlertDialog.Content>
      </AlertDialog.Portal>
    </AlertDialog.Root>
  );
}
