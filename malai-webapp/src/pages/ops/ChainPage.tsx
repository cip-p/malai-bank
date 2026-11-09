import type { MdrChainVerificationResponse } from "@/api/generated/types.gen";
import { Button } from "@/components/ui/button";
import {
  useMdrChainVerification,
  type UseMdrChainVerificationResult,
} from "@/ops/useMdrChainVerification";
import { useFocusOnMount } from "@/lib/useFocusOnMount";
import { usePageTitle } from "@/lib/usePageTitle";

export function ChainPage() {
  usePageTitle("Chain");
  const headingRef = useFocusOnMount<HTMLHeadingElement>();
  const verification = useMdrChainVerification();

  return (
    <section className="mx-auto w-full max-w-6xl px-6 py-6">
      <h1
        ref={headingRef}
        tabIndex={-1}
        className="text-2xl font-semibold tracking-tight outline-none"
      >
        Chain
      </h1>
      <p className="mt-1 max-w-prose text-sm text-muted-foreground">
        Every entry in the Model Decision Record is hash-chained to the one
        before it. Verification walks the whole record on demand and reports
        whether it is intact.
      </p>
      <div className="mt-4">
        <Button
          type="button"
          data-testid="chain-verify"
          disabled={verification.isVerifying}
          onClick={verification.verify}
        >
          {verification.isVerifying
            ? "Verifying…"
            : verification.isIdle
              ? "Verify now"
              : "Verify again"}
        </Button>
      </div>
      <ChainVerdict verification={verification} />
      {verification.isError ? (
        <p
          data-testid="chain-error"
          role="alert"
          className="mt-6 max-w-prose rounded-lg border border-border px-4 py-3 text-sm text-destructive"
        >
          Couldn&rsquo;t run the verification — the check itself failed, which
          is not a verdict on the record. Verify again.
        </p>
      ) : null}
      {verification.isVerifying ? (
        <p
          data-testid="chain-verifying"
          aria-live="polite"
          className="mt-6 max-w-prose rounded-2xl border border-dashed border-border px-6 py-5 text-sm text-muted-foreground"
        >
          Walking the record from its first entry to the chain head…
        </p>
      ) : null}
      {verification.isIdle ? (
        <p
          data-testid="chain-idle"
          className="mt-6 max-w-prose rounded-2xl border border-dashed border-border px-6 py-5 text-sm text-muted-foreground"
        >
          Verification walks the Model Decision Record from its first entry to
          the chain head, recomputing every entry&rsquo;s hash and the link to
          its predecessor. Nothing runs until you ask.
        </p>
      ) : null}
    </section>
  );
}

function ChainVerdict({
  verification,
}: {
  verification: UseMdrChainVerificationResult;
}) {
  const verdict = verification.verdict;
  if (!verdict) return null;
  if (verdict.result === "OK") {
    return (
      <div
        data-testid="chain-verdict-ok"
        className="mt-6 max-w-prose rounded-2xl border border-positive/40 bg-positive/10 px-6 py-5"
      >
        <p className="text-base font-semibold text-positive">
          The record is intact.
        </p>
        <p className="mt-1 text-sm text-muted-foreground">
          {new Intl.NumberFormat("en-GB").format(verdict.rowsVerified ?? 0)}{" "}
          entries walked from the first entry to the chain head; every link
          holds.
        </p>
      </div>
    );
  }
  return <BrokenChainVerdict verdict={verdict} />;
}

function breakExplanation(verdict: MdrChainVerificationResponse): string {
  switch (verdict.result) {
    case "GAP_AT_SEQ":
      return `The chain breaks at sequence ${verdict.atSeq}: there is a gap in the record. An entry that was written here is missing — rows have been removed.`;
    case "PREV_HASH_MISMATCH":
      return `The chain breaks at sequence ${verdict.atSeq}: this entry does not link back to the entry before it. The record has been rewritten or reordered between the two.`;
    case "CURRENT_HASH_MISMATCH":
      return `The chain breaks at sequence ${verdict.atSeq}: this entry no longer matches its own recorded hash. Its content has been altered since it was written.`;
    case "HEAD_MISMATCH":
      return "The record's head does not match its final entry. The end of the record has been truncated or tampered with.";
    default:
      return "The chain does not verify.";
  }
}

function BrokenChainVerdict({
  verdict,
}: {
  verdict: MdrChainVerificationResponse;
}) {
  return (
    <div
      data-testid="chain-verdict-broken"
      role="alert"
      className="mt-6 max-w-prose rounded-2xl border border-destructive/40 bg-destructive/10 px-6 py-5"
    >
      <p className="text-base font-semibold text-destructive">
        The record is not intact.
      </p>
      <p className="mt-1 text-sm text-foreground">
        {breakExplanation(verdict)}
      </p>
    </div>
  );
}
