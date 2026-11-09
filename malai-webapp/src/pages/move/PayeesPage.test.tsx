import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import type {
  PayeeResponse,
  StandingOrderResponse,
} from "@/api/generated/types.gen";
import type { UsePayeesResult } from "@/move-money/usePayees";
import type { UseStandingOrdersResult } from "@/move-money/useStandingOrders";
import type { UseDeletePayeeResult } from "@/move-money/useDeletePayee";

vi.mock("@/move-money/usePayees", () => ({
  usePayees: vi.fn(),
}));
vi.mock("@/move-money/useStandingOrders", () => ({
  useStandingOrders: vi.fn(),
}));
vi.mock("@/move-money/useDeletePayee", () => ({
  useDeletePayee: vi.fn(),
}));

const usePayeesHook = await import("@/move-money/usePayees");
const useStandingOrdersHook = await import("@/move-money/useStandingOrders");
const useDeletePayeeHook = await import("@/move-money/useDeletePayee");
const usePayeesMock = usePayeesHook.usePayees as unknown as ReturnType<
  typeof vi.fn<() => UsePayeesResult>
>;
const useStandingOrdersMock =
  useStandingOrdersHook.useStandingOrders as unknown as ReturnType<
    typeof vi.fn<() => UseStandingOrdersResult>
  >;
const useDeletePayeeMock =
  useDeletePayeeHook.useDeletePayee as unknown as ReturnType<
    typeof vi.fn<() => UseDeletePayeeResult>
  >;

import { PayeesPage } from "./PayeesPage";

function wrap(ui: React.ReactNode) {
  return <MemoryRouter>{ui}</MemoryRouter>;
}

const SARAH: PayeeResponse = {
  payeeId: "11111111-1111-1111-1111-1111111111aa",
  name: "Sarah Mitchell",
  sortCode: "12-34-56",
  accountNumber: "12345678",
  createdAt: "2026-05-19T10:00:00Z",
};

const THAMES: PayeeResponse = {
  payeeId: "22222222-2222-2222-2222-2222222222bb",
  name: "Thames Water",
  sortCode: "98-76-54",
  accountNumber: "87654321",
  createdAt: "2026-05-18T10:00:00Z",
};

const BABA: PayeeResponse = {
  payeeId: "33333333-3333-3333-3333-3333333333cc",
  name: "Adam Adams",
  nickname: "Baba",
  sortCode: "11-11-11",
  accountNumber: "11111111",
  createdAt: "2026-05-17T10:00:00Z",
};

const deletePayeeFn = vi.fn();

const SO_TO_SARAH: StandingOrderResponse = {
  standingOrderId: "so-1",
  accountId: "acc-1",
  payeeId: SARAH.payeeId,
  payeeName: SARAH.name,
  payeeSortCode: SARAH.sortCode,
  payeeAccountNumber: SARAH.accountNumber,
  amountMinorUnits: 1000,
  currency: "GBP",
  frequency: "MONTHLY",
  status: "ACTIVE",
  startDate: "2026-01-15",
};

const SO_TO_SARAH_2: StandingOrderResponse = {
  ...SO_TO_SARAH,
  standingOrderId: "so-2",
};

function noStandingOrders(): UseStandingOrdersResult {
  return {
    standingOrders: [],
    isPending: false,
    isError: false,
    error: null,
  };
}

