import { describe, expect, it } from "vitest";
import { verdictHighlight } from "./verdictHighlight";

describe("verdictHighlight", () => {
  it("reads a screening verdict as its decision and the rule behind it", () => {
    expect(verdictHighlight({ decision: "REFER", rule: "WATCHLIST" })).toEqual({
      conclusion: "REFER",
      detail: "WATCHLIST",
    });
  });

  it("reads a screening decision that names no rule", () => {
    expect(verdictHighlight({ decision: "PASS" })).toEqual({
      conclusion: "PASS",
      detail: null,
    });
  });

  it("reads a rule that names no decision", () => {
    expect(verdictHighlight({ rule: "WATCHLIST" })).toEqual({
      conclusion: null,
      detail: "WATCHLIST",
    });
  });

  it("reads an AML alert verdict as its typology and risk score", () => {
    expect(
      verdictHighlight({
        typology: "STRUCTURING",
        riskScore: 82,
        evidencePaymentIds: ["a", "b", "c"],
      }),
    ).toEqual({ conclusion: "Structuring", detail: "risk 82" });
  });

  it("names every typology the contract can detect", () => {
    expect(
      verdictHighlight({ typology: "RAPID_IN_OUT", riskScore: 60 }),
    ).toEqual({ conclusion: "Rapid in-out", detail: "risk 60" });
    expect(
      verdictHighlight({ typology: "HIGH_RISK_CORRIDOR", riskScore: 75 }),
    ).toEqual({ conclusion: "High-risk corridor", detail: "risk 75" });
  });

  it("keeps a risk score of zero, which is a score and not an absence", () => {
    expect(verdictHighlight({ typology: "STRUCTURING", riskScore: 0 })).toEqual(
      {
        conclusion: "Structuring",
        detail: "risk 0",
      },
    );
  });

  it("names the typology even when no risk score is carried", () => {
    expect(verdictHighlight({ typology: "STRUCTURING" })).toEqual({
      conclusion: "Structuring",
      detail: null,
    });
  });

  it("recognises nothing in a typology it cannot name", () => {
    expect(
      verdictHighlight({ typology: "SMURFING_VIA_MULES", riskScore: 90 }),
    ).toEqual({ conclusion: null, detail: null });
  });

  it("recognises nothing in a verdict speaking neither vocabulary", () => {
    expect(verdictHighlight({ assessment: "PASS" })).toEqual({
      conclusion: null,
      detail: null,
    });
  });

  it("recognises nothing in an absent verdict", () => {
    expect(verdictHighlight(undefined)).toEqual({
      conclusion: null,
      detail: null,
    });
  });

  it("ignores fields carrying something other than the shape it reads", () => {
    expect(
      verdictHighlight({ decision: 7, rule: null, typology: { of: "sorts" } }),
    ).toEqual({ conclusion: null, detail: null });
  });
});
