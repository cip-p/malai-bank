import { describe, expect, it } from "vitest";
import { render, screen, within } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { MoveMoneyLayout } from "./MoveMoneyLayout";

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/move" element={<MoveMoneyLayout />}>
          <Route path="send" element={<div>send-outlet</div>} />
          <Route path="payees" element={<div>payees-outlet</div>} />
          <Route path="payees/new" element={<div>add-payee-outlet</div>} />
          <Route path="direct-debits" element={<div>dd-outlet</div>} />
          <Route path="standing-orders" element={<div>so-outlet</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe("MoveMoneyLayout", () => {
  it("renders the Move-money sub-nav with the four documented items in order", () => {
    renderAt("/move/send");
    const subnav = screen.getByTestId("move-subnav");
    const list = within(subnav).getByRole("list");
    const items = within(list).getAllByRole("listitem");
    expect(items).toHaveLength(4);
    expect(
      within(items[0]).getByRole("link", { name: "Send money" }),
    ).toHaveAttribute("href", "/move/send");
    expect(
      within(items[1]).getByRole("link", { name: "Payees" }),
    ).toHaveAttribute("href", "/move/payees");
    expect(
      within(items[2]).getByRole("link", { name: "Direct Debits" }),
    ).toHaveAttribute("href", "/move/direct-debits");
    expect(
      within(items[3]).getByRole("link", { name: "Standing Orders" }),
    ).toHaveAttribute("href", "/move/standing-orders");
  });

  it("gives the sub-nav an accessible label distinct from the primary nav", () => {
    renderAt("/move/send");
    expect(screen.getByTestId("move-subnav")).toHaveAttribute(
      "aria-label",
      "Move money",
    );
  });

  it("marks Send money aria-current=page on /move/send", () => {
    renderAt("/move/send");
    expect(screen.getByTestId("move-subnav-link-send")).toHaveAttribute(
      "aria-current",
      "page",
    );
    for (const id of [
      "move-subnav-link-payees",
      "move-subnav-link-direct-debits",
      "move-subnav-link-standing-orders",
    ]) {
      expect(screen.getByTestId(id)).not.toHaveAttribute("aria-current");
    }
  });

  it("marks Payees aria-current=page on /move/payees", () => {
    renderAt("/move/payees");
    expect(screen.getByTestId("move-subnav-link-payees")).toHaveAttribute(
      "aria-current",
      "page",
    );
  });

  it("keeps Payees aria-current=page on the nested /move/payees/new route", () => {
    renderAt("/move/payees/new");
    expect(screen.getByTestId("move-subnav-link-payees")).toHaveAttribute(
      "aria-current",
      "page",
    );
  });

  it("marks Direct Debits aria-current=page on /move/direct-debits", () => {
    renderAt("/move/direct-debits");
    expect(
      screen.getByTestId("move-subnav-link-direct-debits"),
    ).toHaveAttribute("aria-current", "page");
  });

  it("marks Standing Orders aria-current=page on /move/standing-orders", () => {
    renderAt("/move/standing-orders");
    expect(
      screen.getByTestId("move-subnav-link-standing-orders"),
    ).toHaveAttribute("aria-current", "page");
  });

  it("renders the routed Outlet beneath the sub-nav", () => {
    renderAt("/move/payees");
    expect(screen.getByText("payees-outlet")).toBeInTheDocument();
  });
});
