import { useState, type ReactNode } from "react";
import { Link, useParams } from "react-router-dom";
import type {
  Claim,
  HumanDispositionKind,
  MdrActionableOutputResponse,
  MdrDecisionThreadResponse,
  MdrDiffLine,
  MdrDispositionView,
  MdrModelDecisionView,
  MdrRetrievedContextRef,
} from "@/api/generated/types.gen";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { CopyButton } from "@/components/CopyButton";
import { OutcomeTag, RulingTag } from "@/ops/decisionTags";
import { formatCostMicros } from "@/ops/formatCost";
import { formatDecisionTimestamp } from "@/ops/formatDecisionTimestamp";
import {
  useMdrActionableOutput,
  type UseMdrActionableOutputResult,
} from "@/ops/useMdrActionableOutput";
import { useMdrDecisionThread } from "@/ops/useMdrDecisionThread";
import { useExplanation, type ExplanationState } from "@/ops/useExplanation";
import {
  useRulingDraft,
  type UseRulingDraftResult,
} from "@/ops/useRulingDraft";
import { useFocusOnMount } from "@/lib/useFocusOnMount";
import { usePageTitle } from "@/lib/usePageTitle";
import { cn } from "@/lib/utils";
import { ScrollRegion } from "@/components/ScrollRegion";

export function DecisionPage() {
  const { decisionId = "", caseId } = useParams<{
    decisionId: string;
    caseId: string;
  }>();
  const { thread, isPending, isNotFound, isError, refetch } =
    useMdrDecisionThread(decisionId);
  const draft = useRulingDraft(decisionId);
  const gate = useMdrActionableOutput(decisionId);
  usePageTitle(
    isNotFound
      ? "Decision not found"
      : thread
        ? `Decision · ${thread.correlationType} ${thread.correlationId.slice(0, 8)}`
        : "Decision",
  );

  if (isNotFound) return <DecisionNotFound caseId={caseId} />;

  return (
    <section className="mx-auto w-full max-w-4xl px-6 py-6">
      <BackLink caseId={caseId} />
      <DecisionHeading text="Decision" />
      {isPending ? (
        <DecisionSkeleton />
      ) : isError ? (
        <DecisionError retry={refetch} />
      ) : thread ? (
        <DecisionRecord thread={thread} draft={draft} gate={gate} />
      ) : null}
    </section>
  );
}

function BackLink({ caseId }: { caseId?: string }) {
  return (
    <Link
      to={caseId ? `/ops/cases/${caseId}` : "/ops"}
      data-testid="decision-back-link"
      className="text-sm text-muted-foreground hover:underline focus-visible:underline"
    >
      {caseId ? "← Case" : "← Worklist"}
    </Link>
  );
}

function DecisionHeading({ text }: { text: string }) {
  const headingRef = useFocusOnMount<HTMLHeadingElement>();
  return (
    <h1
      ref={headingRef}
      tabIndex={-1}
      className="mt-3 text-2xl font-semibold tracking-tight outline-none"
    >
      {text}
    </h1>
  );
}

function DecisionNotFound({ caseId }: { caseId?: string }) {
  return (
    <section className="mx-auto w-full max-w-4xl px-6 py-6">
      <BackLink caseId={caseId} />
      <DecisionHeading text="Decision not found" />
      <p
        data-testid="decision-not-found"
        className="mt-2 max-w-md text-sm text-muted-foreground"
      >
        No decision with this id exists on the record. The link may be wrong, or
        it may point at a different environment.
      </p>
    </section>
  );
}

function DecisionSkeleton() {
  return (
    <div
      data-testid="decision-skeleton"
      aria-busy="true"
      className="mt-6 space-y-3"
    >
      <span className="sr-only">Loading the decision</span>
      <div className="h-6 w-1/2 rounded-md bg-muted/40 motion-safe:animate-pulse" />
      <div className="h-24 rounded-md bg-muted/40 motion-safe:animate-pulse" />
      <div className="h-24 rounded-md bg-muted/40 motion-safe:animate-pulse" />
    </div>
  );
}

