import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import type { AmlCaseDetailResponse } from "@/api/generated/types.gen";
import { Button } from "@/components/ui/button";
import { RulingTag } from "@/ops/decisionTags";
import { sarPanelState, type SarPanelState } from "@/ops/sarPanelState";
import { useSarDraft } from "@/ops/useSarDraft";

const ID_PREFIX = 8;

const NO_ANSWER_FACT: Record<
  Extract<SarPanelState, { kind: "no-answer" }>["outcome"],
  string
> = {
  TIMED_OUT:
    "No narrative was produced — the model exceeded its budget and was abandoned. The invocation is on the record all the same.",
  ERRORED:
    "No narrative was produced — the invocation failed. It is on the record all the same.",
};

export function SarPanel({ detail }: { detail: AmlCaseDetailResponse }) {
  const { invocation, requestDraft } = useSarDraft(detail.caseId);
  const state = sarPanelState(detail.status, detail.sar, invocation);

  return (
    <section
      aria-labelledby="sar-heading"
      data-testid="sar-panel"
      className="mt-8"
    >
      <h2 id="sar-heading" className="text-lg font-semibold">
        Suspicious Activity Report
      </h2>
      <p className="mt-1 text-sm text-muted-foreground">
        The narrative is drafted from this Case&rsquo;s timeline and cited back
        to it. Drafting is an officer action — it appends to the chain and moves
        the investigation on — but it files nothing: the draft becomes the
        Case&rsquo;s narrative only when a ruling on its record says so.
      </p>
      <SarOutcome
        state={state}
        requestDraft={requestDraft}
        caseId={detail.caseId}
      />
    </section>
  );
}

function SarOutcome({
  state,
  requestDraft,
  caseId,
}: {
  state: SarPanelState;
  requestDraft: () => void;
  caseId: string;
}) {
  if (state.kind === "requestable" || state.kind === "drafting") {
    return (
      <div className="mt-3">
        <Button
          type="button"
          variant="outline"
          data-testid="sar-request"
          disabled={state.kind === "drafting"}
          onClick={requestDraft}
        >
          {state.kind === "drafting" ? "Drafting…" : "Draft the SAR narrative"}
        </Button>
      </div>
    );
  }

  if (state.kind === "never-drafted") {
    return (
      <Fact testId="sar-never-drafted">
        No narrative was ever drafted on this Case, and none can be now: it is
        closed.
      </Fact>
    );
  }

  if (state.kind === "grounded") {
    return (
      <div className="mt-3 space-y-3">
        <Narrative testId="sar-narrative" text={state.narrative} />
        <p className="text-sm text-muted-foreground">
          A draft, not the filed narrative — it is nothing until it is ruled on.
        </p>
        <RecordLink
          caseId={caseId}
          decisionId={state.decisionId}
          label="Read and rule on this draft at its record"
        />
      </div>
    );
  }

  if (state.kind === "refused") {
    return (
      <Fact testId="sar-refused">
        <p>
          A narrative was produced and withheld: it cited a record outside this
          Case&rsquo;s assembled timeline, or cited nothing at all, so grounding
          failed and the whole draft was refused rather than served.
        </p>
        <RecordLink
          caseId={caseId}
          decisionId={state.decisionId}
          label="Read the refused draft on the record"
        />
      </Fact>
    );
  }

  if (state.kind === "no-answer") {
    return (
      <Fact testId="sar-no-answer">
        <p>{NO_ANSWER_FACT[state.outcome]}</p>
        <RecordLink
          caseId={caseId}
          decisionId={state.decisionId}
          label="Read the record of this invocation"
        />
      </Fact>
    );
  }

  if (state.kind === "request-failed") {
    return (
      <p
        data-testid="sar-request-failed"
        role="alert"
        className="mt-3 rounded-lg border border-border px-4 py-3 text-sm text-destructive"
      >
        Couldn&rsquo;t request a draft — the request itself failed, which is not
        a refusal. Requesting again re-sends this same invocation, never a
        second one.
      </p>
    );
  }

  if (state.kind === "on-record") {
    return (
      <div
        data-testid="sar-on-record"
        className="mt-3 space-y-2 rounded-lg border border-border px-4 py-3 text-sm"
      >
        <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
          <span className="font-medium">Drafted</span>
          <RulingTag ruling={state.rulingInForce} testId="sar-ruling" />
        </div>
        <p className="text-muted-foreground">
          The draft lives on the record, not on this screen — a drafted
          narrative is read and ruled where every model output is.
        </p>
        <RecordLink
          caseId={caseId}
          decisionId={state.decisionId}
          label="Read and rule on the draft at its record"
        />
      </div>
    );
  }

  return (
    <div className="mt-3 space-y-3">
      <Narrative testId="sar-attached" text={state.narrative} />
      <p className="text-sm text-muted-foreground">
        Filed with this Case, exactly as ruled.
      </p>
      <RecordLink
        caseId={caseId}
        decisionId={state.decisionId}
        label="Read the decision this narrative was filed under"
      />
    </div>
  );
}

function Narrative({ testId, text }: { testId: string; text: string }) {
  return (
    <p
      data-testid={testId}
      className="max-w-prose rounded-lg border border-border px-4 py-3 text-sm leading-relaxed whitespace-pre-wrap"
    >
      {text}
    </p>
  );
}

function Fact({ testId, children }: { testId: string; children: ReactNode }) {
  return (
    <div
      data-testid={testId}
      className="mt-3 space-y-2 rounded-lg border border-dashed border-border px-4 py-3 text-sm text-muted-foreground"
    >
      {children}
    </div>
  );
}

function RecordLink({
  caseId,
  decisionId,
  label,
}: {
  caseId: string;
  decisionId: string;
  label: string;
}) {
  return (
    <Link
      to={`/ops/cases/${caseId}/decisions/${decisionId}`}
      data-testid="sar-record-link"
      title={decisionId}
      className="inline-flex items-center gap-2 text-sm text-muted-foreground underline-offset-4 hover:underline focus-visible:underline"
    >
      {label}
      <span className="font-mono text-xs">
        {decisionId.slice(0, ID_PREFIX)}
      </span>
    </Link>
  );
}
