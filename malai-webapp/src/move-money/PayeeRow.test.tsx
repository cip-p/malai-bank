import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { PayeeResponse } from "@/api/generated/types.gen";
import { PayeeRow } from "./PayeeRow";

const BASE: PayeeResponse = {
  payeeId: "11111111-1111-1111-1111-111111111aaa",
  name: "Sarah Mitchell",
  sortCode: "12-34-56",
  accountNumber: "12345678",
  createdAt: "2026-05-19T10:00:00Z",
};

function asList(payee: PayeeResponse) {
  return (
    <ul>
      <PayeeRow payee={payee} />
    </ul>
  );
}

describe("PayeeRow", () => {
  it("uses the nickname as the primary label when present", () => {
    render(asList({ ...BASE, nickname: "Sis" }));
    expect(screen.getByTestId("payee-primary").textContent).toBe("Sis");
  });

  it("uses the name as the primary label when no nickname is present", () => {
    render(asList(BASE));
    expect(screen.getByTestId("payee-primary").textContent).toBe(
      "Sarah Mitchell",
    );
  });

  it("renders an avatar showing the first initial of the primary label", () => {
    render(asList({ ...BASE, nickname: "Sis" }));
    expect(screen.getByTestId("initial-avatar")).toHaveTextContent("S");
  });

  it("renders the sort code in full", () => {
    render(asList(BASE));
    expect(screen.getByTestId("payee-sort-code").textContent).toBe("12-34-56");
  });

  it("renders the account number in full", () => {
    render(asList(BASE));
    expect(screen.getByTestId("payee-account-number").textContent).toBe(
      "12345678",
    );
  });

  it("sets the row id to the payeeId so /move/payees#<id> anchors", () => {
    render(asList(BASE));
    expect(screen.getByTestId("payee-row").getAttribute("id")).toBe(
      BASE.payeeId,
    );
  });

  it("renders no delete affordance when onDelete is omitted", () => {
    render(asList(BASE));
    expect(screen.queryByTestId("payee-delete")).toBeNull();
  });

  it("renders a delete button when onDelete is provided", () => {
    const onDelete = vi.fn();
    render(
      <ul>
        <PayeeRow payee={BASE} onDelete={onDelete} />
      </ul>,
    );
    const btn = screen.getByTestId("payee-delete");
    expect(btn).toHaveAccessibleName(`Remove ${BASE.name}`);
  });

  it("fires onDelete with the payee when the delete button is clicked", async () => {
    const onDelete = vi.fn();
    render(
      <ul>
        <PayeeRow payee={BASE} onDelete={onDelete} />
      </ul>,
    );
    await userEvent.click(screen.getByTestId("payee-delete"));
    expect(onDelete).toHaveBeenCalledWith(BASE);
  });

  it("delete button is keyboard-activatable via Enter", async () => {
    const onDelete = vi.fn();
    render(
      <ul>
        <PayeeRow payee={BASE} onDelete={onDelete} />
      </ul>,
    );
    const btn = screen.getByTestId("payee-delete");
    btn.focus();
    await userEvent.keyboard("{Enter}");
    expect(onDelete).toHaveBeenCalledWith(BASE);
  });

  it("uses the nickname in the accessible label when present", () => {
    const onDelete = vi.fn();
    render(
      <ul>
        <PayeeRow payee={{ ...BASE, nickname: "Sis" }} onDelete={onDelete} />
      </ul>,
    );
    expect(screen.getByTestId("payee-delete")).toHaveAccessibleName(
      "Remove Sis",
    );
  });
});