function DecisionError({ retry }: { retry: () => void }) {
  return (
    <div
      data-testid="decision-error"
      role="alert"
      className="mt-6 space-y-3 rounded-2xl border border-dashed border-border px-6 py-8 text-center"
    >
      <p className="text-sm text-muted-foreground">
        Couldn&rsquo;t load the decision.
      </p>
      <Button type="button" data-testid="decision-retry" onClick={retry}>
        Try again
      </Button>
    </div>
  );
}

function DecisionRecord({
  thread,
  draft,
  gate,
}: {
  thread: MdrDecisionThreadResponse;
  draft: UseRulingDraftResult;
  gate: UseMdrActionableOutputResult;
}) {
  return (
    <div className="mt-2">
      <div className="flex flex-wrap items-center gap-2">
        <span
          data-testid="decision-correlation"
          className="font-mono text-xs text-muted-foreground"
        >
          {thread.correlationType} ·{" "}
          <CorrelationId
            correlationType={thread.correlationType}
            correlationId={thread.correlationId}
          />
        </span>
        <OutcomeTag
          outcome={thread.modelDecision.outcome}
          testId="decision-outcome"
        />
        <RulingTag ruling={thread.rulingInForce} testId="decision-ruling" />
        <span
          data-testid="decision-id"
          title={thread.decisionId}
          className="flex items-center gap-1 font-mono text-xs text-muted-foreground"
        >
          decision {thread.decisionId.slice(0, 8)}
          <CopyButton value={thread.decisionId} label="Copy decision id" />
        </span>
      </div>
      <ModelDecisionSection decision={thread.modelDecision} />
      <DispositionThreadSection
        dispositions={thread.dispositions}
        draft={draft}
        rawOutput={thread.modelDecision.rawOutput}
      />
      {thread.correlationType === "PAYMENT" &&
      !thread.modelDecision.advisory ? (
        <ExplanationPanel subjectId={thread.correlationId} />
      ) : null}
      <GatePanel gate={gate} />
    </div>
  );
}

const CASE_CORRELATION_TYPE = "CASE";

function CorrelationId({
  correlationType,
  correlationId,
}: {
  correlationType: string;
  correlationId: string;
}) {
  if (correlationType !== CASE_CORRELATION_TYPE) return <>{correlationId}</>;
  return (
    <Link
      to={`/ops/cases/${correlationId}`}
      data-testid="decision-case-link"
      className="underline decoration-border underline-offset-4 outline-none hover:decoration-foreground focus-visible:decoration-foreground"
    >
      {correlationId}
    </Link>
  );
}

function ExplanationPanel({ subjectId }: { subjectId: string }) {
  const { state, explain } = useExplanation(subjectId);

  return (
    <section
      aria-labelledby="explanation-heading"
      data-testid="explanation-panel"
      className="mt-8"
    >
      <h2 id="explanation-heading" className="text-lg font-semibold">
        Explanation
      </h2>
      <p className="mt-1 text-sm text-muted-foreground">
        A cited account of what happened to this payment, assembled from records
        the bank holds. It aids an investigation and is never itself a finding —
        nothing in the bank acts on it.
      </p>
      <div className="mt-3">
        <Button
          type="button"
          variant="outline"
          data-testid="explanation-ask"
          disabled={state.kind === "asking"}
          onClick={explain}
        >
          {state.kind === "asking"
            ? "Explaining…"
            : state.kind === "idle"
              ? "Explain this payment"
              : "Explain again"}
        </Button>
      </div>
      <ExplanationOutcome state={state} />
    </section>
  );
}

