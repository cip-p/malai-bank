import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import type { MdrDecisionSummary } from "@/api/generated/types.gen";
import type { UseMdrDecisionsResult } from "@/ops/useMdrDecisions";
import type { WorklistCounts } from "@/ops/useWorklistCounts";

vi.mock("@/ops/useMdrDecisions", () => ({
  useMdrDecisions: vi.fn(),
}));

vi.mock("@/ops/useWorklistCounts", () => ({
  useWorklistCounts: vi.fn(),
}));

const hookModule = await import("@/ops/useMdrDecisions");
const useMdrDecisionsMock = hookModule.useMdrDecisions as unknown as ReturnType<
  typeof vi.fn<(state: string) => UseMdrDecisionsResult>
>;

const countsModule = await import("@/ops/useWorklistCounts");
const useWorklistCountsMock =
  countsModule.useWorklistCounts as unknown as ReturnType<
    typeof vi.fn<() => WorklistCounts>
  >;

import { WorklistPage } from "./WorklistPage";

function makeDecision(
  overrides: Partial<MdrDecisionSummary>,
): MdrDecisionSummary {
  return {
    decisionId: "11111111-1111-1111-1111-111111111111",
    entryId: "22222222-2222-2222-2222-222222222222",
    seq: 40,
    ts: "2026-07-18T09:30:00",
    outcome: "COMPLETED",
    modelId: "claude-sonnet-5",
    modelVersion: "20260301",
    correlationType: "PAYMENT",
    correlationId: "33333333-3333-3333-3333-333333333333",
    dispositionCount: 0,
    latencyMs: 1204,
    costMicros: 1250,
    costCurrency: "USD",
    advisory: false,
    ...overrides,
  };
}

function served(decisions: MdrDecisionSummary[]): UseMdrDecisionsResult {
  return {
    decisions,
    isPending: false,
    isError: false,
    error: null,
    refetch: vi.fn(),
  };
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/ops"]}>
      <Routes>
        <Route path="/ops" element={<WorklistPage />} />
        <Route
          path="/ops/decisions/:decisionId"
          element={<p data-testid="decision-probe" />}
        />
      </Routes>
    </MemoryRouter>,
  );
}

