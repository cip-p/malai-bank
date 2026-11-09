import { useRef } from "react";
import { AlertDialog } from "radix-ui";
import type { StandingOrderResponse } from "@/api/generated/types.gen";
import { Button } from "@/components/ui/button";
import {
  alertDialogContentClassName,
  scrimClassName,
} from "@/components/scrim";

type Props = {
  open: boolean;
  standingOrder: StandingOrderResponse;
  onCancel: () => void;
  onConfirm: () => void;
};

export function CancelStandingOrderDialog({
  open,
  standingOrder,
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
          data-testid="cancel-so-dialog"
          className={alertDialogContentClassName}
        >
          <AlertDialog.Title className="text-lg font-semibold">
            Cancel standing order
          </AlertDialog.Title>
          <AlertDialog.Description
            data-testid="cancel-so-description"
            className="mt-2 text-sm text-muted-foreground"
          >
            {`Cancel the standing order to ${standingOrder.payeeName}? Future payments will stop. Payments already made stay in your transaction history.`}
          </AlertDialog.Description>
          <div className="mt-6 flex justify-end gap-2">
            <AlertDialog.Cancel asChild>
              <Button
                type="button"
                variant="outline"
                data-testid="cancel-so-keep"
              >
                Keep standing order
              </Button>
            </AlertDialog.Cancel>
            <AlertDialog.Action asChild>
              <Button
                type="button"
                variant="destructive"
                data-testid="cancel-so-confirm"
                onClick={() => {
                  confirming.current = true;
                  onConfirm();
                }}
              >
                Cancel standing order
              </Button>
            </AlertDialog.Action>
          </div>
        </AlertDialog.Content>
      </AlertDialog.Portal>
    </AlertDialog.Root>
  );
}