function ExplanationOutcome({ state }: { state: ExplanationState }) {
  if (state.kind === "grounded") {
    return (
      <div className="mt-3 space-y-3">
        <ol data-testid="explanation-claims" className="space-y-2">
          {state.claims.map((claim, index) => (
            <ClaimRow key={index} claim={claim} />
          ))}
        </ol>
        <ExplanationRecordLink decisionId={state.decisionId} />
      </div>
    );
  }
  if (state.kind === "refused") {
    return (
      <div
        data-testid="explanation-refused"
        className="mt-3 space-y-2 rounded-lg border border-dashed border-border px-4 py-3 text-sm text-muted-foreground"
      >
        <p>
          An answer was produced and withheld: one of its claims cited a record
          outside the assembled timeline, so grounding failed and the whole
          answer was refused rather than shown.
        </p>
        <ExplanationRecordLink
          decisionId={state.decisionId}
          label="Read the refused answer on the record"
        />
      </div>
    );
  }
  if (state.kind === "no-answer") {
    return (
      <div
        data-testid="explanation-no-answer"
        className="mt-3 space-y-2 rounded-lg border border-dashed border-border px-4 py-3 text-sm text-muted-foreground"
      >
        <p>{NO_ANSWER_FACT[state.outcome]}</p>
        <ExplanationRecordLink decisionId={state.decisionId} />
      </div>
    );
  }
  if (state.kind === "ask-failed") {
    return (
      <p
        data-testid="explanation-ask-failed"
        role="alert"
        className="mt-3 rounded-lg border border-border px-4 py-3 text-sm text-destructive"
      >
        Couldn&rsquo;t ask for an explanation — the ask itself failed, which is
        not a refusal. Asking again re-sends this same invocation, never a
        second one.
      </p>
    );
  }
  return null;
}

const NO_ANSWER_FACT: Record<
  Extract<ExplanationState, { kind: "no-answer" }>["outcome"],
  string
> = {
  TIMED_OUT:
    "No answer was produced — the model exceeded its budget and was abandoned. The invocation is on the record all the same.",
  ERRORED:
    "No answer was produced — the invocation failed. It is on the record all the same.",
};

function ClaimRow({ claim }: { claim: Claim }) {
  return (
    <li
      data-testid="explanation-claim"
      className="rounded-lg border border-border px-4 py-3 text-sm"
    >
      <p>{claim.text}</p>
      <ul className="mt-2 flex flex-wrap gap-x-4 gap-y-1">
        {claim.sources.map((source, index) => (
          <li
            key={index}
            data-testid="explanation-claim-source"
            className="font-mono text-xs text-muted-foreground"
          >
            {source.type} · {source.id}
          </li>
        ))}
      </ul>
    </li>
  );
}

function ExplanationRecordLink({
  decisionId,
  label = "Read this explanation's own record",
}: {
  decisionId: string;
  label?: string;
}) {
  return (
    <Link
      to={`/ops/decisions/${decisionId}`}
      data-testid="explanation-record-link"
      className="inline-block text-sm text-muted-foreground underline-offset-4 hover:underline focus-visible:underline"
    >
      {label}
    </Link>
  );
}

function GatePanel({ gate }: { gate: UseMdrActionableOutputResult }) {
  return (
    <section
      aria-labelledby="gate-heading"
      data-testid="gate-panel"
      className="mt-8"
    >
      <h2 id="gate-heading" className="text-lg font-semibold">
        Gate
      </h2>
      <p className="mt-1 text-sm text-muted-foreground">
        What may a consumer act on right now? The gate answers only when asked.
      </p>
      <div className="mt-3">
        <Button
          type="button"
          variant="outline"
          data-testid="gate-ask"
          disabled={gate.isAsking}
          onClick={gate.ask}
        >
          {gate.isAsking
            ? "Asking…"
            : gate.isIdle
              ? "Ask the gate"
              : "Ask again"}
        </Button>
      </div>
      {gate.inForce ? <GateInForce answer={gate.inForce} /> : null}
      {gate.isNoRuling ? (
        <p
          data-testid="gate-no-ruling"
          className="mt-3 rounded-lg border border-dashed border-border px-4 py-3 text-sm text-muted-foreground"
        >
          No ruling is in force. The gate is refusing to serve this
          decision&rsquo;s output — nothing downstream may act on it until a
          reviewer rules.
        </p>
      ) : null}
      {gate.isError ? (
        <p
          data-testid="gate-error"
          role="alert"
          className="mt-3 rounded-lg border border-border px-4 py-3 text-sm text-destructive"
        >
          Couldn&rsquo;t ask the gate — the ask itself failed, which is not a
          refusal. Ask again.
        </p>
      ) : null}
    </section>
  );
}

