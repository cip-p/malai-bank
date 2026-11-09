import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { act, render, screen } from "@testing-library/react";
import { HeaderWordmark } from "./HeaderWordmark";
import { ThemeProvider, useTheme, type ThemeId } from "./useTheme";

beforeEach(() => {
  window.localStorage.clear();
  document.documentElement.removeAttribute("data-theme");
});

afterEach(() => {
  window.localStorage.clear();
  document.documentElement.removeAttribute("data-theme");
});

function ThemeSwap({ next }: { next: ThemeId }) {
  const { setTheme } = useTheme();
  return (
    <button type="button" onClick={() => setTheme(next)} data-testid="swap">
      swap
    </button>
  );
}

function renderWithProvider() {
  return render(
    <ThemeProvider>
      <HeaderWordmark />
      <ThemeSwap next="halcyon" />
    </ThemeProvider>,
  );
}

describe("HeaderWordmark", () => {
  it("renders the malai wordmark by default", () => {
    renderWithProvider();
    const wordmark = screen.getByTestId("header-wordmark");
    expect(wordmark).toHaveAttribute("data-theme", "malai");
    expect(wordmark).toHaveAttribute("aria-label", "malai");
    expect(wordmark.tagName.toLowerCase()).toBe("svg");
  });

  it("uses role='img' so screen readers announce the brand", () => {
    renderWithProvider();
    expect(screen.getByTestId("header-wordmark")).toHaveAttribute(
      "role",
      "img",
    );
  });

  it("swaps the rendered wordmark when the active theme changes", () => {
    renderWithProvider();
    expect(screen.getByTestId("header-wordmark")).toHaveAttribute(
      "data-theme",
      "malai",
    );
    act(() => {
      screen.getByTestId("swap").click();
    });
    const next = screen.getByTestId("header-wordmark");
    expect(next).toHaveAttribute("data-theme", "halcyon");
    expect(next).toHaveAttribute("aria-label", "halcyon");
  });

  it("uses currentColor for its painted strokes/fills so a parent text-primary tints it in-brand", () => {
    renderWithProvider();
    const wordmark = screen.getByTestId("header-wordmark");
    const painted = wordmark.querySelectorAll("[fill],[stroke]");
    expect(painted.length).toBeGreaterThan(0);
    for (const node of Array.from(painted)) {
      const fill = node.getAttribute("fill");
      const stroke = node.getAttribute("stroke");
      if (fill !== null) expect(["currentColor", "none"]).toContain(fill);
      if (stroke !== null) expect(["currentColor", "none"]).toContain(stroke);
    }
  });
});
