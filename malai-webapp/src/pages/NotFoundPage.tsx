import { Link } from "react-router-dom";
import { useFocusOnMount } from "@/lib/useFocusOnMount";

export function NotFoundPage() {
  const headingRef = useFocusOnMount<HTMLHeadingElement>();
  return (
    <main className="flex min-h-dvh flex-col items-center justify-center px-6 text-center">
      <p className="text-sm font-medium tracking-widest text-muted-foreground">
        404
      </p>
      <h1
        ref={headingRef}
        tabIndex={-1}
        data-testid="not-found-heading"
        className="mt-3 text-2xl font-semibold tracking-tight outline-none"
      >
        Page not found
      </h1>
      <p className="mt-2 max-w-sm text-sm text-muted-foreground">
        We couldn&apos;t find the page you were looking for. The link may be
        wrong, or the page may have moved.
      </p>
      <Link
        to="/"
        data-testid="not-found-home-link"
        className="mt-6 inline-block text-sm text-muted-foreground hover:underline focus-visible:underline"
      >
        ← Back to home
      </Link>
    </main>
  );
}