function GateInForce({ answer }: { answer: MdrActionableOutputResponse }) {
  return (
    <div
      data-testid="gate-in-force"
      className="mt-3 rounded-lg border border-border px-4 py-3"
    >
      <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
        <RulingTag ruling={answer.ruling} testId="gate-ruling" />
        <span
          data-testid="gate-entry"
          className="font-mono text-xs text-muted-foreground"
        >
          authorised by entry {answer.rulingEntryId}
        </span>
      </div>
      <p className="mt-2 text-sm text-muted-foreground">
        A consumer may act on this output, served under the Ruling in force:
      </p>
      <ScrollRegion
        label="Actionable output"
        className="mt-1.5 rounded-lg border border-border bg-muted/30"
      >
        <pre
          data-testid="gate-output"
          className="px-4 py-3 font-mono text-xs leading-relaxed whitespace-pre-wrap"
        >
          {answer.actionableOutput}
        </pre>
      </ScrollRegion>
    </div>
  );
}

function DispositionThreadSection({
  dispositions,
  draft,
  rawOutput,
}: {
  dispositions: MdrDispositionView[];
  draft: UseRulingDraftResult;
  rawOutput: string | undefined;
}) {
  return (
    <section aria-labelledby="dispositions-heading" className="mt-8">
      <h2 id="dispositions-heading" className="text-lg font-semibold">
        Rulings
      </h2>
      {dispositions.length === 0 ? (
        <p
          data-testid="dispositions-empty"
          className="mt-3 rounded-lg border border-dashed border-border px-4 py-3 text-sm text-muted-foreground"
        >
          No rulings have been appended yet.
        </p>
      ) : (
        <ol className="mt-3 space-y-3">
          {dispositions.map((disposition) => (
            <DispositionEntry
              key={disposition.entryId}
              disposition={disposition}
            />
          ))}
        </ol>
      )}
      <RulingDraftFoot draft={draft} rawOutput={rawOutput} />
    </section>
  );
}

function RulingDraftFoot({
  draft,
  rawOutput,
}: {
  draft: UseRulingDraftResult;
  rawOutput: string | undefined;
}) {
  if (!draft.isOpen) {
    return (
      <div className="mt-3">
        <Button
          type="button"
          variant="outline"
          data-testid="ruling-draft-open"
          onClick={draft.open}
        >
          Append a ruling
        </Button>
      </div>
    );
  }
  return <RulingDraftForm draft={draft} rawOutput={rawOutput} />;
}

const DRAFT_KINDS: {
  kind: HumanDispositionKind;
  label: string;
  testId: string;
}[] = [
  { kind: "ACCEPT", label: "Accept", testId: "ruling-draft-kind-accept" },
  { kind: "EDIT", label: "Edit", testId: "ruling-draft-kind-edit" },
  { kind: "REJECT", label: "Reject", testId: "ruling-draft-kind-reject" },
];

function RulingDraftForm({
  draft,
  rawOutput,
}: {
  draft: UseRulingDraftResult;
  rawOutput: string | undefined;
}) {
  const [kind, setKind] = useState<HumanDispositionKind | null>(null);
  const [replacement, setReplacement] = useState(rawOutput ?? "");

  const append = () => {
    if (kind === "ACCEPT") draft.appendAccept();
    if (kind === "EDIT") draft.appendEdit(replacement);
    if (kind === "REJECT") draft.appendReject();
  };

  return (
    <div
      data-testid="ruling-draft"
      className="mt-3 rounded-lg border border-border px-4 py-3"
    >
      <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
        <h3 className="text-sm font-medium">Append a ruling</h3>
        <span
          data-testid="ruling-draft-entry-id"
          className="font-mono text-xs text-muted-foreground"
        >
          entry {draft.entryId}
        </span>
      </div>
      <div
        role="group"
        aria-label="Ruling"
        className="mt-3 flex flex-wrap gap-2"
      >
        {DRAFT_KINDS.map((choice) => {
          const selected = choice.kind === kind;
          return (
            <Button
              key={choice.kind}
              type="button"
              size="sm"
              variant={selected ? "default" : "outline"}
              aria-pressed={selected}
              data-testid={choice.testId}
              onClick={() => setKind(choice.kind)}
            >
              {choice.label}
            </Button>
          );
        })}
      </div>
      {kind === "EDIT" ? (
        <div className="mt-3">
          <Label htmlFor="ruling-draft-replacement">Replacement output</Label>
          <Textarea
            id="ruling-draft-replacement"
            data-testid="ruling-draft-replacement"
            className="mt-1.5 font-mono text-xs leading-relaxed"
            value={replacement}
            onChange={(event) => setReplacement(event.target.value)}
          />
        </div>
      ) : null}
      {draft.isError ? (
        <p
          data-testid="ruling-draft-failure"
          role="alert"
          className="mt-3 text-sm text-destructive"
        >
          The ruling didn&rsquo;t reach the record. Appending again re-sends
          this same entry — never a duplicate.
        </p>
      ) : null}
      <div className="mt-4 flex flex-wrap gap-2">
        <Button
          type="button"
          data-testid="ruling-draft-append"
          disabled={kind === null || draft.isPending}
          onClick={append}
        >
          {draft.isPending ? "Appending…" : "Append to the record"}
        </Button>
        <Button
          type="button"
          variant="ghost"
          data-testid="ruling-draft-discard"
          onClick={draft.discard}
        >
          Discard draft
        </Button>
      </div>
    </div>
  );
}

