import { Mail, RefreshCw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useFocusOnMount } from "@/lib/useFocusOnMount";
import { HeaderWordmark } from "@/theming/HeaderWordmark";
import { useTheme } from "@/theming/useTheme";

export const CONTACT_EMAIL = "info@malaibank.com";

const MAILTO = `mailto:${CONTACT_EMAIL}?subject=${encodeURIComponent(
  "I'd like to try malai",
)}`;

type Props = {
  rechecking: boolean;
  onRetry: () => void;
};

export function ServicePausedPage({ rechecking, onRetry }: Props) {
  const headingRef = useFocusOnMount<HTMLHeadingElement>();
  const { theme, themes } = useTheme();
  const brand =
    themes.find((candidate) => candidate.id === theme)?.label ?? "Malai";

  return (
    <main
      id="main"
      className="relative flex min-h-dvh flex-col items-center justify-center overflow-hidden px-6 py-16"
    >
      <div
        aria-hidden="true"
        className="pointer-events-none absolute -top-40 left-1/2 size-[40rem] -translate-x-1/2 rounded-full bg-[radial-gradient(closest-side,color-mix(in_oklab,var(--primary)_9%,transparent),transparent)]"
      />

      <div className="relative flex w-full max-w-lg flex-col items-center text-center">
        <HeaderWordmark className="h-14 w-auto text-primary sm:h-20" />

        <h1
          ref={headingRef}
          tabIndex={-1}
          data-testid="service-paused-heading"
          className="mt-10 text-2xl font-semibold tracking-tight text-balance outline-none sm:text-3xl"
        >
          The application is paused at the moment
        </h1>

        <p
          data-testid="service-paused-body"
          className="mt-4 text-base text-pretty text-muted-foreground"
        >
          {brand}&rsquo;s services are switched off for now. If you&rsquo;d like
          to try the bank for yourself, drop us an email and we&rsquo;ll open it
          back up for you — we&rsquo;re more than happy to help.
        </p>

        <div className="mt-8 flex w-full flex-col items-stretch gap-3 sm:w-auto sm:flex-row sm:items-center">
          <Button asChild size="lg" className="px-4">
            <a href={MAILTO} data-testid="service-paused-email">
              <Mail aria-hidden="true" />
              {CONTACT_EMAIL}
            </a>
          </Button>
          <Button
            type="button"
            variant="outline"
            size="lg"
            className="px-4"
            onClick={onRetry}
            disabled={rechecking}
            data-testid="service-paused-retry"
          >
            <RefreshCw
              aria-hidden="true"
              className={rechecking ? "motion-safe:animate-spin" : undefined}
            />
            Check again
          </Button>
        </div>

        <p
          role="status"
          aria-live="polite"
          data-testid="service-paused-status"
          className="mt-6 text-xs text-muted-foreground"
        >
          {rechecking
            ? `Checking whether ${brand} is back…`
            : `We keep checking in the background and this page will let you straight in when ${brand} is back.`}
        </p>
      </div>
    </main>
  );
}
