import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { Money } from "./Money";

describe("Money", () => {
  it("formats a positive GBP minor-unit amount without a sign by default", () => {
    render(<Money minorUnits={1250} />);
    expect(screen.getByTestId("money")).toHaveTextContent("£12.50");
  });

  it("formats a negative GBP amount with the Unicode minus by default", () => {
    render(<Money minorUnits={-1250} />);
    expect(screen.getByTestId("money")).toHaveTextContent("−£12.50");
  });

  it("formats zero as £0.00 without a sign", () => {
    render(<Money minorUnits={0} />);
    expect(screen.getByTestId("money")).toHaveTextContent("£0.00");
  });

  it("inserts the en-GB thousands separator", () => {
    render(<Money minorUnits={123456} />);
    expect(screen.getByTestId("money")).toHaveTextContent("£1,234.56");
  });

  it("renders the negative sign for negatives even when signed='never'", () => {
    render(<Money minorUnits={-1250} signed="never" />);
    expect(screen.getByTestId("money")).toHaveTextContent("£12.50");
    expect(screen.getByTestId("money")).not.toHaveTextContent("−");
    expect(screen.getByTestId("money")).not.toHaveTextContent("-");
  });

  it("prepends + on positives when signed='always'", () => {
    render(<Money minorUnits={1250} signed="always" />);
    expect(screen.getByTestId("money")).toHaveTextContent("+£12.50");
  });

  it("keeps the Unicode minus on negatives when signed='always'", () => {
    render(<Money minorUnits={-1250} signed="always" />);
    expect(screen.getByTestId("money")).toHaveTextContent("−£12.50");
  });

  it("renders zero as £0.00 without a sign even when signed='always'", () => {
    render(<Money minorUnits={0} signed="always" />);
    expect(screen.getByTestId("money")).toHaveTextContent("£0.00");
    expect(screen.getByTestId("money")).not.toHaveTextContent("+");
    expect(screen.getByTestId("money")).not.toHaveTextContent("−");
  });

  it("applies the tabular-nums utility", () => {
    render(<Money minorUnits={1250} />);
    expect(screen.getByTestId("money")).toHaveClass("tabular-nums");
  });

  it("applies a positive-tone class when signed='always' and the value is positive", () => {
    render(<Money minorUnits={1250} signed="always" />);
    expect(screen.getByTestId("money")).toHaveClass("text-positive");
  });

  it("keeps debits in the default ink when signed='always' and the value is negative", () => {
    render(<Money minorUnits={-1250} signed="always" />);
    expect(screen.getByTestId("money")).not.toHaveClass("text-negative");
    expect(screen.getByTestId("money")).not.toHaveClass("text-positive");
  });

  it("does not apply a tone class when signed='always' and the value is zero", () => {
    render(<Money minorUnits={0} signed="always" />);
    expect(screen.getByTestId("money")).not.toHaveClass("text-positive");
    expect(screen.getByTestId("money")).not.toHaveClass("text-negative");
  });

  it("does not apply a tone class by default", () => {
    render(<Money minorUnits={-1250} />);
    expect(screen.getByTestId("money")).not.toHaveClass("text-positive");
    expect(screen.getByTestId("money")).not.toHaveClass("text-negative");
  });
});
