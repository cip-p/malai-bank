import { useCallback, useState } from "react";
import { Compass, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  offeredSurfaces,
  type Role,
  type SurfaceId,
} from "@/surfaces/surfaceOffering";

export const FEATURE_HINT_STORAGE_KEY = "malai:feature-hint-dismissed";

export function readFeatureHintDismissed(): boolean {
  if (typeof window === "undefined") return false;
  try {
    return window.localStorage.getItem(FEATURE_HINT_STORAGE_KEY) === "true";
  } catch {
    return false;
  }
}

export function useFeatureHint() {
  const [dismissed, setDismissed] = useState(() => readFeatureHintDismissed());

  const dismiss = useCallback(() => {
    setDismissed(true);
    try {
      window.localStorage.setItem(FEATURE_HINT_STORAGE_KEY, "true");
    } catch {
      void 0;
    }
  }, []);

  return { visible: !dismissed, dismiss };
}

const SURFACE_BLURBS: Record<SurfaceId, string | null> = {
  banking: null,
  ops: "the Ops review worklist",
  exec: "the Exec dashboard",
};

export function hintDestinations(roles: readonly Role[]): string {
  const staff = offeredSurfaces(roles)
    .map((surface) => SURFACE_BLURBS[surface.id])
    .filter((blurb): blurb is string => blurb !== null);
  const all = [...staff, "the Sandbox"];
  if (all.length === 1) return all[0];
  if (all.length === 2) return `${all[0]} and ${all[1]}`;
  const last = all[all.length - 1];
  return `${all.slice(0, -1).join(", ")}, and ${last}`;
}

type Props = {
  roles: readonly Role[];
  onDismiss: () => void;
};

export function FeatureHintBanner({ roles, onDismiss }: Props) {
  return (
    <div
      data-testid="feature-hint-banner"
      className="border-b border-border bg-accent text-accent-foreground"
    >
      <div className="mx-auto flex w-full max-w-5xl items-center gap-3 px-4 py-2 md:px-6">
        <Compass
          aria-hidden="true"
          className="hidden size-4 shrink-0 sm:block"
        />
        <p className="flex-1 text-sm leading-snug">
          <span className="font-medium">New here?</span>{" "}
          <span
            data-testid="feature-hint-location-desktop"
            className="hidden md:inline"
          >
            The menu under your name, top right,
          </span>
          <span
            data-testid="feature-hint-location-mobile"
            className="md:hidden"
          >
            The Profile tab
          </span>{" "}
          also opens {hintDestinations(roles)}
          <span className="hidden sm:inline">
            {" "}
            for simulating inbound payments and card activity
          </span>
          .
        </p>
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          onClick={onDismiss}
          aria-label="Dismiss this tip"
          data-testid="feature-hint-dismiss"
          className="-mr-1 shrink-0 hover:bg-accent-foreground/10"
        >
          <X aria-hidden="true" />
        </Button>
      </div>
    </div>
  );
}
