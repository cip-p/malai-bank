import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { CategoryPill } from "./CategoryPill";

describe("CategoryPill", () => {
  it("renders the human label and tags the category", () => {
    render(<CategoryPill category="EATING_OUT" />);
    const pill = screen.getByTestId("transaction-category-pill");
    expect(pill).toHaveTextContent("Eating out");
    expect(pill).toHaveAttribute("data-category", "EATING_OUT");
  });

  it("title-cases an unknown category rather than breaking", () => {
    render(<CategoryPill category="SOME_NEW_THING" />);
    expect(screen.getByTestId("transaction-category-pill")).toHaveTextContent(
      "Some New Thing",
    );
  });
});
