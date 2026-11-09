import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import type { AmlCaseStatus, AmlTypology } from "@/api/generated/types.gen";
import {
  CASE_STATUS_LABEL,
  CaseStatusTag,
  TYPOLOGY_LABEL,
  isTerminal,
} from "./caseTags";

describe("CaseStatusTag", () => {
  const labels: [AmlCaseStatus, string][] = [
    ["OPEN", "Open"],
    ["INVESTIGATING", "Investigating"],
    ["CLOSED_DISMISSED", "Closed dismissed"],
    ["CLOSED_REPORTED", "Closed reported"],
  ];

  for (const [status, label] of labels) {
    it(`wears ${status} as "${label}"`, () => {
      render(<CaseStatusTag status={status} testId="case-status" />);
      expect(screen.getByTestId("case-status")).toHaveTextContent(label);
    });
  }

  it("labels every lifecycle state the contract can serve", () => {
    expect(Object.keys(CASE_STATUS_LABEL)).toEqual([
      "OPEN",
      "INVESTIGATING",
      "CLOSED_DISMISSED",
      "CLOSED_REPORTED",
    ]);
  });

  it("distinguishes a filed report from a dismissal by more than its words", () => {
    const { rerender } = render(
      <CaseStatusTag status="CLOSED_REPORTED" testId="case-status" />,
    );
    const reported = screen.getByTestId("case-status").className;
    rerender(<CaseStatusTag status="CLOSED_DISMISSED" testId="case-status" />);
    expect(screen.getByTestId("case-status").className).not.toBe(reported);
  });
});

describe("TYPOLOGY_LABEL", () => {
  const labels: [AmlTypology, string][] = [
    ["STRUCTURING", "Structuring"],
    ["RAPID_IN_OUT", "Rapid in-out"],
    ["HIGH_RISK_CORRIDOR", "High-risk corridor"],
  ];

  for (const [typology, label] of labels) {
    it(`reads ${typology} as "${label}"`, () => {
      expect(TYPOLOGY_LABEL[typology]).toBe(label);
    });
  }

  it("names every pattern the contract can detect", () => {
    expect(Object.keys(TYPOLOGY_LABEL)).toEqual([
      "STRUCTURING",
      "RAPID_IN_OUT",
      "HIGH_RISK_CORRIDOR",
    ]);
  });
});

describe("isTerminal", () => {
  it("reads both closed states as terminal", () => {
    expect(isTerminal("CLOSED_DISMISSED")).toBe(true);
    expect(isTerminal("CLOSED_REPORTED")).toBe(true);
  });

  it("reads a live investigation as non-terminal", () => {
    expect(isTerminal("OPEN")).toBe(false);
    expect(isTerminal("INVESTIGATING")).toBe(false);
  });
});
