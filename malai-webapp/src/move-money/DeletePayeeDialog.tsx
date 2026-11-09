import { useRef } from "react";
import { AlertDialog } from "radix-ui";
import { Link } from "react-router-dom";
import type {
  PayeeResponse,
  StandingOrderResponse,
} from "@/api/generated/types.gen";
import { Button } from "@/components/ui/button";
import {
  alertDialogContentClassName,
  scrimClassName,
} from "@/components/scrim";

type Props = {
  open: boolean;
  payee: PayeeResponse;
  linkedStandingOrders: StandingOrderResponse[];
  onCancel: () => void;
  onConfirm: () => void;
};

const STANDING_ORDERS_PATH = "/move/standing-orders";

export function DeletePayeeDialog({
  open,
  payee,
  linkedStandingOrders,
  onCancel,
  onConfirm,
}: Props) {
  const confirming = useRef(false);
  const displayName = payee.nickname ?? payee.name;
  const activeCount = linkedStandingOrders.filter(
    (so) => so.status === "ACTIVE",
  ).length;
  const blocked = activeCount > 0;
  const title = blocked ? "Can’t remove payee" : "Remove payee";
  const description = blocked
    ? blockedDescription(displayName, activeCount)
    : `Remove ${displayName}? You can add them again later.`;

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
          data-testid="delete-payee-dialog"
          className={alertDialogContentClassName}
        >
          <AlertDialog.Title className="text-lg font-semibold">
            {title}
          </AlertDialog.Title>
          <AlertDialog.Description
            data-testid="delete-payee-description"
            className="mt-2 text-sm text-muted-foreground"
          >
            {description}
          </AlertDialog.Description>
          <div className="mt-6 flex justify-end gap-2">
            <AlertDialog.Cancel asChild>
              <Button
                type="button"
                variant="outline"
                data-testid="delete-payee-cancel"
              >
                {blocked ? "Close" : "Cancel"}
              </Button>
            </AlertDialog.Cancel>
            {blocked ? (
              <Button asChild data-testid="delete-payee-view-standing-orders">
                <Link to={STANDING_ORDERS_PATH}>View standing orders</Link>
              </Button>
            ) : (
              <AlertDialog.Action asChild>
                <Button
                  type="button"
                  variant="destructive"
                  data-testid="delete-payee-confirm"
                  onClick={() => {
                    confirming.current = true;
                    onConfirm();
                  }}
                >
                  Remove
                </Button>
              </AlertDialog.Action>
            )}
          </div>
        </AlertDialog.Content>
      </AlertDialog.Portal>
    </AlertDialog.Root>
  );
}

function blockedDescription(displayName: string, activeCount: number): string {
  if (activeCount === 1) {
    return `You can’t remove ${displayName} while a standing order still pays them. Cancel the standing order first, then remove the payee.`;
  }
  return `You can’t remove ${displayName} while ${activeCount} standing orders still pay them. Cancel them first, then remove the payee.`;
}