describe("PayeesPage", () => {
  beforeEach(() => {
    usePayeesMock.mockReset();
    useStandingOrdersMock.mockReset();
    useStandingOrdersMock.mockReturnValue(noStandingOrders());
    deletePayeeFn.mockReset();
    useDeletePayeeMock.mockReset();
    useDeletePayeeMock.mockReturnValue({
      deletePayee: deletePayeeFn,
      isPending: false,
    });
  });

  it("focuses the heading on mount", () => {
    usePayeesMock.mockReturnValue({
      payees: [],
      isPending: false,
      isError: false,
      error: null,
    });
    render(wrap(<PayeesPage />));
    expect(screen.getByRole("heading", { name: "Payees" })).toHaveFocus();
  });

  it("renders the loading skeleton while pending", () => {
    usePayeesMock.mockReturnValue({
      payees: null,
      isPending: true,
      isError: false,
      error: null,
    });
    render(wrap(<PayeesPage />));
    expect(screen.getByTestId("payees-skeleton")).toBeInTheDocument();
  });

  it("renders the error caption on error", () => {
    usePayeesMock.mockReturnValue({
      payees: null,
      isPending: false,
      isError: true,
      error: new Error("boom"),
    });
    render(wrap(<PayeesPage />));
    expect(screen.getByTestId("payees-error")).toHaveTextContent(
      "Couldn’t load payees",
    );
  });

  it("renders the designed empty state with a headline, body, and an Add-payee CTA", () => {
    usePayeesMock.mockReturnValue({
      payees: [],
      isPending: false,
      isError: false,
      error: null,
    });
    render(wrap(<PayeesPage />));
    const empty = screen.getByTestId("payees-empty");
    expect(empty).toHaveTextContent("No saved payees yet");
    expect(empty).toHaveTextContent(
      "Save someone you pay regularly so you don’t have to re-enter their details next time.",
    );
    const emptyCta = screen.getByTestId("payees-empty-add");
    expect(emptyCta).toHaveTextContent("Add payee");
    expect(emptyCta.getAttribute("href")).toBe("/move/payees/new");
  });

  it("always renders the header Add-payee button regardless of list emptiness", () => {
    usePayeesMock.mockReturnValue({
      payees: [],
      isPending: false,
      isError: false,
      error: null,
    });
    render(wrap(<PayeesPage />));
    const headerCta = screen.getByTestId("payees-header-add");
    expect(headerCta).toHaveTextContent("Add payee");
    expect(headerCta.getAttribute("href")).toBe("/move/payees/new");
  });

  it("renders the list sorted alphabetically by nickname-or-name", () => {
    usePayeesMock.mockReturnValue({
      payees: [SARAH, THAMES, BABA],
      isPending: false,
      isError: false,
      error: null,
    });
    render(wrap(<PayeesPage />));
    const primaries = screen.getAllByTestId("payee-primary");
    expect(primaries.map((p) => p.textContent)).toEqual([
      "Baba",
      "Sarah Mitchell",
      "Thames Water",
    ]);
  });

  it("renders a delete button per payee row", () => {
    usePayeesMock.mockReturnValue({
      payees: [SARAH, THAMES],
      isPending: false,
      isError: false,
      error: null,
    });
    render(wrap(<PayeesPage />));
    expect(screen.getAllByTestId("payee-delete")).toHaveLength(2);
  });

  it("clicking delete opens the dialog with the 0-linked copy when no SOs reference the payee", async () => {
    usePayeesMock.mockReturnValue({
      payees: [SARAH],
      isPending: false,
      isError: false,
      error: null,
    });
    render(wrap(<PayeesPage />));
    await userEvent.click(screen.getByTestId("payee-delete"));
    const description = screen.getByTestId("delete-payee-description");
    expect(description.textContent).toContain(
      "Remove Sarah Mitchell? You can add them again later.",
    );
  });

  it("clicking delete blocks with a View standing orders link when an ACTIVE SO references the payee", async () => {
    usePayeesMock.mockReturnValue({
      payees: [SARAH],
      isPending: false,
      isError: false,
      error: null,
    });
    useStandingOrdersMock.mockReturnValue({
      standingOrders: [SO_TO_SARAH, SO_TO_SARAH_2],
      isPending: false,
      isError: false,
      error: null,
    });
    render(wrap(<PayeesPage />));
    await userEvent.click(screen.getByTestId("payee-delete"));
    const description = screen.getByTestId("delete-payee-description");
    expect(description.textContent).toContain(
      "You can’t remove Sarah Mitchell while 2 standing orders still pay them. Cancel them first, then remove the payee.",
    );
    const viewLink = screen.getByTestId("delete-payee-view-standing-orders");
    expect(viewLink.getAttribute("href")).toBe("/move/standing-orders");
    expect(screen.queryByTestId("delete-payee-confirm")).toBeNull();
  });

  it("clicking delete confirms normally when only a CANCELLED SO references the payee", async () => {
    usePayeesMock.mockReturnValue({
      payees: [SARAH],
      isPending: false,
      isError: false,
      error: null,
    });
    useStandingOrdersMock.mockReturnValue({
      standingOrders: [{ ...SO_TO_SARAH, status: "CANCELLED" }],
      isPending: false,
      isError: false,
      error: null,
    });
    render(wrap(<PayeesPage />));
    await userEvent.click(screen.getByTestId("payee-delete"));
    expect(
      screen.getByTestId("delete-payee-description").textContent,
    ).toContain("Remove Sarah Mitchell? You can add them again later.");
    await userEvent.click(screen.getByTestId("delete-payee-confirm"));
    expect(deletePayeeFn).toHaveBeenCalledWith(SARAH.payeeId);
  });

  it("Cancel dismisses the dialog without firing deletePayee", async () => {
    usePayeesMock.mockReturnValue({
      payees: [SARAH],
      isPending: false,
      isError: false,
      error: null,
    });
    render(wrap(<PayeesPage />));
    await userEvent.click(screen.getByTestId("payee-delete"));
    await userEvent.click(screen.getByTestId("delete-payee-cancel"));
    expect(screen.queryByTestId("delete-payee-description")).toBeNull();
    expect(deletePayeeFn).not.toHaveBeenCalled();
  });

  it("Remove fires deletePayee with the payee id and closes the dialog", async () => {
    usePayeesMock.mockReturnValue({
      payees: [SARAH],
      isPending: false,
      isError: false,
      error: null,
    });
    render(wrap(<PayeesPage />));
    await userEvent.click(screen.getByTestId("payee-delete"));
    await userEvent.click(screen.getByTestId("delete-payee-confirm"));
    expect(deletePayeeFn).toHaveBeenCalledWith(SARAH.payeeId);
    expect(screen.queryByTestId("delete-payee-description")).toBeNull();
  });
});
