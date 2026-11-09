import { cn } from "@/lib/utils";

type Props = {
  className?: string;
};

export function RouteFallback({ className }: Props) {
  return (
    <div
      data-testid="route-fallback"
      aria-busy="true"
      className={cn("mx-auto w-full max-w-5xl p-6", className)}
    >
      <span className="sr-only">Loading</span>
      <div className="h-8 w-48 rounded-md bg-muted/40 motion-safe:animate-pulse" />
      <div className="mt-6 h-20 rounded-lg border border-border bg-muted/40 motion-safe:animate-pulse" />
    </div>
  );
}
