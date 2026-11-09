import { afterEach, describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { ScrollRegion } from "./ScrollRegion";

function pretendContentSize(scroll: number, client: number) {
  Object.defineProperty(HTMLElement.prototype, "scrollWidth", {
    configurable: true,
    get: () => scroll,
  });
  Object.defineProperty(HTMLElement.prototype, "clientWidth", {
    configurable: true,
    get: () => client,
  });
}

describe("ScrollRegion", () => {
  afterEach(() => {
    pretendContentSize(0, 0);
  });

  it("is a plain container while its content fits", () => {
    pretendContentSize(200, 200);
    render(
      <ScrollRegion label="Model decisions" testId="region">
        <table />
      </ScrollRegion>,
    );
    const el = screen.getByTestId("region");
    expect(el).not.toHaveAttribute("tabindex");
    expect(el).not.toHaveAttribute("role");
    expect(screen.queryByRole("region")).not.toBeInTheDocument();
  });

  it("becomes a focusable, labelled region once its content overflows", () => {
    pretendContentSize(900, 300);
    render(
      <ScrollRegion label="Model decisions" testId="region">
        <table />
      </ScrollRegion>,
    );
    const region = screen.getByRole("region", { name: "Model decisions" });
    expect(region).toHaveAttribute("tabindex", "0");
  });
});
