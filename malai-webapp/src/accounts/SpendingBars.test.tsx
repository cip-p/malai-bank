import { describe, expect, it } from "vitest";
import { render, screen, within } from "@testing-library/react";
import type { SpendingCategoryTotal } from "@/api/generated/types.gen";
import { SpendingBars } from "./SpendingBars";

const CATEGORIES: SpendingCategoryTotal[] = [
  { category: "EATING_OUT", totalMinorUnits: 5000, transactionCount: 1 },
  { category: "GROCERIES", totalMinorUnits: 10000, transactionCount: 4 },
];

function renderBars(totalMinorUnits = 15000) {
  return render(
    <SpendingBars
      categories={CATEGORIES}
      totalMinorUnits={totalMinorUnits}
      currency="GBP"
    />,
  );
}

describe("SpendingBars", () => {
  it("leads with the month's total", () => {
    renderBars();
    expect(screen.getByTestId("spending-total")).toHaveTextContent("£150.00");
  });

  it("ranks categories largest first, each with label, amount, share and count", () => {
    renderBars();
    const rows = screen.getAllByTestId("spending-row");
    expect(rows).toHaveLength(2);
    expect(rows[0]).toHaveAttribute("data-category", "GROCERIES");
    expect(within(rows[0]).getByText("Groceries")).toBeInTheDocument();
    expect(
      within(rows[0]).getByTestId("spending-row-amount"),
    ).toHaveTextContent("£100.00");
    expect(within(rows[0]).getByTestId("spending-row-share")).toHaveTextContent(
      "67% · 4 transactions",
    );
    expect(rows[1]).toHaveAttribute("data-category", "EATING_OUT");
    expect(within(rows[1]).getByTestId("spending-row-share")).toHaveTextContent(
      "33% · 1 transaction",
    );
  });

  it("draws each bar as that category's share of the total, in one hue", () => {
    renderBars();
    const bars = screen.getAllByTestId("spending-row-bar");
    expect(bars[0]).toHaveStyle({ width: "66.66666666666666%" });
    expect(bars[1]).toHaveStyle({ width: "33.33333333333333%" });
    for (const bar of bars) expect(bar).toHaveClass("bg-primary");
  });

  it("draws empty bars rather than dividing by zero when nothing was spent", () => {
    render(
      <SpendingBars
        categories={CATEGORIES}
        totalMinorUnits={0}
        currency="GBP"
      />,
    );
    for (const bar of screen.getAllByTestId("spending-row-bar")) {
      expect(bar).toHaveStyle({ width: "0%" });
    }
  });
});
