import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import type {
  AccountResponse,
  StandingOrderResponse,
} from "@/api/generated/types.gen";
import { StandingOrdersPage } from "./StandingOrdersPage";

const useStandingOrdersMock = vi.fn();
const useAccountsMock = vi.fn();
const usePayeesMock = vi.fn();
const cancelStandingOrderMock = vi.fn();
vi.mock("@/move-money/useStandingOrders", () => ({
  useStandingOrders: () => useStandingOrdersMock(),
}));
vi.mock("@/accounts/useAccounts", () => ({
  useAccounts: () => useAccountsMock(),
}));
vi.mock("@/move-money/usePayees", () => ({
  usePayees: () => usePayeesMock(),
}));
vi.mock("@/move-money/useCancelStandingOrder", () => ({
  useCancelStandingOrder: () => ({
    cancelStandingOrder: cancelStandingOrderMock,
    isPending: false,
  }),
}));

function makeAccount(over: Partial<AccountResponse> = {}): AccountResponse {
  return {
    accountUid: "acc-1",
    accountType: "CURRENT",
    defaultCategory: "PERSONAL",
    currency: "GBP",
    createdAt: "2024-01-01T00:00:00Z",
    name: "Main",
    ...over,
  };
}

function makeSo(over: Partial<StandingOrderResponse>): StandingOrderResponse {
  return {
    standingOrderId: "so-1",
    accountId: "acc-1",
    payeeName: "Landlord",
    payeeSortCode: "12-34-56",
    payeeAccountNumber: "87654321",
    amountMinorUnits: 150_000,
    currency: "GBP",
    frequency: "MONTHLY",
    status: "ACTIVE",
    startDate: "2026-01-15",
    nextPaymentAt: "2026-05-23T12:00:00Z",
    ...over,
  };
}

function renderPage() {
  return render(
    <MemoryRouter>
      <StandingOrdersPage now={new Date("2026-05-20T12:00:00Z")} />
    </MemoryRouter>,
  );
}