function DispositionEntry({
  disposition,
}: {
  disposition: MdrDispositionView;
}) {
  return (
    <li
      data-testid="disposition-entry"
      className="rounded-lg border border-border px-4 py-3"
    >
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm">
        <RulingTag ruling={disposition.disposition} testId="disposition-kind" />
        <span
          data-testid="disposition-human"
          title={disposition.humanId}
          className="flex items-center gap-1 font-medium"
        >
          Reviewer{" "}
          <span className="font-mono text-xs">
            {disposition.humanId.slice(0, 8)}
          </span>
          <CopyButton value={disposition.humanId} label="Copy reviewer id" />
        </span>
        <span
          data-testid="disposition-ts"
          className="text-muted-foreground tabular-nums"
        >
          {formatDecisionTimestamp(disposition.ts)}
        </span>
        <span className="font-mono text-xs text-muted-foreground">
          seq {disposition.seq} · {disposition.entryId}
        </span>
      </div>
      {disposition.disposition === "EDIT" ? (
        <EditRulingRecord disposition={disposition} />
      ) : null}
    </li>
  );
}

function EditRulingRecord({
  disposition,
}: {
  disposition: MdrDispositionView;
}) {
  return (
    <div className="mt-3 space-y-3">
      <div>
        <h4 className="text-sm font-medium text-muted-foreground">
          Replacement output
        </h4>
        <ScrollRegion
          label="Replacement output"
          className="mt-1.5 rounded-lg border border-border bg-muted/30"
        >
          <pre
            data-testid="disposition-replacement"
            className="px-4 py-3 font-mono text-xs leading-relaxed whitespace-pre-wrap"
          >
            {disposition.editedOutput}
          </pre>
        </ScrollRegion>
      </div>
      {disposition.diff && disposition.diff.length > 0 ? (
        <div>
          <h4 className="text-sm font-medium text-muted-foreground">
            Difference from the raw output
          </h4>
          <ScrollRegion
            label="Difference from the raw output"
            className="mt-1.5 rounded-lg border border-border"
          >
            <ol className="font-mono text-xs leading-relaxed">
              {disposition.diff.map((line, index) => (
                <DiffLineRow key={index} line={line} />
              ))}
            </ol>
          </ScrollRegion>
        </div>
      ) : null}
    </div>
  );
}

const DIFF_LINE_TONE: Record<MdrDiffLine["op"], string> = {
  UNCHANGED: "text-muted-foreground",
  REMOVED: "bg-destructive/10 text-destructive",
  ADDED: "bg-positive/10 text-positive",
};

const DIFF_LINE_MARKER: Record<MdrDiffLine["op"], string> = {
  UNCHANGED: " ",
  REMOVED: "−",
  ADDED: "+",
};

