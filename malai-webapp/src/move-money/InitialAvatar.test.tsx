import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { InitialAvatar } from "./InitialAvatar";

describe("InitialAvatar", () => {
  it("renders the first character of the label in upper case", () => {
    render(<InitialAvatar label="sarah" />);
    expect(screen.getByTestId("initial-avatar")).toHaveTextContent("S");
  });

  it("preserves an already-upper-case first character", () => {
    render(<InitialAvatar label="Thames Water" />);
    expect(screen.getByTestId("initial-avatar")).toHaveTextContent("T");
  });

  it("trims leading whitespace before picking the initial", () => {
    render(<InitialAvatar label="   Adam" />);
    expect(screen.getByTestId("initial-avatar")).toHaveTextContent("A");
  });

  it("falls back to '?' for an all-whitespace label", () => {
    render(<InitialAvatar label="   " />);
    expect(screen.getByTestId("initial-avatar")).toHaveTextContent("?");
  });

  it("falls back to '?' for an empty label", () => {
    render(<InitialAvatar label="" />);
    expect(screen.getByTestId("initial-avatar")).toHaveTextContent("?");
  });

  it("is decorative (aria-hidden)", () => {
    render(<InitialAvatar label="Sarah" />);
    expect(screen.getByTestId("initial-avatar")).toHaveAttribute(
      "aria-hidden",
      "true",
    );
  });
});
