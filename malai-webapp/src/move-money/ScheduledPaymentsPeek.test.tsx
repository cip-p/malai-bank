import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import type {
  DirectDebitResponse,
  StandingOrderResponse,
} from "@/api/generated/types.gen";
import { ScheduledPaymentsPeek } from "./ScheduledPaymentsPeek";

const useDirectDebitsMock = vi.fn();
const useStandingOrdersMock = vi.fn();
vi.mock("./useDirectDebits", () => ({
  useDirectDebits: () => useDirectDebitsMock(),
}));
vi.mock("./useStandingOrders", () => ({
  useStandingOrders: () => useStandingOrdersMock(),
}));

const NOW = new Date("2026-05-20T12:00:00Z");

function makeDd(over: Partial<DirectDebitResponse>): DirectDebitResponse {
  return {
    directDebitId: "dd-1",
    accountId: "acc-1",
    creditorName: "Thames Water",
    creditorSortCode: "12-34-56",
    creditorAccountNumber: "12345678",
    amountMinorUnits: 4500,
    currency: "GBP",
    frequency: "MONTHLY",
    status: "ACTIVE",
    startDate: "2026-01-15",
    nextCollectionAt: "2026-05-23T12:00:00Z",
    ...over,
  };
}

function makeSo(over: Partial<StandingOrderResponse>): StandingOrderResponse {
  return {
    standingOrderId: "so-1",
    accountId: "acc-1",
    payeeName: "Landlord",
    payeeSortCode: "11-22-33",
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

function renderPeek(accountUid = "acc-1") {
  return render(
    <MemoryRouter>
      <ScheduledPaymentsPeek accountUid={accountUid} now={NOW} />
    </MemoryRouter>,
  );
}

describe("ScheduledPaymentsPeek", () => {
  beforeEach(() => {
    useDirectDebitsMock.mockReset();
    useStandingOrdersMock.mockReset();
    useDirectDebitsMock.mockReturnValue({
      directDebits: [],
      isPending: false,
      isError: false,
    });
    useStandingOrdersMock.mockReturnValue({
      standingOrders: [],
      isPending: false,
      isError: false,
    });
  });

  it("renders the section heading 'Scheduled payments on this account'", () => {
    renderPeek();
    expect(
      screen.getByRole("heading", {
        level: 2,
        name: "Scheduled payments on this account",
      }),
    ).toBeInTheDocument();
  });

  it("renders both Direct Debits and Standing Orders sub-headings", () => {
    renderPeek();
    expect(
      screen.getByRole("heading", { level: 3, name: "Direct Debits" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { level: 3, name: "Standing Orders" }),
    ).toBeInTheDocument();
  });

  it("renders the DD empty state when the account has no direct debits", () => {
    renderPeek();
    expect(screen.getByTestId("peek-dd-empty")).toHaveTextContent(
      "No direct debits on this account.",
    );
  });

  it("renders the SO empty state when the account has no standing orders", () => {
    renderPeek();
    expect(screen.getByTestId("peek-so-empty")).toHaveTextContent(
      "No standing orders on this account.",
    );
  });

  it("renders a 'View all' link in each sub-section to its hub", () => {
    renderPeek();
    expect(
      screen.getByRole("link", { name: "View all direct debits" }),
    ).toHaveAttribute("href", "/move/direct-debits");
    expect(
      screen.getByRole("link", { name: "View all standing orders" }),
    ).toHaveAttribute("href", "/move/standing-orders");
  });

  it("filters direct debits to the matching account id", () => {
    useDirectDebitsMock.mockReturnValue({
      directDebits: [
        makeDd({ directDebitId: "dd-self", accountId: "acc-1" }),
        makeDd({ directDebitId: "dd-other", accountId: "acc-2" }),
      ],
      isPending: false,
      isError: false,
    });
    renderPeek("acc-1");
    const ddList = screen.getByTestId("peek-dd-list");
    const rows = within(ddList).getAllByTestId("dd-row");
    expect(rows).toHaveLength(1);
    expect(within(rows[0]!).getByTestId("dd-primary")).toBeInTheDocument();
    expect(screen.queryByTestId("peek-dd-empty")).toBeNull();
  });

  it("filters standing orders to the matching account id", () => {
    useStandingOrdersMock.mockReturnValue({
      standingOrders: [
        makeSo({ standingOrderId: "so-self", accountId: "acc-1" }),
        makeSo({ standingOrderId: "so-other", accountId: "acc-2" }),
      ],
      isPending: false,
      isError: false,
    });
    renderPeek("acc-1");
    const soList = screen.getByTestId("peek-so-list");
    const rows = within(soList).getAllByTestId("so-row");
    expect(rows).toHaveLength(1);
    expect(screen.queryByTestId("peek-so-empty")).toBeNull();
  });

  it("excludes CANCELLED direct debits from the peek", () => {
    useDirectDebitsMock.mockReturnValue({
      directDebits: [
        makeDd({ directDebitId: "dd-live", accountId: "acc-1" }),
        makeDd({
          directDebitId: "dd-gone",
          accountId: "acc-1",
          status: "CANCELLED",
          cancelledAt: "2026-05-01T09:00:00Z",
        }),
      ],
      isPending: false,
      isError: false,
    });
    renderPeek("acc-1");
    const ddList = screen.getByTestId("peek-dd-list");
    expect(within(ddList).getAllByTestId("dd-row")).toHaveLength(1);
  });

  it("shows the DD empty state when the account's only direct debits are cancelled", () => {
    useDirectDebitsMock.mockReturnValue({
      directDebits: [
        makeDd({
          accountId: "acc-1",
          status: "CANCELLED",
          cancelledAt: "2026-05-01T09:00:00Z",
        }),
      ],
      isPending: false,
      isError: false,
    });
    renderPeek("acc-1");
    expect(screen.getByTestId("peek-dd-empty")).toBeInTheDocument();
  });

  it("excludes CANCELLED standing orders from the peek", () => {
    useStandingOrdersMock.mockReturnValue({
      standingOrders: [
        makeSo({ standingOrderId: "so-live", accountId: "acc-1" }),
        makeSo({
          standingOrderId: "so-gone",
          accountId: "acc-1",
          status: "CANCELLED",
          cancelledAt: "2026-05-01T09:00:00Z",
        }),
      ],
      isPending: false,
      isError: false,
    });
    renderPeek("acc-1");
    const soList = screen.getByTestId("peek-so-list");
    expect(within(soList).getAllByTestId("so-row")).toHaveLength(1);
  });

  it("shows the SO empty state when the account's only standing orders are cancelled", () => {
    useStandingOrdersMock.mockReturnValue({
      standingOrders: [
        makeSo({
          accountId: "acc-1",
          status: "CANCELLED",
          cancelledAt: "2026-05-01T09:00:00Z",
        }),
      ],
      isPending: false,
      isError: false,
    });
    renderPeek("acc-1");
    expect(screen.getByTestId("peek-so-empty")).toBeInTheDocument();
  });

  it("renders a DD skeleton while direct debits are pending", () => {
    useDirectDebitsMock.mockReturnValue({
      directDebits: null,
      isPending: true,
      isError: false,
    });
    renderPeek();
    expect(screen.getByTestId("peek-dd-skeleton")).toBeInTheDocument();
    expect(screen.queryByTestId("peek-dd-empty")).toBeNull();
  });

  it("renders a SO skeleton while standing orders are pending", () => {
    useStandingOrdersMock.mockReturnValue({
      standingOrders: null,
      isPending: true,
      isError: false,
    });
    renderPeek();
    expect(screen.getByTestId("peek-so-skeleton")).toBeInTheDocument();
    expect(screen.queryByTestId("peek-so-empty")).toBeNull();
  });

  it("renders a DD error caption when the direct-debits query fails", () => {
    useDirectDebitsMock.mockReturnValue({
      directDebits: null,
      isPending: false,
      isError: true,
    });
    renderPeek();
    expect(screen.getByTestId("peek-dd-error")).toHaveTextContent(
      "Couldn’t load direct debits",
    );
  });

  it("renders a SO error caption when the standing-orders query fails", () => {
    useStandingOrdersMock.mockReturnValue({
      standingOrders: null,
      isPending: false,
      isError: true,
    });
    renderPeek();
    expect(screen.getByTestId("peek-so-error")).toHaveTextContent(
      "Couldn’t load standing orders",
    );
  });

  it("does not render the account chip on any row (per-account context)", () => {
    useDirectDebitsMock.mockReturnValue({
      directDebits: [makeDd({ accountId: "acc-1" })],
      isPending: false,
      isError: false,
    });
    useStandingOrdersMock.mockReturnValue({
      standingOrders: [makeSo({ accountId: "acc-1" })],
      isPending: false,
      isError: false,
    });
    renderPeek("acc-1");
    expect(screen.queryByTestId("dd-account-chip")).toBeNull();
    expect(screen.queryByTestId("so-account-chip")).toBeNull();
  });

  it("sorts each list by next-date ascending", () => {
    useDirectDebitsMock.mockReturnValue({
      directDebits: [
        makeDd({
          directDebitId: "dd-later",
          accountId: "acc-1",
          creditorName: "Vodafone",
          nextCollectionAt: "2026-06-01T12:00:00Z",
        }),
        makeDd({
          directDebitId: "dd-sooner",
          accountId: "acc-1",
          creditorName: "Thames Water",
          nextCollectionAt: "2026-05-23T12:00:00Z",
        }),
      ],
      isPending: false,
      isError: false,
    });
    renderPeek("acc-1");
    const ddList = screen.getByTestId("peek-dd-list");
    const rows = within(ddList).getAllByTestId("dd-row");
    expect(within(rows[0]!).getByTestId("dd-primary")).toHaveTextContent(
      "Thames Water",
    );
    expect(within(rows[1]!).getByTestId("dd-primary")).toHaveTextContent(
      "Vodafone",
    );
  });
});