function DiffLineRow({ line }: { line: MdrDiffLine }) {
  return (
    <li
      data-testid="diff-line"
      data-op={line.op}
      className={cn(
        "flex gap-2 px-4 py-0.5 whitespace-pre-wrap",
        DIFF_LINE_TONE[line.op],
      )}
    >
      <span aria-hidden="true" className="w-3 shrink-0 select-none">
        {DIFF_LINE_MARKER[line.op]}
      </span>
      {line.text}
    </li>
  );
}

function ModelDecisionSection({
  decision,
}: {
  decision: MdrModelDecisionView;
}) {
  return (
    <section aria-labelledby="model-decision-heading" className="mt-6">
      <h2 id="model-decision-heading" className="text-lg font-semibold">
        Model decision
      </h2>
      <VerdictSection verdict={decision.verdict} />
      <dl className="mt-4 grid grid-cols-1 gap-x-6 gap-y-3 text-sm sm:grid-cols-2 lg:grid-cols-3">
        <MetaEntry term="Moment" testId="decision-moment">
          {formatDecisionTimestamp(decision.ts)}
        </MetaEntry>
        <MetaEntry term="Model" testId="decision-model">
          {decision.modelId} · {decision.modelVersion}
        </MetaEntry>
        <MetaEntry term="Prompt template" testId="decision-template-pin">
          {decision.promptTemplateId} · v{decision.promptTemplateVersion}
          <span className="flex min-w-0 items-center gap-1">
            <span
              title={decision.promptTemplateHash}
              className="block truncate font-mono text-xs text-muted-foreground"
            >
              {decision.promptTemplateHash}
            </span>
            <CopyButton
              value={decision.promptTemplateHash}
              label="Copy prompt template hash"
            />
          </span>
        </MetaEntry>
        <MetaEntry term="Latency" testId="decision-latency">
          {new Intl.NumberFormat("en-GB").format(decision.latencyMs)} ms
        </MetaEntry>
        <MetaEntry term="Cost" testId="decision-cost">
          {formatCostMicros(decision.costMicros, decision.costCurrency)}
        </MetaEntry>
        <MetaEntry term="Chain entry" testId="decision-entry">
          <span className="font-mono text-xs">
            seq {decision.seq} · {decision.entryId}
          </span>
        </MetaEntry>
      </dl>
      <RawOutputSection decision={decision} />
      <RetrievedContextSection refs={decision.retrievedContext} />
      <RecordBlock heading="Rendered prompt" testId="decision-rendered-prompt">
        {decision.renderedPrompt}
      </RecordBlock>
    </section>
  );
}

function RetrievedContextSection({
  refs,
}: {
  refs: MdrModelDecisionView["retrievedContext"];
}) {
  return (
    <section aria-label="Retrieved context" className="mt-4">
      <h3 className="text-sm font-medium text-muted-foreground">
        Retrieved context
      </h3>
      {refs && refs.length > 0 ? (
        <ul className="mt-1.5 divide-y divide-border rounded-lg border border-border">
          {refs.map((ref, index) => (
            <ContextRefRow key={index} item={ref} />
          ))}
        </ul>
      ) : (
        <p
          data-testid="decision-context-empty"
          className="mt-1.5 rounded-lg border border-dashed border-border px-4 py-3 text-sm text-muted-foreground"
        >
          None recorded.
        </p>
      )}
    </section>
  );
}

function ContextRefRow({ item }: { item: MdrRetrievedContextRef }) {
  const type = refField(item.type);
  const id = refField(item.id);
  const contentHash = refField(item.contentHash);
  const recognised = type !== null || id !== null || contentHash !== null;

  return (
    <li
      data-testid="decision-context-ref"
      className="flex flex-wrap items-baseline gap-x-4 gap-y-1 px-4 py-2.5 text-sm"
    >
      {recognised ? (
        <>
          {type !== null ? <span className="font-medium">{type}</span> : null}
          {id !== null ? <span className="font-mono text-xs">{id}</span> : null}
          {contentHash !== null ? (
            <span className="flex min-w-0 items-center gap-1">
              <span
                title={contentHash}
                className="max-w-56 truncate font-mono text-xs text-muted-foreground"
              >
                {contentHash}
              </span>
              <CopyButton
                value={contentHash}
                label={`Copy ${type ?? "ref"} content hash`}
              />
            </span>
          ) : null}
        </>
      ) : (
        <span className="font-mono text-xs break-all">
          {JSON.stringify(item)}
        </span>
      )}
    </li>
  );
}