describe("WorklistPage", () => {
  beforeEach(() => {
    useMdrDecisionsMock.mockReset();
    useMdrDecisionsMock.mockReturnValue(served([]));
    useWorklistCountsMock.mockReset();
    useWorklistCountsMock.mockReturnValue({});
  });

  it("renders an h1 'Worklist' focused on mount", () => {
    renderPage();
    const heading = screen.getByRole("heading", { level: 1 });
    expect(heading).toHaveTextContent("Worklist");
    expect(heading).toHaveFocus();
  });

  it("asks for non-advisory awaiting-ruling decisions by default", () => {
    renderPage();
    expect(useMdrDecisionsMock).toHaveBeenLastCalledWith({
      rulingState: "AWAITING_RULING",
      advisory: false,
    });
    expect(screen.getByTestId("worklist-filter-awaiting")).toHaveAttribute(
      "aria-pressed",
      "true",
    );
  });

  it("keeps advisory decisions out of every ruling state, since none of them hold money up", async () => {
    const user = userEvent.setup();
    renderPage();

    for (const testId of [
      "worklist-filter-accept",
      "worklist-filter-edit",
      "worklist-filter-reject",
    ]) {
      await user.click(screen.getByTestId(testId));
      expect(useMdrDecisionsMock).toHaveBeenLastCalledWith(
        expect.objectContaining({ advisory: false }),
      );
    }
  });

  it("switches the filter across the four ruling states", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByTestId("worklist-filter-accept"));
    expect(useMdrDecisionsMock).toHaveBeenLastCalledWith(
      expect.objectContaining({ rulingState: "ACCEPT" }),
    );
    expect(screen.getByTestId("worklist-filter-accept")).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    expect(screen.getByTestId("worklist-filter-awaiting")).toHaveAttribute(
      "aria-pressed",
      "false",
    );

    await user.click(screen.getByTestId("worklist-filter-edit"));
    expect(useMdrDecisionsMock).toHaveBeenLastCalledWith(
      expect.objectContaining({ rulingState: "EDIT" }),
    );

    await user.click(screen.getByTestId("worklist-filter-reject"));
    expect(useMdrDecisionsMock).toHaveBeenLastCalledWith(
      expect.objectContaining({ rulingState: "REJECT" }),
    );

    await user.click(screen.getByTestId("worklist-filter-awaiting"));
    expect(useMdrDecisionsMock).toHaveBeenLastCalledWith(
      expect.objectContaining({ rulingState: "AWAITING_RULING" }),
    );
  });

  it("offers the advisory records as their own view, outside every ruling state", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByTestId("worklist-filter-advisory"));
    expect(useMdrDecisionsMock).toHaveBeenLastCalledWith({ advisory: true });
    expect(screen.getByTestId("worklist-filter-advisory")).toHaveAttribute(
      "aria-pressed",
      "true",
    );
  });

  it("shows each view's queue depth on its filter chip", () => {
    useWorklistCountsMock.mockReturnValue({
      AWAITING_RULING: 6,
      ACCEPT: 2,
      ADVISORY: 9,
    });
    renderPage();

    expect(screen.getByTestId("worklist-filter-awaiting")).toHaveTextContent(
      "Awaiting ruling6",
    );
    expect(screen.getByTestId("worklist-filter-accept")).toHaveTextContent(
      "Accept2",
    );
    expect(screen.getByTestId("worklist-filter-advisory")).toHaveTextContent(
      "Advisory9",
    );
    expect(screen.getByTestId("worklist-filter-edit")).toHaveTextContent(
      /^Edit$/,
    );
  });

  it("orders the awaiting queue oldest first, so the longest-waiting decision is worked first", () => {
    useMdrDecisionsMock.mockReturnValue(
      served([
        makeDecision({ decisionId: "aaa", seq: 40, modelId: "model-newest" }),
        makeDecision({ decisionId: "bbb", seq: 12, modelId: "model-oldest" }),
      ]),
    );
    renderPage();

    const rows = screen.getAllByTestId("worklist-row");
    expect(rows).toHaveLength(2);
    expect(rows[0]).toHaveTextContent("model-oldest");
    expect(rows[1]).toHaveTextContent("model-newest");
  });

  it("keeps ruled views in served order, newest first", async () => {
    const user = userEvent.setup();
    useMdrDecisionsMock.mockReturnValue(
      served([
        makeDecision({
          decisionId: "aaa",
          seq: 40,
          modelId: "model-newest",
          rulingInForce: "ACCEPT",
        }),
        makeDecision({
          decisionId: "bbb",
          seq: 12,
          modelId: "model-oldest",
          rulingInForce: "ACCEPT",
        }),
      ]),
    );
    renderPage();
    await user.click(screen.getByTestId("worklist-filter-accept"));

    const rows = screen.getAllByTestId("worklist-row");
    expect(rows[0]).toHaveTextContent("model-newest");
    expect(rows[1]).toHaveTextContent("model-oldest");
  });

  it("shows Outcome as a glance badge per shape", () => {
    useMdrDecisionsMock.mockReturnValue(
      served([
        makeDecision({ decisionId: "a", outcome: "COMPLETED" }),
        makeDecision({ decisionId: "b", outcome: "TIMED_OUT" }),
        makeDecision({ decisionId: "c", outcome: "ERRORED" }),
      ]),
    );
    renderPage();

    const outcomes = screen.getAllByTestId("worklist-outcome");
    expect(outcomes[0]).toHaveTextContent("Completed");
    expect(outcomes[1]).toHaveTextContent("Timed out");
    expect(outcomes[2]).toHaveTextContent("Errored");
  });

  it("says what each decision concluded: the verdict's decision and rule on the row", () => {
    useMdrDecisionsMock.mockReturnValue(
      served([
        makeDecision({
          decisionId: "a",
          verdict: { decision: "REFER", rule: "WATCHLIST" },
        }),
      ]),
    );
    renderPage();

    const verdict = screen.getByTestId("worklist-verdict");
    expect(verdict).toHaveTextContent("REFER");
    expect(verdict).toHaveTextContent("WATCHLIST");
  });

  it("says what an AML alert concluded: its typology and risk score on the row", () => {
    useMdrDecisionsMock.mockReturnValue(
      served([
        makeDecision({
          decisionId: "a",
          correlationType: "ACCOUNT",
          verdict: {
            typology: "STRUCTURING",
            riskScore: 82,
            evidencePaymentIds: ["p1", "p2", "p3"],
          },
        }),
      ]),
    );
    renderPage();

    const verdict = screen.getByTestId("worklist-verdict");
    expect(verdict).toHaveTextContent("Structuring");
    expect(verdict).toHaveTextContent("risk 82");
    expect(verdict).not.toHaveTextContent("STRUCTURING");
  });

  it("falls back to the raw verdict shape when it names no decision or rule", () => {
    useMdrDecisionsMock.mockReturnValue(
      served([
        makeDecision({ decisionId: "a", verdict: { assessment: "PASS" } }),
      ]),
    );
    renderPage();

    expect(screen.getByTestId("worklist-verdict")).toHaveTextContent(
      '{"assessment":"PASS"}',
    );
  });

  it("marks an absent verdict as a dash, not an error", () => {
    useMdrDecisionsMock.mockReturnValue(
      served([makeDecision({ decisionId: "a", verdict: undefined })]),
    );
    renderPage();

    expect(screen.getByTestId("worklist-verdict")).toHaveTextContent("—");
  });

  it("renders correlation ref, rulings count, model, latency, cost, age, and moment", () => {
    useMdrDecisionsMock.mockReturnValue(
      served([makeDecision({ dispositionCount: 3 })]),
    );
    renderPage();

    const row = screen.getByTestId("worklist-row");
    expect(within(row).getByTestId("worklist-model")).toHaveTextContent(
      "claude-sonnet-5 · 20260301",
    );
    expect(within(row).getByTestId("worklist-correlation")).toHaveTextContent(
      "PAYMENT · 33333333-3333-3333-3333-333333333333",
    );
    expect(within(row).getByTestId("worklist-dispositions")).toHaveTextContent(
      "3",
    );
    expect(within(row).getByTestId("worklist-latency")).toHaveTextContent(
      "1,204 ms",
    );
    expect(within(row).getByTestId("worklist-cost")).toHaveTextContent(
      "US$0.00125",
    );
    expect(within(row).getByTestId("worklist-ts")).toHaveTextContent(
      "18 Jul 2026, 09:30",
    );
    expect(within(row).getByTestId("worklist-link")).toHaveTextContent(
      /ago|just now/,
    );
  });

  it("deep-links each row to its Decision view", () => {
    useMdrDecisionsMock.mockReturnValue(
      served([
        makeDecision({ decisionId: "aaa", seq: 1 }),
        makeDecision({ decisionId: "bbb", seq: 2 }),
      ]),
    );
    renderPage();

    const links = screen.getAllByTestId("worklist-link");
    expect(links).toHaveLength(2);
    expect(links[0]).toHaveAttribute("href", "/ops/decisions/aaa");
    expect(links[1]).toHaveAttribute("href", "/ops/decisions/bbb");
  });

  it("opens the decision when the row is clicked anywhere, not only on the link", async () => {
    const user = userEvent.setup();
    useMdrDecisionsMock.mockReturnValue(
      served([makeDecision({ decisionId: "aaa" })]),
    );
    renderPage();

    await user.click(screen.getByTestId("worklist-verdict"));
    expect(screen.getByTestId("decision-probe")).toBeInTheDocument();
  });

  it("shows calm empty copy for the awaiting filter", () => {
    renderPage();
    expect(screen.getByTestId("worklist-empty")).toHaveTextContent(
      "Nothing awaiting a ruling.",
    );
  });

  it("shows calm empty copy for a ruling filter", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(screen.getByTestId("worklist-filter-reject"));
    expect(screen.getByTestId("worklist-empty")).toHaveTextContent(
      "No decisions with this ruling in force.",
    );
  });

  it("shows calm empty copy for the advisory view", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(screen.getByTestId("worklist-filter-advisory"));
    expect(screen.getByTestId("worklist-empty")).toHaveTextContent(
      "No advisory decisions recorded.",
    );
  });

  it("renders a busy skeleton while pending", () => {
    useMdrDecisionsMock.mockReturnValue({
      decisions: null,
      isPending: true,
      isError: false,
      error: null,
      refetch: vi.fn(),
    });
    renderPage();
    expect(screen.getByTestId("worklist-skeleton")).toHaveAttribute(
      "aria-busy",
      "true",
    );
  });

  it("renders an error state whose retry re-asks the worklist without reloading", async () => {
    const user = userEvent.setup();
    const refetch = vi.fn();
    useMdrDecisionsMock.mockReturnValue({
      decisions: null,
      isPending: false,
      isError: true,
      error: new Error("boom"),
      refetch,
    });
    renderPage();
    expect(screen.getByTestId("worklist-error")).toHaveTextContent(
      "Couldn’t load the worklist.",
    );

    await user.click(screen.getByTestId("worklist-retry"));
    expect(refetch).toHaveBeenCalled();
  });
});
