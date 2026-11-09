import { describe, expect, it } from "vitest";
import { ALL_CATEGORIES, CATEGORY_LABELS, categoryLabel } from "./categories";

describe("categoryLabel", () => {
  it("returns the human label for each known category", () => {
    expect(categoryLabel("EATING_OUT")).toBe("Eating out");
    expect(categoryLabel("GROCERIES")).toBe("Groceries");
    expect(categoryLabel("SUBSCRIPTIONS")).toBe("Subscriptions");
  });

  it("title-cases an unknown category", () => {
    expect(categoryLabel("SOME_NEW_THING")).toBe("Some New Thing");
  });

  it("falls back to Uncategorised for undefined", () => {
    expect(categoryLabel(undefined)).toBe("Uncategorised");
  });
});

describe("ALL_CATEGORIES", () => {
  it("covers every label", () => {
    expect(ALL_CATEGORIES.length).toBe(Object.keys(CATEGORY_LABELS).length);
  });
});