describe("StandingOrdersPage", () => {
  beforeEach(() => {
    useStandingOrdersMock.mockReset();
    useAccountsMock.mockReset();
    usePayeesMock.mockReset();
    cancelStandingOrderMock.mockReset();
    useAccountsMock.mockReturnValue({ accounts: [makeAccount()] });
    usePayeesMock.mockReturnValue({ payees: [] });
  });

  it("renders the h1 'Standing Orders' and focuses it on mount", () => {
    useStandingOrdersMock.mockReturnValue({
      standingOrders: [],
      isPending: false,
      isError: false,
    });
    renderPage();
    const h1 = screen.getByRole("heading", { level: 1 });
    expect(h1).toHaveTextContent("Standing Orders");
    expect(h1).toHaveFocus();
  });

  it("renders a loading skeleton while pending", () => {
    useStandingOrdersMock.mockReturnValue({
      standingOrders: null,
      isPending: true,
      isError: false,
    });
    renderPage();
    expect(screen.getByTestId("so-skeleton")).toBeInTheDocument();
  });

  it("renders an error caption when the request fails", () => {
    useStandingOrdersMock.mockReturnValue({
      standingOrders: null,
      isPending: false,
      isError: true,
    });
    renderPage();
    expect(screen.getByTestId("so-error")).toHaveTextContent(
      "Couldn’t load standing orders",
    );
  });

  it("renders the designed empty state with a headline, body and a create CTA", () => {
    useStandingOrdersMock.mockReturnValue({
      standingOrders: [],
      isPending: false,
      isError: false,
    });
    renderPage();
    const empty = screen.getByTestId("so-empty");
    expect(empty).toHaveTextContent("No active standing orders");
    expect(empty).toHaveTextContent(
      "A standing order is a recurring payment you set up from your account on a fixed schedule.",
    );
    expect(screen.getByTestId("so-empty-add")).toHaveAttribute(
      "href",
      "/move/standing-orders/new",
    );
  });

  it("renders a header 'New standing order' CTA pointing at the wizard", () => {
    useStandingOrdersMock.mockReturnValue({
      standingOrders: [makeSo({})],
      isPending: false,
      isError: false,
    });
    renderPage();
    expect(screen.getByTestId("so-header-add")).toHaveAttribute(
      "href",
      "/move/standing-orders/new",
    );
  });

  it("renders standing orders sorted by nextPaymentAt ascending with nulls bucketed", () => {
    useStandingOrdersMock.mockReturnValue({
      standingOrders: [
        makeSo({
          standingOrderId: "so-2",
          payeeName: "Gym",
          nextPaymentAt: "2026-06-01T12:00:00Z",
        }),
        makeSo({
          standingOrderId: "so-3",
          payeeName: "Charity",
          nextPaymentAt: undefined,
        }),
        makeSo({
          standingOrderId: "so-1",
          payeeName: "Landlord",
          nextPaymentAt: "2026-05-23T12:00:00Z",
        }),
      ],
      isPending: false,
      isError: false,
    });
    renderPage();
    const rows = within(screen.getByTestId("so-list")).getAllByTestId("so-row");
    expect(rows).toHaveLength(3);
    expect(within(rows[0]).getByTestId("so-primary")).toHaveTextContent(
      "Landlord",
    );
    expect(within(rows[1]).getByTestId("so-primary")).toHaveTextContent("Gym");
    expect(within(rows[2]).getByTestId("so-primary")).toHaveTextContent(
      "Charity",
    );
  });

  it("renders payeeName as plain text when usePayees does not contain the SO's payeeId", () => {
    useStandingOrdersMock.mockReturnValue({
      standingOrders: [makeSo({ payeeId: "payee-99" })],
      isPending: false,
      isError: false,
    });
    usePayeesMock.mockReturnValue({
      payees: [
        {
          payeeId: "payee-other",
          name: "Other",
          sortCode: "11-11-11",
          accountNumber: "11111111",
          createdAt: "2026-01-01T00:00:00Z",
        },
      ],
    });
    renderPage();
    const primary = screen.getByTestId("so-primary");
    expect(within(primary).queryByRole("link")).toBeNull();
    expect(primary).toHaveTextContent("Landlord");
  });

  it("renders payeeName as plain text when SO has no payeeId at all", () => {
    useStandingOrdersMock.mockReturnValue({
      standingOrders: [makeSo({})],
      isPending: false,
      isError: false,
    });
    renderPage();
    const primary = screen.getByTestId("so-primary");
    expect(within(primary).queryByRole("link")).toBeNull();
    expect(primary).toHaveTextContent("Landlord");
  });

  it("renders payeeName as a link to /move/payees#<payeeId> when usePayees contains that payee", () => {
    useStandingOrdersMock.mockReturnValue({
      standingOrders: [makeSo({ payeeId: "payee-99" })],
      isPending: false,
      isError: false,
    });
    usePayeesMock.mockReturnValue({
      payees: [
        {
          payeeId: "payee-99",
          name: "Landlord",
          sortCode: "12-34-56",
          accountNumber: "87654321",
          createdAt: "2026-01-01T00:00:00Z",
        },
      ],
    });
    renderPage();
    const link = within(screen.getByTestId("so-primary")).getByRole("link", {
      name: "Landlord",
    });
    expect(link.getAttribute("href")).toBe("/move/payees#payee-99");
  });

  it("hides CANCELLED standing orders from the list", () => {
    useStandingOrdersMock.mockReturnValue({
      standingOrders: [
        makeSo({ standingOrderId: "so-1", payeeName: "Landlord" }),
        makeSo({
          standingOrderId: "so-2",
          payeeName: "Old gym",
          status: "CANCELLED",
          cancelledAt: "2026-05-01T09:00:00Z",
        }),
      ],
      isPending: false,
      isError: false,
    });
    renderPage();
    const rows = within(screen.getByTestId("so-list")).getAllByTestId("so-row");
    expect(rows).toHaveLength(1);
    expect(within(rows[0]).getByTestId("so-primary")).toHaveTextContent(
      "Landlord",
    );
  });

  it("shows the empty state when every standing order is cancelled", () => {
    useStandingOrdersMock.mockReturnValue({
      standingOrders: [
        makeSo({
          status: "CANCELLED",
          cancelledAt: "2026-05-01T09:00:00Z",
        }),
      ],
      isPending: false,
      isError: false,
    });
    renderPage();
    expect(screen.getByTestId("so-empty")).toBeInTheDocument();
  });

  it("opens the confirmation dialog from a row's cancel button and cancels on confirm", async () => {
    useStandingOrdersMock.mockReturnValue({
      standingOrders: [makeSo({ standingOrderId: "so-9" })],
      isPending: false,
      isError: false,
    });
    renderPage();

    expect(screen.queryByTestId("cancel-so-dialog")).toBeNull();
    await userEvent.click(
      screen.getByRole("button", { name: "Cancel standing order to Landlord" }),
    );
    expect(screen.getByTestId("cancel-so-dialog")).toBeInTheDocument();

    await userEvent.click(screen.getByTestId("cancel-so-confirm"));
    expect(cancelStandingOrderMock).toHaveBeenCalledWith("so-9");
    expect(screen.queryByTestId("cancel-so-dialog")).toBeNull();
  });

  it("keeps the standing order when the dialog is dismissed", async () => {
    useStandingOrdersMock.mockReturnValue({
      standingOrders: [makeSo({})],
      isPending: false,
      isError: false,
    });
    renderPage();

    await userEvent.click(
      screen.getByRole("button", { name: "Cancel standing order to Landlord" }),
    );
    await userEvent.click(screen.getByTestId("cancel-so-keep"));
    expect(cancelStandingOrderMock).not.toHaveBeenCalled();
    expect(screen.queryByTestId("cancel-so-dialog")).toBeNull();
  });

  it("omits the account chip when the holder has a single Account", () => {
    useAccountsMock.mockReturnValue({ accounts: [makeAccount()] });
    useStandingOrdersMock.mockReturnValue({
      standingOrders: [makeSo({})],
      isPending: false,
      isError: false,
    });
    renderPage();
    expect(screen.queryByTestId("so-account-chip")).toBeNull();
  });

  it("renders the account chip with the account name when the holder has ≥2 Accounts", () => {
    useAccountsMock.mockReturnValue({
      accounts: [
        makeAccount({ accountUid: "acc-1", name: "Main" }),
        makeAccount({ accountUid: "acc-2", name: "Savings" }),
      ],
    });
    useStandingOrdersMock.mockReturnValue({
      standingOrders: [makeSo({ accountId: "acc-2" })],
      isPending: false,
      isError: false,
    });
    renderPage();
    expect(screen.getByTestId("so-account-chip")).toHaveTextContent("Savings");
  });
});