function refField(value: unknown): string | null {
  if (value === undefined || value === null) return null;
  if (typeof value === "string") return value;
  return JSON.stringify(value) ?? null;
}

const OUTPUT_ABSENT_FACT: Record<
  Exclude<MdrModelDecisionView["outcome"], "COMPLETED">,
  string
> = {
  TIMED_OUT: "No output was recorded — the decision timed out.",
  ERRORED: "No output was recorded — the decision errored.",
};

function RawOutputSection({ decision }: { decision: MdrModelDecisionView }) {
  if (decision.outcome === "COMPLETED") {
    return (
      <RecordBlock heading="Raw output" testId="decision-raw-output">
        {decision.rawOutput}
      </RecordBlock>
    );
  }
  return (
    <section aria-label="Raw output" className="mt-4">
      <h3 className="text-sm font-medium text-muted-foreground">Raw output</h3>
      <p
        data-testid="decision-output-absent"
        className="mt-1.5 rounded-lg border border-dashed border-border px-4 py-3 text-sm text-muted-foreground"
      >
        {OUTPUT_ABSENT_FACT[decision.outcome]}
      </p>
      {decision.errorDetail ? (
        <ScrollRegion
          label="Error detail"
          className="mt-2 max-w-3xl rounded-lg border border-border bg-muted/30"
        >
          <pre
            data-testid="decision-error-detail"
            className="px-4 py-3 font-mono text-xs leading-relaxed whitespace-pre-wrap"
          >
            {decision.errorDetail}
          </pre>
        </ScrollRegion>
      ) : null}
    </section>
  );
}

function VerdictSection({
  verdict,
}: {
  verdict: MdrModelDecisionView["verdict"];
}) {
  if (verdict === undefined) {
    return (
      <section aria-label="Verdict" className="mt-4">
        <h3 className="text-sm font-medium text-muted-foreground">Verdict</h3>
        <p
          data-testid="decision-verdict"
          className="mt-1.5 rounded-lg border border-dashed border-border px-4 py-3 text-sm text-muted-foreground"
        >
          None recorded.
        </p>
      </section>
    );
  }
  return (
    <section aria-label="Verdict" className="mt-4">
      <h3 className="text-sm font-medium text-muted-foreground">Verdict</h3>
      <dl
        data-testid="decision-verdict"
        className="mt-1.5 grid grid-cols-1 gap-x-6 gap-y-2 rounded-lg border border-border px-4 py-3 sm:grid-cols-2 lg:grid-cols-3"
      >
        {Object.entries(verdict).map(([key, value]) => (
          <div key={key} className="min-w-0">
            <dt className="text-xs font-medium text-muted-foreground">{key}</dt>
            <dd
              className={cn(
                "mt-0.5 text-sm break-words",
                (key === "decision" || key === "rule") && "font-semibold",
              )}
            >
              {typeof value === "string" ? value : JSON.stringify(value)}
            </dd>
          </div>
        ))}
      </dl>
    </section>
  );
}

function MetaEntry({
  term,
  testId,
  children,
}: {
  term: string;
  testId: string;
  children: ReactNode;
}) {
  return (
    <div data-testid={testId} className="min-w-0">
      <dt className="text-xs font-medium text-muted-foreground">{term}</dt>
      <dd className="mt-0.5 tabular-nums">{children}</dd>
    </div>
  );
}

function RecordBlock({
  heading,
  testId,
  children,
}: {
  heading: string;
  testId: string;
  children: ReactNode;
}) {
  return (
    <section aria-label={heading} className="mt-4">
      <h3 className="text-sm font-medium text-muted-foreground">{heading}</h3>
      <ScrollRegion
        label={heading}
        className="mt-1.5 max-h-80 max-w-3xl rounded-lg border border-border bg-muted/30"
      >
        <pre
          data-testid={testId}
          className="px-4 py-3 font-mono text-xs leading-relaxed whitespace-pre-wrap"
        >
          {children}
        </pre>
      </ScrollRegion>
    </section>
  );
}
