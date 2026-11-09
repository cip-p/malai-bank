import { useEffect, useRef } from "react";
import type { AmlCaseDetailResponse } from "@/api/generated/types.gen";
import { Button } from "@/components/ui/button";
import { isTerminal } from "@/ops/caseTags";
import type { CloseOutcome, CloseRefusalReason } from "@/ops/closeRefusal";
import { useCloseCase, type CloseAction } from "@/ops/useCloseCase";
import { useTwoPressConfirm } from "@/ops/useTwoPressConfirm";

const ACTION_LABEL: Record<CloseAction, string> = {
  dismissed: "Close as dismissed",
  reported: "Close as reported",
};

const ARMED_LABEL: Record<CloseAction, string> = {
  dismissed: "Confirm — close as dismissed",
  reported: "Confirm — close as reported",
};

const REFUSAL_FACT: Record<CloseRefusalReason, string> = {
  "accepted-alert-stands":
    "Not dismissed: an Alert on this Case carries an accept ruling in force. Confirmed suspicion cannot be waved away — reverse that ruling at the Alert's decision first.",
  "no-sar-ruling-in-force":
    "Not reported: no SAR narrative has been ruled acceptable. Draft one and accept or edit it at its record, then close again. The Case stays exactly where it was.",
  "already-closed":
    "Already closed. Another officer got here first, or this close already landed — nothing was changed by this press. The Case above refreshes itself.",
  unknown:
    "Refused. The Case is unchanged and the refusal is on the record; the reason it gave is not one this screen knows how to name.",
};

export function CloseActions({ detail }: { detail: AmlCaseDetailResponse }) {
  const { close, pending, outcome } = useCloseCase(detail.caseId);
  const { armed, press, disarm } = useTwoPressConfirm<CloseAction>(close);
  const actionsRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (armed === null) return;
    const onPointerDown = (event: MouseEvent) => {
      const target = event.target;
      if (target instanceof Node && actionsRef.current?.contains(target))
        return;
      disarm();
    };
    document.addEventListener("pointerdown", onPointerDown);
    return () => document.removeEventListener("pointerdown", onPointerDown);
  }, [armed, disarm]);

  if (isTerminal(detail.status)) return null;

  return (
    <section
      aria-labelledby="close-heading"
      data-testid="close-actions"
      className="mt-8"
    >
      <h2 id="close-heading" className="text-lg font-semibold">
        Close the case
      </h2>
      <p className="mt-1 text-sm text-muted-foreground">
        Both closes are terminal and both are ruled: dismissing needs no accept
        ruling standing on any Alert, and reporting reads the SAR ruling in
        force at the moment you press. Press once to arm, again to close.
      </p>
      <div ref={actionsRef} className="mt-3 flex flex-wrap gap-3">
        <CloseButton
          action="dismissed"
          armed={armed}
          pending={pending}
          press={press}
        />
        <CloseButton
          action="reported"
          armed={armed}
          pending={pending}
          press={press}
        />
      </div>
      <CloseFailure outcome={outcome} />
    </section>
  );
}

function CloseButton({
  action,
  armed,
  pending,
  press,
}: {
  action: CloseAction;
  armed: CloseAction | null;
  pending: CloseAction | null;
  press: (action: CloseAction) => void;
}) {
  const isArmed = armed === action;
  const label = pending === action ? "Closing…" : null;

  return (
    <Button
      type="button"
      variant="outline"
      data-testid={`close-${action}`}
      disabled={pending !== null}
      onClick={() => press(action)}
    >
      {label ?? (isArmed ? ARMED_LABEL[action] : ACTION_LABEL[action])}
    </Button>
  );
}

function CloseFailure({ outcome }: { outcome: CloseOutcome | null }) {
  if (outcome === null) return null;

  if (outcome.kind === "failed") {
    return (
      <p
        data-testid="close-failed"
        role="alert"
        className="mt-3 rounded-lg border border-border px-4 py-3 text-sm text-destructive"
      >
        Couldn&rsquo;t close the case — the request itself failed, which is not
        a refusal. Closing again re-sends this same attempt, never a second
        close.
      </p>
    );
  }

  return (
    <p
      data-testid="close-refusal"
      role="status"
      className="mt-3 rounded-lg border border-dashed border-border px-4 py-3 text-sm text-muted-foreground"
    >
      {REFUSAL_FACT[outcome.reason]}
    </p>
  );
}
