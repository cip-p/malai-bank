import { describe, expect, it } from "vitest";
import { FORBIDDEN_TERMS, findForbidden } from "../../src/lib/forbidden-terms";

describe("findForbidden", () => {
  it("returns [] for empty input", () => {
    expect(findForbidden("")).toEqual([]);
  });

  it("returns [] for clean marketing copy", () => {
    const clean =
      "Malai is a working reference platform — not an authorised bank, holds no money.";
    expect(findForbidden(clean)).toEqual([]);
  });

  it("flags a single forbidden cloud-provider term", () => {
    expect(findForbidden("We run on AWS.")).toEqual(["AWS"]);
  });

  it("flags an internal service identifier", () => {
    expect(findForbidden("calls go through account-service")).toEqual([
      "account-service",
    ]);
  });

  it("flags an internal service when embedded in a longer string", () => {
    expect(findForbidden("see scripts/account-service-helper.ts")).toEqual([
      "account-service",
    ]);
  });

  it("returns each forbidden term at most once", () => {
    const text = "MalaiBank uses MalaiBank-internal naming for MalaiBank.";
    expect(findForbidden(text)).toEqual(["MalaiBank"]);
  });

  it("flags multiple distinct terms when several appear", () => {
    const hits = findForbidden("MalaiBank uses Fargate and S3.");
    expect(hits.sort()).toEqual(["Fargate", "MalaiBank", "S3"]);
  });

  it("does not flag the near-miss BACS", () => {
    expect(findForbidden("Settled overnight via BACS.")).toEqual([]);
  });

  it("is case-sensitive — lowercase aws is not flagged", () => {
    expect(findForbidden("the aws region went down")).toEqual([]);
  });

  it("flags every canonical term when the whole list is present", () => {
    const blob = FORBIDDEN_TERMS.join(" ");
    expect(findForbidden(blob).sort()).toEqual([...FORBIDDEN_TERMS].sort());
  });

  it("does not flag account-service when only account-holder-service is present", () => {
    expect(findForbidden("calls go through account-holder-service")).toEqual([
      "account-holder-service",
    ]);
  });
});
