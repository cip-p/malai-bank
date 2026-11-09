import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import type {
  MdrDecisionThreadResponse,
  MdrDispositionView,
  MdrModelDecisionView,
  MdrRetrievedContextRef,
} from "@/api/generated/types.gen";
import type { UseMdrActionableOutputResult } from "@/ops/useMdrActionableOutput";
import type { UseMdrDecisionThreadResult } from "@/ops/useMdrDecisionThread";
import type { UseRulingDraftResult } from "@/ops/useRulingDraft";
import type {
  ExplanationState,
  UseExplanationResult,
} from "@/ops/useExplanation";

vi.mock("@/ops/useMdrDecisionThread", () => ({
  useMdrDecisionThread: vi.fn(),
}));

vi.mock("@/ops/useRulingDraft", () => ({
  useRulingDraft: vi.fn(),
}));

vi.mock("@/ops/useMdrActionableOutput", () => ({
  useMdrActionableOutput: vi.fn(),
}));

vi.mock("@/ops/useExplanation", () => ({
  useExplanation: vi.fn(),
}));

const hookModule = await import("@/ops/useMdrDecisionThread");
const useMdrDecisionThreadMock =
  hookModule.useMdrDecisionThread as unknown as ReturnType<
    typeof vi.fn<(decisionId: string) => UseMdrDecisionThreadResult>
  >;

const draftModule = await import("@/ops/useRulingDraft");
const useRulingDraftMock = draftModule.useRulingDraft as unknown as ReturnType<
  typeof vi.fn<(decisionId: string) => UseRulingDraftResult>
>;

const gateModule = await import("@/ops/useMdrActionableOutput");
const useMdrActionableOutputMock =
  gateModule.useMdrActionableOutput as unknown as ReturnType<
    typeof vi.fn<(decisionId: string) => UseMdrActionableOutputResult>
  >;

const explanationModule = await import("@/ops/useExplanation");
const useExplanationMock =
  explanationModule.useExplanation as unknown as ReturnType<
    typeof vi.fn<(subjectId: string) => UseExplanationResult>
  >;

import { DecisionPage } from "./DecisionPage";

function makeModelDecision(
  overrides: Partial<MdrModelDecisionView>,
): MdrModelDecisionView {
  return {
    entryId: "22222222-2222-2222-2222-222222222222",
    seq: 12,
    ts: "2026-07-18T09:30:00",
    outcome: "COMPLETED",
    modelId: "claude-sonnet-5",
    modelVersion: "20260301",
    promptTemplateId: "payment-triage",
    promptTemplateVersion: "3",
    promptTemplateHash: "ab12cd34ef56",
    renderedPrompt: "Assess this payment for anomalies.",
    rawOutput: "No anomalies found.",
    latencyMs: 1204,
    costMicros: 1250,
    costCurrency: "USD",
    advisory: false,
    ...overrides,
  };
}

function makeDisposition(
  overrides: Partial<MdrDispositionView>,
): MdrDispositionView {
  return {
    entryId: "55555555-5555-5555-5555-555555555555",
    seq: 13,
    ts: "2026-07-18T10:15:00",
    disposition: "ACCEPT",
    humanId: "0e4f2a6b-8c1d-4e7f-9a3b-5d8c2f6e0a4b",
    parentEntryId: "22222222-2222-2222-2222-222222222222",
    ...overrides,
  };
}

function makeThread(
  overrides: Partial<MdrDecisionThreadResponse>,
): MdrDecisionThreadResponse {
  return {
    decisionId: "11111111-1111-1111-1111-111111111111",
    correlationType: "PAYMENT",
    correlationId: "33333333-3333-3333-3333-333333333333",
    modelDecision: makeModelDecision({}),
    dispositions: [],
    ...overrides,
  };
}

function served(thread: MdrDecisionThreadResponse): UseMdrDecisionThreadResult {
  return {
    thread,
    isPending: false,
    isNotFound: false,
    isError: false,
    error: null,
    refetch: vi.fn(),
  };
}

const MINTED_ENTRY_ID = "77777777-7777-7777-7777-777777777777";

function makeDraft(
  overrides: Partial<UseRulingDraftResult>,
): UseRulingDraftResult {
  return {
    isOpen: false,
    entryId: null,
    isError: false,
    isPending: false,
    open: vi.fn(),
    discard: vi.fn(),
    appendAccept: vi.fn(),
    appendEdit: vi.fn(),
    appendReject: vi.fn(),
    ...overrides,
  };
}

function openDraft(
  overrides: Partial<UseRulingDraftResult> = {},
): UseRulingDraftResult {
  return makeDraft({ isOpen: true, entryId: MINTED_ENTRY_ID, ...overrides });
}

function makeGate(
  overrides: Partial<UseMdrActionableOutputResult>,
): UseMdrActionableOutputResult {
  return {
    isIdle: true,
    isAsking: false,
    inForce: null,
    isNoRuling: false,
    isError: false,
    ask: vi.fn(),
    ...overrides,
  };
}

function makeExplanation(state: ExplanationState): UseExplanationResult {
  return { state, explain: vi.fn() };
}

