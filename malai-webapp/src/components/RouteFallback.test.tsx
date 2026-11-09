import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { RouteFallback } from "./RouteFallback";

describe("RouteFallback", () => {
  it("marks itself busy and names the wait for assistive tech", () => {
    render(<RouteFallback />);
    const fallback = screen.getByTestId("route-fallback");
    expect(fallback).toHaveAttribute("aria-busy", "true");
    expect(screen.getByText("Loading")).toHaveClass("sr-only");
  });

  it("accepts a container width override for the wider staff shells", () => {
    render(<RouteFallback className="max-w-6xl" />);
    expect(screen.getByTestId("route-fallback")).toHaveClass("max-w-6xl");
  });
});
