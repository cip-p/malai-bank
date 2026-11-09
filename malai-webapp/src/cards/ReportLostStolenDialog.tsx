import { useRef } from "react";
import { AlertDialog } from "radix-ui";
import { Button } from "@/components/ui/button";
import {
  alertDialogContentClassName,
  scrimClassName,
} from "@/components/scrim";

type Props = {
  open: boolean;
  onCancel: () => void;
  onConfirm: () => void;
};

export function ReportLostStolenDialog({ open, onCancel, onConfirm }: Props) {
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
          data-testid="report-lost-dialog"
          className={alertDialogContentClassName}
        >
          <AlertDialog.Title className="text-lg font-semibold">
            Report lost or stolen
          </AlertDialog.Title>
          <AlertDialog.Description
            data-testid="report-lost-description"
            className="mt-2 text-sm text-muted-foreground"
          >
            This card will be cancelled immediately and can’t be used again. A
            replacement card with a new number is issued straight away.
          </AlertDialog.Description>
          <div className="mt-6 flex justify-end gap-2">
            <AlertDialog.Cancel asChild>
              <Button
                type="button"
                variant="outline"
                data-testid="report-lost-keep"
              >
                Keep this card
              </Button>
            </AlertDialog.Cancel>
            <AlertDialog.Action asChild>
              <Button
                type="button"
                variant="destructive"
                data-testid="report-lost-confirm"
                onClick={() => {
                  confirming.current = true;
                  onConfirm();
                }}
              >
                Cancel and replace
              </Button>
            </AlertDialog.Action>
          </div>
        </AlertDialog.Content>
      </AlertDialog.Portal>
    </AlertDialog.Root>
  );
}