function renderPage(
  decisionId = "11111111-1111-1111-1111-111111111111",
): ReturnType<typeof render> {
  return renderAt(`/ops/decisions/${decisionId}`);
}

function renderPageWithinCase(
  caseId: string,
  decisionId = "11111111-1111-1111-1111-111111111111",
): ReturnType<typeof render> {
  return renderAt(`/ops/cases/${caseId}/decisions/${decisionId}`);
}

function renderAt(path: string): ReturnType<typeof render> {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/ops/decisions/:decisionId" element={<DecisionPage />} />
        <Route
          path="/ops/cases/:caseId/decisions/:decisionId"
          element={<DecisionPage />}
        />
      </Routes>
    </MemoryRouter>,
  );
}

describe("DecisionPage", () => {
  beforeEach(() => {
    useMdrDecisionThreadMock.mockReset();
    useMdrDecisionThreadMock.mockReturnValue(served(makeThread({})));
    useRulingDraftMock.mockReset();
    useRulingDraftMock.mockReturnValue(makeDraft({}));
    useMdrActionableOutputMock.mockReset();
    useMdrActionableOutputMock.mockReturnValue(makeGate({}));
    useExplanationMock.mockReset();
    useExplanationMock.mockReturnValue(makeExplanation({ kind: "idle" }));
  });

  it("walks back to the worklist when the decision stands alone", () => {
    renderPage();

    const back = screen.getByTestId("decision-back-link");
    expect(back).toHaveTextContent("← Worklist");
    expect(back).toHaveAttribute("href", "/ops");
  });

  it("walks back to the case when the decision was opened from one", () => {
    renderPageWithinCase("1a2b3c4d-1111-1111-1111-111111111111");

    const back = screen.getByTestId("decision-back-link");
    expect(back).toHaveTextContent("← Case");
    expect(back).toHaveAttribute(
      "href",
      "/ops/cases/1a2b3c4d-1111-1111-1111-111111111111",
    );
  });

  it("keeps the case context on a decision that does not exist", () => {
    useMdrDecisionThreadMock.mockReturnValue({
      thread: null,
      isPending: false,
      isNotFound: true,
      isError: false,
      error: null,
      refetch: vi.fn(),
    });
    renderPageWithinCase("1a2b3c4d-1111-1111-1111-111111111111");

    expect(screen.getByTestId("decision-back-link")).toHaveAttribute(
      "href",
      "/ops/cases/1a2b3c4d-1111-1111-1111-111111111111",
    );
  });

  it("offers to ask the gate for this decision, fetching nothing until asked", async () => {
    const user = userEvent.setup();
    const gate = makeGate({});
    useMdrActionableOutputMock.mockReturnValue(gate);
    renderPage("aaaa1111-0000-0000-0000-000000000000");

    expect(useMdrActionableOutputMock).toHaveBeenLastCalledWith(
      "aaaa1111-0000-0000-0000-000000000000",
    );
    const panel = screen.getByTestId("gate-panel");
    expect(panel).toHaveTextContent("What may a consumer act on right now?");
    expect(screen.queryByTestId("gate-in-force")).not.toBeInTheDocument();
    expect(screen.queryByTestId("gate-no-ruling")).not.toBeInTheDocument();
    expect(screen.queryByTestId("gate-error")).not.toBeInTheDocument();

    const askButton = within(panel).getByTestId("gate-ask");
    expect(askButton).toHaveTextContent("Ask the gate");
    await user.click(askButton);
    expect(gate.ask).toHaveBeenCalled();
  });

  it("shows the answer in force: the authorising ruling, its Entry, and the output served verbatim", () => {
    useMdrActionableOutputMock.mockReturnValue(
      makeGate({
        isIdle: false,
        inForce: {
          decisionId: "11111111-1111-1111-1111-111111111111",
          ruling: "EDIT",
          rulingEntryId: "55555555-5555-5555-5555-555555555555",
          actionableOutput: "Two anomalies found.\nEscalate to fraud team.",
        },
      }),
    );
    renderPage();

    const inForce = screen.getByTestId("gate-in-force");
    expect(within(inForce).getByTestId("gate-ruling")).toHaveTextContent(
      "Edit",
    );
    expect(within(inForce).getByTestId("gate-entry")).toHaveTextContent(
      "55555555-5555-5555-5555-555555555555",
    );
    expect(within(inForce).getByTestId("gate-output")).toHaveTextContent(
      "Two anomalies found. Escalate to fraud team.",
    );
    expect(screen.getByTestId("gate-ask")).toHaveTextContent("Ask again");
    expect(screen.queryByTestId("gate-no-ruling")).not.toBeInTheDocument();
    expect(screen.queryByTestId("gate-error")).not.toBeInTheDocument();
  });

  it("renders the gate's refusal as a calm no-ruling-in-force fact, not an error", () => {
    useMdrActionableOutputMock.mockReturnValue(
      makeGate({ isIdle: false, isNoRuling: true }),
    );
    renderPage();

    const refusal = screen.getByTestId("gate-no-ruling");
    expect(refusal).toHaveTextContent("No ruling is in force");
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.queryByTestId("gate-in-force")).not.toBeInTheDocument();
    expect(screen.queryByTestId("gate-error")).not.toBeInTheDocument();
    expect(screen.getByTestId("gate-ask")).toHaveTextContent("Ask again");
  });

  it("renders any other gate failure as an ordinary error, apart from the refusal", () => {
    useMdrActionableOutputMock.mockReturnValue(
      makeGate({ isIdle: false, isError: true }),
    );
    renderPage();

    const failure = screen.getByTestId("gate-error");
    expect(failure).toHaveTextContent("Couldn’t ask the gate");
    expect(screen.getByRole("alert")).toBe(failure);
    expect(screen.queryByTestId("gate-no-ruling")).not.toBeInTheDocument();
    expect(screen.queryByTestId("gate-in-force")).not.toBeInTheDocument();
  });

  it("holds the ask while the gate is answering", () => {
    useMdrActionableOutputMock.mockReturnValue(
      makeGate({ isIdle: false, isAsking: true }),
    );
    renderPage();

    const askButton = screen.getByTestId("gate-ask");
    expect(askButton).toBeDisabled();
    expect(askButton).toHaveTextContent("Asking…");
    expect(screen.queryByTestId("gate-in-force")).not.toBeInTheDocument();
    expect(screen.queryByTestId("gate-no-ruling")).not.toBeInTheDocument();
  });

  it("offers to append a ruling at the foot of the thread, opening the draft for this decision", async () => {
    const user = userEvent.setup();
    const draft = makeDraft({});
    useRulingDraftMock.mockReturnValue(draft);
    renderPage("aaaa1111-0000-0000-0000-000000000000");

    expect(useRulingDraftMock).toHaveBeenLastCalledWith(
      "aaaa1111-0000-0000-0000-000000000000",
    );
    const openButton = screen.getByTestId("ruling-draft-open");
    expect(openButton).toHaveTextContent("Append a ruling");
    await user.click(openButton);
    expect(draft.open).toHaveBeenCalled();
  });

  it("renders the open draft with the three rulings unchosen, the minted Entry id, and a gated append", async () => {
    const user = userEvent.setup();
    const draft = openDraft();
    useRulingDraftMock.mockReturnValue(draft);
    renderPage();

    const draftRegion = screen.getByTestId("ruling-draft");
    expect(screen.queryByTestId("ruling-draft-open")).not.toBeInTheDocument();
    for (const kind of ["accept", "edit", "reject"]) {
      expect(
        within(draftRegion).getByTestId(`ruling-draft-kind-${kind}`),
      ).toHaveAttribute("aria-pressed", "false");
    }
    expect(
      within(draftRegion).getByTestId("ruling-draft-entry-id"),
    ).toHaveTextContent(MINTED_ENTRY_ID);

    const submit = within(draftRegion).getByTestId("ruling-draft-append");
    expect(submit).toHaveTextContent("Append to the record");
    expect(submit).toBeDisabled();
    await user.click(within(draftRegion).getByTestId("ruling-draft-discard"));
    expect(draft.discard).toHaveBeenCalled();
    expect(draft.appendAccept).not.toHaveBeenCalled();
    expect(draft.appendEdit).not.toHaveBeenCalled();
    expect(draft.appendReject).not.toHaveBeenCalled();
  });

  it("appends an accept ruling carrying the ruling alone, with no editor shown", async () => {
    const user = userEvent.setup();
    const draft = openDraft();
    useRulingDraftMock.mockReturnValue(draft);
    renderPage();

    await user.click(screen.getByTestId("ruling-draft-kind-accept"));
    expect(screen.getByTestId("ruling-draft-kind-accept")).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
    await user.click(screen.getByTestId("ruling-draft-append"));

    expect(draft.appendAccept).toHaveBeenCalled();
    expect(draft.appendEdit).not.toHaveBeenCalled();
    expect(draft.appendReject).not.toHaveBeenCalled();
  });

  it("appends a reject ruling carrying the ruling alone", async () => {
    const user = userEvent.setup();
    const draft = openDraft();
    useRulingDraftMock.mockReturnValue(draft);
    renderPage();

    await user.click(screen.getByTestId("ruling-draft-kind-reject"));
    await user.click(screen.getByTestId("ruling-draft-append"));

    expect(draft.appendReject).toHaveBeenCalled();
    expect(draft.appendAccept).not.toHaveBeenCalled();
    expect(draft.appendEdit).not.toHaveBeenCalled();
  });

  it("exposes the full-replacement editor prefilled with the raw output when edit is chosen", async () => {
    const user = userEvent.setup();
    const draft = openDraft();
    useRulingDraftMock.mockReturnValue(draft);
    useMdrDecisionThreadMock.mockReturnValue(
      served(
        makeThread({
          modelDecision: makeModelDecision({
            rawOutput: "No anomalies found.",
          }),
        }),
      ),
    );
    renderPage();

    await user.click(screen.getByTestId("ruling-draft-kind-edit"));

    const editor = screen.getByRole("textbox", {
      name: "Replacement output",
    });
    expect(editor).toHaveValue("No anomalies found.");

    await user.clear(editor);
    await user.type(editor, "Two anomalies found.");
    await user.click(screen.getByTestId("ruling-draft-append"));

    expect(draft.appendEdit).toHaveBeenCalledWith("Two anomalies found.");
    expect(draft.appendAccept).not.toHaveBeenCalled();
    expect(draft.appendReject).not.toHaveBeenCalled();
  });

  it("prefills an empty editor when the decision recorded no output", async () => {
    const user = userEvent.setup();
    useRulingDraftMock.mockReturnValue(openDraft());
    useMdrDecisionThreadMock.mockReturnValue(
      served(
        makeThread({
          modelDecision: makeModelDecision({
            outcome: "TIMED_OUT",
            rawOutput: undefined,
            verdict: undefined,
          }),
        }),
      ),
    );
    renderPage();

    await user.click(screen.getByTestId("ruling-draft-kind-edit"));
    expect(
      screen.getByRole("textbox", { name: "Replacement output" }),
    ).toHaveValue("");
  });

  it("renders a failed append inline in the draft, naming the retry-safe Entry", async () => {
    const user = userEvent.setup();
    useRulingDraftMock.mockReturnValue(openDraft({ isError: true }));
    renderPage();

    const failure = screen.getByTestId("ruling-draft-failure");
    expect(screen.getByTestId("ruling-draft")).toContainElement(failure);
    expect(failure).toHaveTextContent(
      "The ruling didn’t reach the record. Appending again re-sends this same entry — never a duplicate.",
    );
    await user.click(screen.getByTestId("ruling-draft-kind-accept"));
    expect(screen.getByTestId("ruling-draft-append")).toBeEnabled();
  });

  it("holds the append while one is in flight", async () => {
    const user = userEvent.setup();
    const draft = openDraft({ isPending: true });
    useRulingDraftMock.mockReturnValue(draft);
    renderPage();

    await user.click(screen.getByTestId("ruling-draft-kind-accept"));
    const submit = screen.getByTestId("ruling-draft-append");
    expect(submit).toBeDisabled();
    expect(submit).toHaveTextContent("Appending…");
  });

  it("speaks only append-only language with the draft open, its editor shown, and a failure inline", async () => {
    const user = userEvent.setup();
    useRulingDraftMock.mockReturnValue(openDraft({ isError: true }));
    const { container } = renderPage();

    await user.click(screen.getByTestId("ruling-draft-kind-edit"));
    expect(
      screen.getByRole("textbox", { name: "Replacement output" }),
    ).toBeVisible();
    expect(container.textContent).not.toMatch(/\bupdate\b/i);
    expect(container.textContent).not.toMatch(/\bsave\b/i);
    expect(container.textContent).not.toMatch(/edit in place/i);
  });

  it("asks for the decision named in the URL and renders a focused heading", () => {
    renderPage("aaaa1111-0000-0000-0000-000000000000");
    expect(useMdrDecisionThreadMock).toHaveBeenLastCalledWith(
      "aaaa1111-0000-0000-0000-000000000000",
    );
    const heading = screen.getByRole("heading", { level: 1 });
    expect(heading).toHaveTextContent("Decision");
    expect(heading).toHaveFocus();
  });

  it("makes the correlation ref, outcome, and Ruling in force glanceable at the top", () => {
    useMdrDecisionThreadMock.mockReturnValue(
      served(makeThread({ rulingInForce: "EDIT" })),
    );
    renderPage();

    expect(screen.getByTestId("decision-correlation")).toHaveTextContent(
      "PAYMENT · 33333333-3333-3333-3333-333333333333",
    );
    expect(screen.getByTestId("decision-outcome")).toHaveTextContent(
      "Completed",
    );
    expect(screen.getByTestId("decision-ruling")).toHaveTextContent("Edit");
  });

  it("navigates a CASE correlation ref back to the investigation it belongs to", () => {
    useMdrDecisionThreadMock.mockReturnValue(
      served(
        makeThread({
          correlationType: "CASE",
          correlationId: "77777777-7777-7777-7777-777777777777",
        }),
      ),
    );
    renderPage();

    const correlation = screen.getByTestId("decision-correlation");
    expect(correlation).toHaveTextContent(
      "CASE · 77777777-7777-7777-7777-777777777777",
    );
    expect(
      within(correlation).getByTestId("decision-case-link"),
    ).toHaveAttribute(
      "href",
      "/ops/cases/77777777-7777-7777-7777-777777777777",
    );
  });

  for (const correlationType of ["ACCOUNT", "PAYMENT"]) {
    it(`leaves a ${correlationType} correlation ref as text, inventing no join`, () => {
      useMdrDecisionThreadMock.mockReturnValue(
        served(makeThread({ correlationType })),
      );
      renderPage();

      expect(screen.getByTestId("decision-correlation")).toHaveTextContent(
        `${correlationType} · 33333333-3333-3333-3333-333333333333`,
      );
      expect(
        screen.queryByTestId("decision-case-link"),
      ).not.toBeInTheDocument();
    });
  }

  it("shows Awaiting ruling while no ruling exists", () => {
    renderPage();
    expect(screen.getByTestId("decision-ruling")).toHaveTextContent(
      "Awaiting ruling",
    );
  });

  it("carries its own identity: the decision id at the top, shortened, with the full id a copy away", () => {
    renderPage();
    const identity = screen.getByTestId("decision-id");
    expect(identity).toHaveTextContent("decision 11111111");
    expect(identity).toHaveAttribute(
      "title",
      "11111111-1111-1111-1111-111111111111",
    );
    expect(
      within(identity).getByRole("button", { name: "Copy decision id" }),
    ).toBeInTheDocument();
  });

  it("renders a completed decision's record: prompt, raw output, verdict, pin, latency, cost", () => {
    useMdrDecisionThreadMock.mockReturnValue(
      served(
        makeThread({
          modelDecision: makeModelDecision({
            verdict: { assessment: "PASS", score: 0.97 },
          }),
        }),
      ),
    );
    renderPage();

    expect(screen.getByTestId("decision-rendered-prompt")).toHaveTextContent(
      "Assess this payment for anomalies.",
    );
    expect(screen.getByTestId("decision-raw-output")).toHaveTextContent(
      "No anomalies found.",
    );
    const verdict = screen.getByTestId("decision-verdict");
    expect(verdict).toHaveTextContent("assessment");
    expect(verdict).toHaveTextContent("PASS");
    expect(verdict).toHaveTextContent("score");
    expect(verdict).toHaveTextContent("0.97");
    expect(screen.getByTestId("decision-model")).toHaveTextContent(
      "claude-sonnet-5 · 20260301",
    );
    expect(screen.getByTestId("decision-template-pin")).toHaveTextContent(
      "payment-triage · v3",
    );
    expect(screen.getByTestId("decision-template-pin")).toHaveTextContent(
      "ab12cd34ef56",
    );
    expect(screen.getByTestId("decision-latency")).toHaveTextContent(
      "1,204 ms",
    );
    expect(screen.getByTestId("decision-cost")).toHaveTextContent("US$0.00125");
    expect(screen.getByTestId("decision-moment")).toHaveTextContent(
      "18 Jul 2026, 09:30",
    );
  });

  it("renders a timed-out decision's recorded absence as a fact, not an error state", () => {
    useMdrDecisionThreadMock.mockReturnValue(
      served(
        makeThread({
          modelDecision: makeModelDecision({
            outcome: "TIMED_OUT",
            rawOutput: undefined,
            verdict: undefined,
          }),
        }),
      ),
    );
    renderPage();

    expect(screen.getByTestId("decision-outcome")).toHaveTextContent(
      "Timed out",
    );
    expect(screen.getByTestId("decision-output-absent")).toHaveTextContent(
      "No output was recorded — the decision timed out.",
    );
    expect(screen.queryByTestId("decision-raw-output")).not.toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.getByTestId("decision-rendered-prompt")).toHaveTextContent(
      "Assess this payment for anomalies.",
    );
  });

  it("renders an errored decision's recorded absence with the error detail as facts", () => {
    useMdrDecisionThreadMock.mockReturnValue(
      served(
        makeThread({
          modelDecision: makeModelDecision({
            outcome: "ERRORED",
            rawOutput: undefined,
            verdict: undefined,
            errorDetail: "Upstream model returned 500 after 3 retries",
          }),
        }),
      ),
    );
    renderPage();

    expect(screen.getByTestId("decision-output-absent")).toHaveTextContent(
      "No output was recorded — the decision errored.",
    );
    expect(screen.getByTestId("decision-error-detail")).toHaveTextContent(
      "Upstream model returned 500 after 3 retries",
    );
    expect(screen.queryByTestId("decision-raw-output")).not.toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("records an absent verdict as a fact", () => {
    useMdrDecisionThreadMock.mockReturnValue(
      served(
        makeThread({
          modelDecision: makeModelDecision({ verdict: undefined }),
        }),
      ),
    );
    renderPage();
    expect(screen.getByTestId("decision-verdict")).toHaveTextContent(
      "None recorded.",
    );
  });

  it("renders retrieved-context refs as type/id/hash rows", () => {
    useMdrDecisionThreadMock.mockReturnValue(
      served(
        makeThread({
          modelDecision: makeModelDecision({
            retrievedContext: [
              { type: "PAYEE", id: "payee-77", contentHash: "feed0123beef" },
              { type: "ACCOUNT", id: "acct-12", contentHash: "cafe4567" },
            ],
          }),
        }),
      ),
    );
    renderPage();

    const refs = screen.getAllByTestId("decision-context-ref");
    expect(refs).toHaveLength(2);
    expect(refs[0]).toHaveTextContent("PAYEE");
    expect(refs[0]).toHaveTextContent("payee-77");
    expect(refs[0]).toHaveTextContent("feed0123beef");
    expect(refs[1]).toHaveTextContent("ACCOUNT");
    expect(refs[1]).toHaveTextContent("acct-12");
    expect(refs[1]).toHaveTextContent("cafe4567");
  });

  it("renders unexpected ref shapes without crashing", () => {
    const offContract = [
      { type: "PAYEE", id: 42, extra: { nested: true } },
      { unrecognised: "shape" },
    ] as unknown as MdrRetrievedContextRef[];
    useMdrDecisionThreadMock.mockReturnValue(
      served(
        makeThread({
          modelDecision: makeModelDecision({
            retrievedContext: offContract,
          }),
        }),
      ),
    );
    renderPage();

    const refs = screen.getAllByTestId("decision-context-ref");
    expect(refs).toHaveLength(2);
    expect(refs[0]).toHaveTextContent("PAYEE");
    expect(refs[0]).toHaveTextContent("42");
    expect(refs[1]).toHaveTextContent('{"unrecognised":"shape"}');
  });

  it("records absent retrieved context as a fact", () => {
    renderPage();
    expect(screen.getByTestId("decision-context-empty")).toHaveTextContent(
      "None recorded.",
    );
  });

  it("renders the ruling thread as served with kind, reviewer, and moment per Entry", () => {
    useMdrDecisionThreadMock.mockReturnValue(
      served(
        makeThread({
          dispositions: [
            makeDisposition({
              entryId: "e1",
              seq: 13,
              ts: "2026-07-18T10:15:00",
              disposition: "REJECT",
              humanId: "11111111-aaaa-4aaa-8aaa-111111111111",
            }),
            makeDisposition({
              entryId: "e2",
              seq: 18,
              ts: "2026-07-18T11:40:00",
              disposition: "ACCEPT",
              humanId: "22222222-bbbb-4bbb-8bbb-222222222222",
            }),
          ],
          rulingInForce: "ACCEPT",
        }),
      ),
    );
    renderPage();

    const entries = screen.getAllByTestId("disposition-entry");
    expect(entries).toHaveLength(2);
    expect(
      within(entries[0]).getByTestId("disposition-kind"),
    ).toHaveTextContent("Reject");
    expect(
      within(entries[0]).getByTestId("disposition-human"),
    ).toHaveTextContent("Reviewer 11111111");
    expect(within(entries[0]).getByTestId("disposition-human")).toHaveAttribute(
      "title",
      "11111111-aaaa-4aaa-8aaa-111111111111",
    );
    expect(entries[0]).toHaveTextContent("18 Jul 2026, 10:15");
    expect(
      within(entries[1]).getByTestId("disposition-kind"),
    ).toHaveTextContent("Accept");
    expect(
      within(entries[1]).getByTestId("disposition-human"),
    ).toHaveTextContent("Reviewer 22222222");
    expect(entries[1]).toHaveTextContent("18 Jul 2026, 11:40");
  });

  it("records an unruled decision's empty thread as a fact", () => {
    renderPage();
    expect(screen.getByTestId("dispositions-empty")).toHaveTextContent(
      "No rulings have been appended yet.",
    );
  });

  it("shows an edit Disposition's stored replacement and the derived line-diff", () => {
    useMdrDecisionThreadMock.mockReturnValue(
      served(
        makeThread({
          dispositions: [
            makeDisposition({
              disposition: "EDIT",
              editedOutput: "Two anomalies found.\nEscalate to fraud team.",
              diff: [
                { op: "REMOVED", text: "No anomalies found." },
                { op: "ADDED", text: "Two anomalies found." },
                { op: "ADDED", text: "Escalate to fraud team." },
              ],
            }),
          ],
          rulingInForce: "EDIT",
        }),
      ),
    );
    renderPage();

    const entry = screen.getByTestId("disposition-entry");
    expect(
      within(entry).getByTestId("disposition-replacement"),
    ).toHaveTextContent("Two anomalies found. Escalate to fraud team.");

    const lines = within(entry).getAllByTestId("diff-line");
    expect(lines).toHaveLength(3);
    expect(lines[0]).toHaveAttribute("data-op", "REMOVED");
    expect(lines[0]).toHaveTextContent("No anomalies found.");
    expect(lines[1]).toHaveAttribute("data-op", "ADDED");
    expect(lines[1]).toHaveTextContent("Two anomalies found.");
    expect(lines[2]).toHaveAttribute("data-op", "ADDED");
    expect(lines[2]).toHaveTextContent("Escalate to fraud team.");
  });

  it("marks unchanged diff lines distinctly from removed and added ones", () => {
    useMdrDecisionThreadMock.mockReturnValue(
      served(
        makeThread({
          dispositions: [
            makeDisposition({
              disposition: "EDIT",
              editedOutput: "Kept line\nNew line",
              diff: [
                { op: "UNCHANGED", text: "Kept line" },
                { op: "REMOVED", text: "Old line" },
                { op: "ADDED", text: "New line" },
              ],
            }),
          ],
        }),
      ),
    );
    renderPage();

    const lines = screen.getAllByTestId("diff-line");
    expect(lines[0]).toHaveAttribute("data-op", "UNCHANGED");
    expect(lines[0].className).not.toEqual(lines[1].className);
    expect(lines[1].className).not.toEqual(lines[2].className);
    expect(lines[0].className).not.toEqual(lines[2].className);
  });

  it("shows no replacement or diff for accept and reject Dispositions", () => {
    useMdrDecisionThreadMock.mockReturnValue(
      served(
        makeThread({
          dispositions: [
            makeDisposition({ disposition: "ACCEPT" }),
            makeDisposition({ entryId: "e2", disposition: "REJECT" }),
          ],
        }),
      ),
    );
    renderPage();

    expect(
      screen.queryByTestId("disposition-replacement"),
    ).not.toBeInTheDocument();
    expect(screen.queryByTestId("diff-line")).not.toBeInTheDocument();
  });

  it("renders a polite not-found for an unknown decision id, with a way back", () => {
    useMdrDecisionThreadMock.mockReturnValue({
      thread: null,
      isPending: false,
      isNotFound: true,
      isError: false,
      error: null,
      refetch: vi.fn(),
    });
    renderPage("99999999-9999-9999-9999-999999999999");

    const heading = screen.getByRole("heading", { level: 1 });
    expect(heading).toHaveTextContent("Decision not found");
    expect(heading).toHaveFocus();
    expect(screen.getByTestId("decision-not-found")).toHaveTextContent(
      "No decision with this id exists on the record.",
    );
    expect(screen.getByTestId("decision-back-link")).toHaveAttribute(
      "href",
      "/ops",
    );
  });

  it("renders a busy skeleton while the thread is in flight", () => {
    useMdrDecisionThreadMock.mockReturnValue({
      thread: null,
      isPending: true,
      isNotFound: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    });
    renderPage();
    expect(screen.getByTestId("decision-skeleton")).toHaveAttribute(
      "aria-busy",
      "true",
    );
  });

  it("renders an error state whose retry re-asks the thread without reloading", async () => {
    const user = userEvent.setup();
    const refetch = vi.fn();
    useMdrDecisionThreadMock.mockReturnValue({
      thread: null,
      isPending: false,
      isNotFound: false,
      isError: true,
      error: new Error("boom"),
      refetch,
    });
    renderPage();
    expect(screen.getByTestId("decision-error")).toHaveTextContent(
      "Couldn’t load the decision.",
    );

    await user.click(screen.getByTestId("decision-retry"));
    expect(refetch).toHaveBeenCalled();
  });

  it("keeps the record read-only: the append and gate-ask affordances are the only controls, and no update, save, or edit-in-place copy", () => {
    useMdrDecisionThreadMock.mockReturnValue(
      served(
        makeThread({
          modelDecision: makeModelDecision({
            retrievedContext: [{ type: "PAYEE", id: "p1", contentHash: "aa" }],
            verdict: { assessment: "PASS" },
          }),
          dispositions: [
            makeDisposition({
              disposition: "EDIT",
              editedOutput: "Replacement text",
              diff: [
                { op: "REMOVED", text: "No anomalies found." },
                { op: "ADDED", text: "Replacement text" },
              ],
            }),
          ],
          rulingInForce: "EDIT",
        }),
      ),
    );
    const { container } = renderPage();

    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
    const buttons = screen
      .getAllByRole("button")
      .filter((b) => b.getAttribute("data-testid") !== "copy-button");
    expect(buttons).toHaveLength(3);
    expect(buttons.map((b) => b.getAttribute("data-testid"))).toEqual([
      "ruling-draft-open",
      "explanation-ask",
      "gate-ask",
    ]);
    expect(container.querySelector("input, textarea, select")).toBeNull();
    expect(container.textContent).not.toMatch(/\bupdate\b/i);
    expect(container.textContent).not.toMatch(/\bsave\b/i);
    expect(container.textContent).not.toMatch(/edit in place/i);
  });

  it("offers to explain a payment-correlated decision, asking nothing until pressed", async () => {
    const user = userEvent.setup();
    const explanation = makeExplanation({ kind: "idle" });
    useExplanationMock.mockReturnValue(explanation);
    useMdrDecisionThreadMock.mockReturnValue(
      served(
        makeThread({
          correlationType: "PAYMENT",
          correlationId: "99999999-9999-9999-9999-999999999999",
        }),
      ),
    );
    renderPage();

    expect(useExplanationMock).toHaveBeenLastCalledWith(
      "99999999-9999-9999-9999-999999999999",
    );
    const panel = screen.getByTestId("explanation-panel");
    expect(within(panel).queryByTestId("explanation-claim")).toBeNull();

    await user.click(within(panel).getByTestId("explanation-ask"));
    expect(explanation.explain).toHaveBeenCalled();
  });

  it("renders the claims in served order with every source id beside its claim, and links the explanation's own record", () => {
    useExplanationMock.mockReturnValue(
      makeExplanation({
        kind: "grounded",
        decisionId: "abcdabcd-abcd-abcd-abcd-abcdabcdabcd",
        claims: [
          {
            text: "The payment was referred by screening.",
            sources: [
              { type: "PAYMENT", id: "pay-1" },
              { type: "SCREENING_DECISION", id: "scr-1" },
            ],
          },
          {
            text: "A hold of £0.50 was placed and never released.",
            sources: [{ type: "HOLD", id: "hold-1" }],
          },
        ],
      }),
    );
    renderPage();

    const claims = screen.getAllByTestId("explanation-claim");
    expect(claims).toHaveLength(2);
    expect(claims[0]).toHaveTextContent(
      "The payment was referred by screening",
    );
    expect(claims[1]).toHaveTextContent("A hold of £0.50 was placed");

    const firstSources = within(claims[0]).getAllByTestId(
      "explanation-claim-source",
    );
    expect(firstSources.map((s) => s.textContent)).toEqual([
      "PAYMENT · pay-1",
      "SCREENING_DECISION · scr-1",
    ]);
    expect(
      within(claims[1]).getByTestId("explanation-claim-source"),
    ).toHaveTextContent("HOLD · hold-1");

    expect(screen.getByTestId("explanation-record-link")).toHaveAttribute(
      "href",
      "/ops/decisions/abcdabcd-abcd-abcd-abcd-abcdabcdabcd",
    );
  });

  it("renders a refusal as a produced-then-withheld answer, distinct from an error and from nothing found", () => {
    useExplanationMock.mockReturnValue(
      makeExplanation({
        kind: "refused",
        decisionId: "abcdabcd-abcd-abcd-abcd-abcdabcdabcd",
      }),
    );
    renderPage();

    const refused = screen.getByTestId("explanation-refused");
    expect(refused).toHaveTextContent(/grounding failed/i);
    expect(refused).toHaveTextContent(/withheld/i);
    expect(refused).not.toHaveAttribute("role", "alert");
    expect(screen.queryByTestId("explanation-claim")).not.toBeInTheDocument();
    expect(
      screen.queryByTestId("explanation-ask-failed"),
    ).not.toBeInTheDocument();
    expect(
      within(refused).getByTestId("explanation-record-link"),
    ).toHaveAttribute(
      "href",
      "/ops/decisions/abcdabcd-abcd-abcd-abcd-abcdabcdabcd",
    );
  });

  it.each(["TIMED_OUT", "ERRORED"] as const)(
    "renders %s as no answer produced, with the record still linked",
    (outcome) => {
      useExplanationMock.mockReturnValue(
        makeExplanation({
          kind: "no-answer",
          decisionId: "abcdabcd-abcd-abcd-abcd-abcdabcdabcd",
          outcome,
        }),
      );
      renderPage();

      const noAnswer = screen.getByTestId("explanation-no-answer");
      expect(noAnswer).toHaveTextContent(/no answer was produced/i);
      expect(
        screen.queryByTestId("explanation-refused"),
      ).not.toBeInTheDocument();
      expect(
        within(noAnswer).getByTestId("explanation-record-link"),
      ).toHaveAttribute(
        "href",
        "/ops/decisions/abcdabcd-abcd-abcd-abcd-abcdabcdabcd",
      );
    },
  );

  it("renders a failed ask as a failure to reach the record, not as a refusal", () => {
    useExplanationMock.mockReturnValue(makeExplanation({ kind: "ask-failed" }));
    renderPage();

    const failure = screen.getByTestId("explanation-ask-failed");
    expect(failure).toHaveAttribute("role", "alert");
    expect(failure).toHaveTextContent(/not a refusal/i);
    expect(screen.queryByTestId("explanation-refused")).not.toBeInTheDocument();
    expect(
      screen.queryByTestId("explanation-record-link"),
    ).not.toBeInTheDocument();
  });

  it("reads as an aid and never as a finding the bank acts on", () => {
    useExplanationMock.mockReturnValue(
      makeExplanation({
        kind: "grounded",
        decisionId: "abcdabcd-abcd-abcd-abcd-abcdabcdabcd",
        claims: [
          { text: "A claim.", sources: [{ type: "PAYMENT", id: "p1" }] },
        ],
      }),
    );
    renderPage();

    const panel = screen.getByTestId("explanation-panel");
    expect(panel).toHaveTextContent(/never itself a finding/i);
    expect(panel).toHaveTextContent(/nothing in the bank acts on it/i);
  });

  it("holds the ask shut while an explanation is in flight", () => {
    useExplanationMock.mockReturnValue(makeExplanation({ kind: "asking" }));
    renderPage();

    expect(screen.getByTestId("explanation-ask")).toBeDisabled();
  });

  it("offers no explanation for a decision correlated to something other than a payment", () => {
    useMdrDecisionThreadMock.mockReturnValue(
      served(makeThread({ correlationType: "ACCOUNT" })),
    );
    renderPage();

    expect(screen.queryByTestId("explanation-panel")).not.toBeInTheDocument();
    expect(screen.queryByTestId("explanation-ask")).not.toBeInTheDocument();
  });

  it("offers no explanation of an advisory decision, which is an explanation already", () => {
    useMdrDecisionThreadMock.mockReturnValue(
      served(
        makeThread({
          correlationType: "PAYMENT",
          modelDecision: makeModelDecision({ advisory: true }),
        }),
      ),
    );
    renderPage();

    expect(screen.queryByTestId("explanation-panel")).not.toBeInTheDocument();
  });
});
