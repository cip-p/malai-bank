import { Link } from "react-router-dom";
import { useFocusOnMount } from "@/lib/useFocusOnMount";

export function AccountNotFoundPage() {
  const headingRef = useFocusOnMount<HTMLHeadingElement>();
  return (
    <section className="mt-6 max-w-xl">
      <h1
        ref={headingRef}
        tabIndex={-1}
        data-testid="account-not-found-heading"
        className="text-2xl font-semibold tracking-tight outline-none"
      >
        Account not found
      </h1>
      <p className="mt-2 text-sm text-muted-foreground">
        We couldn&apos;t find that account. It may have been closed or the link
        may be wrong.
      </p>
      <Link
        to="/"
        data-testid="account-not-found-back-link"
        className="mt-6 inline-block text-sm text-muted-foreground hover:underline focus-visible:underline"
      >
        ← Back to Accounts
      </Link>
    </section>
  );
}
