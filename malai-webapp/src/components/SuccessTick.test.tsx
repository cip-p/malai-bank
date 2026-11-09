import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { SuccessTick } from "./SuccessTick";

type MatchMediaImpl = (q: string) => MediaQueryList;

function mockMatchMedia(reduce: boolean) {
  const impl: MatchMediaImpl = (query) =>
    ({
      matches: query.includes("prefers-reduced-motion") ? reduce : false,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    }) as unknown as MediaQueryList;
  vi.stubGlobal("matchMedia", impl);
  Object.defineProperty(window, "matchMedia", {
    configurable: true,
    writable: true,
    value: impl,
  });
}

describe("SuccessTick", () => {
  beforeEach(() => {
    mockMatchMedia(false);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("renders the animated SVG under motion-safe", () => {
    render(<SuccessTick />);
    const svg = screen.getByTestId("success-tick");
    expect(svg).toBeInTheDocument();
    expect(svg.getAttribute("data-motion")).toBe("animated");
  });

  it("renders the static check under prefers-reduced-motion: reduce", () => {
    mockMatchMedia(true);
    render(<SuccessTick />);
    const svg = screen.getByTestId("success-tick");
    expect(svg).toBeInTheDocument();
    expect(svg.getAttribute("data-motion")).toBe("reduced");
  });

  it("is aria-hidden so screen readers ignore it as decoration", () => {
    render(<SuccessTick />);
    expect(screen.getByTestId("success-tick")).toHaveAttribute(
      "aria-hidden",
      "true",
    );
  });

  it("is aria-hidden under reduced motion too", () => {
    mockMatchMedia(true);
    render(<SuccessTick />);
    expect(screen.getByTestId("success-tick")).toHaveAttribute(
      "aria-hidden",
      "true",
    );
  });
});
