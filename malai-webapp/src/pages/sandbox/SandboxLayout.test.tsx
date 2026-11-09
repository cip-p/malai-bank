import { describe, expect, it } from "vitest";
import { render, screen, within } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { SandboxLayout } from "./SandboxLayout";

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/sandbox" element={<SandboxLayout />}>
          <Route
            path="counterparties"
            element={<div data-testid="cp-stub">cp</div>}
          />
          <Route
            path="inbound-payments"
            element={<div data-testid="ip-stub">ip</div>}
          />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe("SandboxLayout", () => {
  it("renders both sub-nav links", () => {
    renderAt("/sandbox/counterparties");
    const nav = screen.getByTestId("sandbox-subnav");
    expect(
      within(nav).getByRole("link", { name: "Counterparties" }),
    ).toHaveAttribute("href", "/sandbox/counterparties");
    expect(
      within(nav).getByRole("link", { name: "Inbound payments" }),
    ).toHaveAttribute("href", "/sandbox/inbound-payments");
  });

  it("marks the Counterparties link aria-current=page on /sandbox/counterparties", () => {
    renderAt("/sandbox/counterparties");
    expect(
      screen.getByTestId("sandbox-subnav-link-counterparties"),
    ).toHaveAttribute("aria-current", "page");
    expect(
      screen.getByTestId("sandbox-subnav-link-inbound-payments"),
    ).not.toHaveAttribute("aria-current");
  });

  it("marks the Inbound payments link aria-current=page on /sandbox/inbound-payments", () => {
    renderAt("/sandbox/inbound-payments");
    expect(
      screen.getByTestId("sandbox-subnav-link-inbound-payments"),
    ).toHaveAttribute("aria-current", "page");
    expect(
      screen.getByTestId("sandbox-subnav-link-counterparties"),
    ).not.toHaveAttribute("aria-current");
  });

  it("routes /sandbox/inbound-payments through the layout", () => {
    renderAt("/sandbox/inbound-payments");
    expect(screen.getByTestId("ip-stub")).toBeVisible();
  });
});
