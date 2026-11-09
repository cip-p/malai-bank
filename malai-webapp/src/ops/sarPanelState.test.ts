import { describe, expect, it } from "vitest";
import type {
  AmlSarDraftResponse,
  AmlSarView,
} from "@/api/generated/types.gen";
import { sarPanelState, type SarDraftInvocation } from "./sarPanelState";

const DECISION_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
const NARRATIVE =
  "Between 20 and 22 July the account made three payments of £3,400 to one beneficiary.";

const IDLE: SarDraftInvocation = { kind: "idle" };

function served(
  overrides: Partial<AmlSarDraftResponse> = {},
): SarDraftInvocation {
  return {
    kind: "served",
    response: {
      decisionId: DECISION_ID,
      outcome: "COMPLETED",
      narrative: NARRATIVE,
      ...overrides,
    },
  };
}

function onRecord(overrides: Partial<AmlSarView> = {}): AmlSarView {
  return { decisionId: DECISION_ID, ...overrides };
}

describe("sarPanelState", () => {
  it("offers the request affordance on a non-terminal case with no draft", () => {
    expect(sarPanelState("OPEN", undefined, IDLE)).toEqual({
      kind: "requestable",
    });
    expect(sarPanelState("INVESTIGATING", undefined, IDLE)).toEqual({
      kind: "requestable",
    });
  });

  it("offers no affordance on a closed case that was never drafted", () => {
    expect(sarPanelState("CLOSED_DISMISSED", undefined, IDLE)).toEqual({
      kind: "never-drafted",
    });
  });

  it("reports the invocation in flight", () => {
    expect(sarPanelState("OPEN", undefined, { kind: "drafting" })).toEqual({
      kind: "drafting",
    });
  });

  it("renders a grounded draft inline from the invocation response", () => {
    expect(sarPanelState("INVESTIGATING", undefined, served())).toEqual({
      kind: "grounded",
      decisionId: DECISION_ID,
      narrative: NARRATIVE,
    });
  });

  it("reports a draft produced and then refused for failing grounding", () => {
    expect(
      sarPanelState(
        "INVESTIGATING",
        undefined,
        served({ narrative: undefined, refusalCode: "GROUNDING_FAILED" }),
      ),
    ).toEqual({ kind: "refused", decisionId: DECISION_ID });
  });

  it.each(["TIMED_OUT", "ERRORED"] as const)(
    "reports %s as no answer produced, against a record that exists",
    (outcome) => {
      expect(
        sarPanelState(
          "INVESTIGATING",
          undefined,
          served({ outcome, narrative: undefined }),
        ),
      ).toEqual({ kind: "no-answer", decisionId: DECISION_ID, outcome });
    },
  );

  it("reports a request that never reached a record, which is not a refusal", () => {
    expect(sarPanelState("OPEN", undefined, { kind: "failed" })).toEqual({
      kind: "request-failed",
    });
  });

  it("shows the draft on record with its ruling in force on revisit", () => {
    expect(
      sarPanelState(
        "INVESTIGATING",
        onRecord({ rulingInForce: "ACCEPT" }),
        IDLE,
      ),
    ).toEqual({
      kind: "on-record",
      decisionId: DECISION_ID,
      rulingInForce: "ACCEPT",
    });
  });

  it("shows an unruled draft on record as awaiting its first ruling", () => {
    expect(sarPanelState("INVESTIGATING", onRecord(), IDLE)).toEqual({
      kind: "on-record",
      decisionId: DECISION_ID,
      rulingInForce: undefined,
    });
  });

  it("prefers the ephemeral fresh draft over the record it just wrote", () => {
    expect(sarPanelState("INVESTIGATING", onRecord(), served())).toEqual({
      kind: "grounded",
      decisionId: DECISION_ID,
      narrative: NARRATIVE,
    });
  });

  it("renders the attached narrative once the case is closed reported", () => {
    expect(
      sarPanelState(
        "CLOSED_REPORTED",
        onRecord({ rulingInForce: "ACCEPT", narrative: NARRATIVE }),
        IDLE,
      ),
    ).toEqual({
      kind: "attached",
      decisionId: DECISION_ID,
      narrative: NARRATIVE,
    });
  });

  it("keeps the attached narrative over a fresh draft, because the filed story is permanent", () => {
    expect(
      sarPanelState(
        "CLOSED_REPORTED",
        onRecord({ narrative: NARRATIVE }),
        served({ narrative: "a later draft" }),
      ),
    ).toEqual({
      kind: "attached",
      decisionId: DECISION_ID,
      narrative: NARRATIVE,
    });
  });

  it("falls back to the record when a closed-reported case carries no narrative", () => {
    expect(sarPanelState("CLOSED_REPORTED", onRecord(), IDLE)).toEqual({
      kind: "on-record",
      decisionId: DECISION_ID,
      rulingInForce: undefined,
    });
  });
});
