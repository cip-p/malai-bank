import { describe, expect, it } from "vitest";
import { copaNextStep } from "./copaNextStep";

describe("copaNextStep", () => {
  it("returns { kind: 'MATCH' } for verdict MATCH with save-payee intent", () => {
    expect(copaNextStep({ result: "MATCH" }, "save-payee")).toEqual({
      kind: "MATCH",
    });
  });

  it("returns { kind: 'MATCH' } for verdict MATCH with transfer intent", () => {
    expect(copaNextStep({ result: "MATCH" }, "transfer")).toEqual({
      kind: "MATCH",
    });
  });

  it("returns { kind: 'CLOSE_MATCH', onRecordName, intent } with save-payee", () => {
    expect(
      copaNextStep(
        { result: "CLOSE_MATCH", onRecordName: "Thames Water" },
        "save-payee",
      ),
    ).toEqual({
      kind: "CLOSE_MATCH",
      onRecordName: "Thames Water",
      intent: "save-payee",
    });
  });

  it("returns { kind: 'CLOSE_MATCH', onRecordName, intent } with transfer", () => {
    expect(
      copaNextStep(
        { result: "CLOSE_MATCH", onRecordName: "Sarah Mitchell" },
        "transfer",
      ),
    ).toEqual({
      kind: "CLOSE_MATCH",
      onRecordName: "Sarah Mitchell",
      intent: "transfer",
    });
  });

  it("returns { kind: 'NO_MATCH', intent: 'save-payee' }", () => {
    expect(copaNextStep({ result: "NO_MATCH" }, "save-payee")).toEqual({
      kind: "NO_MATCH",
      intent: "save-payee",
    });
  });

  it("returns { kind: 'NO_MATCH', intent: 'transfer' }", () => {
    expect(copaNextStep({ result: "NO_MATCH" }, "transfer")).toEqual({
      kind: "NO_MATCH",
      intent: "transfer",
    });
  });

  it("falls back to an empty on-record name when CLOSE_MATCH omits it", () => {
    expect(copaNextStep({ result: "CLOSE_MATCH" }, "transfer")).toEqual({
      kind: "CLOSE_MATCH",
      onRecordName: "",
      intent: "transfer",
    });
  });
});
