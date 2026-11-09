import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { BalanceText } from "./BalanceText";

describe("BalanceText", () => {
  it("renders the formatted currency string when hidden is false", () => {
    render(
      <BalanceText amountMinorUnits={123456} currency="GBP" hidden={false} />,
    );
    expect(screen.getByText("£1,234.56")).toBeInTheDocument();
  });

  it("renders the masked placeholder when hidden is true", () => {
    render(
      <BalanceText amountMinorUnits={123456} currency="GBP" hidden={true} />,
    );
    expect(screen.getByText("£·····")).toBeInTheDocument();
  });

  it("formats negative amounts with the Unicode minus", () => {
    render(
      <BalanceText amountMinorUnits={-1250} currency="GBP" hidden={false} />,
    );
    expect(screen.getByText("−£12.50")).toBeInTheDocument();
  });
});
