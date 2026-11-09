import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import type {
  InstrumentStatus,
  PayeeResponse,
  StandingOrderResponse,
} from "@/api/generated/types.gen";
import { DeletePayeeDialog } from "./DeletePayeeDialog";

const SARAH: PayeeResponse = {
  payeeId: "11111111-1111-1111-1111-111111111aaa",
  name: "Sarah Mitchell",
  sortCode: "12-34-56",
  accountNumber: "12345678",
  createdAt: "2026-05-19T10:00:00Z",
};

function makeSo(
  payeeId: string,
  status: InstrumentStatus = "ACTIVE",
  standingOrderId: string = "so-" + standingOrderIdCounter++,
): StandingOrderResponse {
  return {
    standingOrderId,
    accountId: "acc-1",
    payeeId,
    payeeName: "Sarah Mitchell",
    payeeSortCode: "12-34-56",
    payeeAccountNumber: "12345678",
    amountMinorUnits: 1000,
    currency: "GBP",
    frequency: "MONTHLY",
    status,
    startDate: "2026-01-15",
  };
}

let standingOrderIdCounter = 1;

function renderDialog(props: Partial<Parameters<typeof DeletePayeeDialog>[0]>) {
  return render(
    <MemoryRouter>
      <DeletePayeeDialog
        open
        payee={SARAH}
        linkedStandingOrders={[]}
        onCancel={() => {}}
        onConfirm={() => {}}
        {...props}
      />
    </MemoryRouter>,
  );
}

describe("DeletePayeeDialog", () => {
  beforeEach(() => {
    standingOrderIdCounter = 1;
  });

  it("renders the 0-linked confirm copy when no SOs reference the payee", () => {
    renderDialog({ linkedStandingOrders: [] });
    expect(
      screen.getByTestId("delete-payee-description").textContent,
    ).toContain("Remove Sarah Mitchell? You can add them again later.");
    expect(screen.getByTestId("delete-payee-confirm")).toHaveTextContent(
      "Remove",
    );
  });

  it("still confirms when only CANCELLED SOs reference the payee", () => {
    renderDialog({
      linkedStandingOrders: [
        makeSo(SARAH.payeeId, "CANCELLED"),
        makeSo(SARAH.payeeId, "CANCELLED"),
      ],
    });
    expect(
      screen.getByTestId("delete-payee-description").textContent,
    ).toContain("Remove Sarah Mitchell? You can add them again later.");
    expect(screen.getByTestId("delete-payee-confirm")).toBeInTheDocument();
    expect(
      screen.queryByTestId("delete-payee-view-standing-orders"),
    ).toBeNull();
  });

  it("blocks with a singular explanation when one ACTIVE SO references the payee", () => {
    renderDialog({ linkedStandingOrders: [makeSo(SARAH.payeeId, "ACTIVE")] });
    expect(
      screen.getByTestId("delete-payee-description").textContent,
    ).toContain(
      "You can’t remove Sarah Mitchell while a standing order still pays them. Cancel the standing order first, then remove the payee.",
    );
  });

  it("blocks with a counted explanation when multiple ACTIVE SOs reference the payee", () => {
    renderDialog({
      linkedStandingOrders: [
        makeSo(SARAH.payeeId, "ACTIVE"),
        makeSo(SARAH.payeeId, "ACTIVE"),
        makeSo(SARAH.payeeId, "ACTIVE"),
      ],
    });
    expect(
      screen.getByTestId("delete-payee-description").textContent,
    ).toContain(
      "You can’t remove Sarah Mitchell while 3 standing orders still pay them. Cancel them first, then remove the payee.",
    );
  });

  it("blocks when at least one linked SO is ACTIVE even if others are cancelled", () => {
    renderDialog({
      linkedStandingOrders: [
        makeSo(SARAH.payeeId, "CANCELLED"),
        makeSo(SARAH.payeeId, "ACTIVE"),
      ],
    });
    expect(
      screen.getByTestId("delete-payee-description").textContent,
    ).toContain(
      "You can’t remove Sarah Mitchell while a standing order still pays them.",
    );
  });

  it("blocked variant offers a View standing orders link and no Remove button", () => {
    renderDialog({ linkedStandingOrders: [makeSo(SARAH.payeeId, "ACTIVE")] });
    const link = screen.getByTestId("delete-payee-view-standing-orders");
    expect(link).toHaveTextContent("View standing orders");
    expect(link.getAttribute("href")).toBe("/move/standing-orders");
    expect(screen.queryByTestId("delete-payee-confirm")).toBeNull();
  });

  it("blocked variant Close fires onCancel", async () => {
    const onCancel = vi.fn();
    renderDialog({
      linkedStandingOrders: [makeSo(SARAH.payeeId, "ACTIVE")],
      onCancel,
    });
    await userEvent.click(screen.getByTestId("delete-payee-cancel"));
    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it("renders Cancel and Remove buttons in confirm mode", () => {
    renderDialog({ linkedStandingOrders: [] });
    expect(screen.getByTestId("delete-payee-cancel")).toHaveTextContent(
      "Cancel",
    );
    expect(screen.getByTestId("delete-payee-confirm")).toHaveTextContent(
      "Remove",
    );
  });

  it("fires onCancel when Cancel is clicked", async () => {
    const onCancel = vi.fn();
    renderDialog({ linkedStandingOrders: [], onCancel });
    await userEvent.click(screen.getByTestId("delete-payee-cancel"));
    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it("fires onConfirm when Remove is clicked", async () => {
    const onConfirm = vi.fn();
    renderDialog({ linkedStandingOrders: [], onConfirm });
    await userEvent.click(screen.getByTestId("delete-payee-confirm"));
    expect(onConfirm).toHaveBeenCalledTimes(1);
  });

  it("does not fire onCancel when Remove is clicked", async () => {
    const onCancel = vi.fn();
    const onConfirm = vi.fn();
    renderDialog({ linkedStandingOrders: [], onCancel, onConfirm });
    await userEvent.click(screen.getByTestId("delete-payee-confirm"));
    expect(onConfirm).toHaveBeenCalledTimes(1);
    expect(onCancel).not.toHaveBeenCalled();
  });

  it("uses the nickname as the display name in confirm mode", () => {
    renderDialog({
      payee: { ...SARAH, nickname: "Sis" },
      linkedStandingOrders: [],
    });
    expect(
      screen.getByTestId("delete-payee-description").textContent,
    ).toContain("Remove Sis? You can add them again later.");
  });

  it("uses the nickname as the display name in blocked mode", () => {
    renderDialog({
      payee: { ...SARAH, nickname: "Sis" },
      linkedStandingOrders: [makeSo(SARAH.payeeId, "ACTIVE")],
    });
    expect(
      screen.getByTestId("delete-payee-description").textContent,
    ).toContain("You can’t remove Sis while a standing order still pays them.");
  });

  it("does not render anything when open is false", () => {
    renderDialog({ open: false, linkedStandingOrders: [] });
    expect(screen.queryByTestId("delete-payee-description")).toBeNull();
  });
});
