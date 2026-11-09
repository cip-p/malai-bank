import { describe, expect, it } from "vitest";
import { render } from "@testing-library/react";
import { useFocusOnMount } from "./useFocusOnMount";

function HeadingHarness() {
  const ref = useFocusOnMount<HTMLHeadingElement>();
  return (
    <h1 ref={ref} tabIndex={-1}>
      Page title
    </h1>
  );
}

function NoTabIndexHarness() {
  const ref = useFocusOnMount<HTMLHeadingElement>();
  return <h1 ref={ref}>Page title</h1>;
}

describe("useFocusOnMount", () => {
  it("focuses the bound element on mount when it can receive focus", () => {
    const { getByRole } = render(<HeadingHarness />);
    expect(getByRole("heading", { level: 1 })).toBe(document.activeElement);
  });

  it("does nothing when the element cannot receive focus", () => {
    const { getByRole } = render(<NoTabIndexHarness />);
    expect(getByRole("heading", { level: 1 })).not.toBe(document.activeElement);
  });
});
