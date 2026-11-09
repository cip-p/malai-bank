import { describe, expect, it } from "vitest";
import { HttpError } from "@/api/http";
import { closeOutcome } from "./closeRefusal";

describe("closeOutcome", () => {
  it("reads an accepted Alert standing in the way of a dismissal", () => {
    expect(closeOutcome(new HttpError(409, { message: "6990" }))).toEqual({
      kind: "refused",
      reason: "accepted-alert-stands",
    });
  });

  it("reads the absence of a SAR ruling in force blocking a report", () => {
    expect(closeOutcome(new HttpError(409, { message: "6989" }))).toEqual({
      kind: "refused",
      reason: "no-sar-ruling-in-force",
    });
  });

  it("reads a Case that another officer already closed", () => {
    expect(closeOutcome(new HttpError(409, { message: "6988" }))).toEqual({
      kind: "refused",
      reason: "already-closed",
    });
  });

  it("falls back generic on a refusal code this screen cannot name", () => {
    expect(closeOutcome(new HttpError(409, { message: "6971" }))).toEqual({
      kind: "refused",
      reason: "unknown",
    });
  });

  it("calls a 500 what it is — the request failed, which is not a refusal", () => {
    expect(closeOutcome(new HttpError(500, { message: "9999" }))).toEqual({
      kind: "failed",
    });
  });

  it("calls a request that never reached the bank a failure too", () => {
    expect(closeOutcome(new Error("network down"))).toEqual({ kind: "failed" });
  });

  it("refuses generically when a 409 arrives with no code to read", () => {
    expect(closeOutcome(new HttpError(409, undefined))).toEqual({
      kind: "refused",
      reason: "unknown",
    });
  });
});
