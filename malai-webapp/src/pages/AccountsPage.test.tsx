import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import type { AccountResponse, UserResponse } from "@/api/generated/types.gen";
import { useBalanceVisibility } from "@/accounts/useBalanceVisibility";
import { AccountsPage } from "./AccountsPage";

const useAccountsMock = vi.fn();
const useActiveAccountHolderMock = vi.fn();
const useOutletContextMock = vi.fn();

vi.mock("@/accounts/useAccounts", () => ({
  useAccounts: () => useAccountsMock(),
}));
vi.mock("@/account-holder/useActiveAccountHolder", () => ({
  useActiveAccountHolder: () => useActiveAccountHolderMock(),
}));
vi.mock("@/accounts/AccountCard", () => ({
  AccountCard: ({ account }: { account: AccountResponse }) => (
    <div data-testid="account-card-stub">{account.name}</div>
  ),
}));

vi.mock("react-router-dom", async () => {
  const actual =
    await vi.importActual<typeof import("react-router-dom")>(
      "react-router-dom",
    );
  return {
    ...actual,
    useOutletContext: () => useOutletContextMock(),
  };
});

const USER: UserResponse = {
  firstName: "Ada",
  lastName: "Lovelace",
  email: "ada@example.com",
  roles: ["CUSTOMER"],
};

const HOLDER = {
  accountHolderUid: "11111111-1111-1111-1111-111111111111",
  accountHolderName: "Ada Lovelace",
  accountHolderType: "INDIVIDUAL",
};

const ACCOUNT_A: AccountResponse = {
  accountUid: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  accountType: "CURRENT",
  defaultCategory: "PERSONAL",
  currency: "GBP",
  createdAt: "2024-01-01T00:00:00Z",
  name: "Everyday",
};

const ACCOUNT_B: AccountResponse = {
  accountUid: "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
  accountType: "SAVINGS",
  defaultCategory: "PERSONAL",
  currency: "GBP",
  createdAt: "2024-02-01T00:00:00Z",
  name: "Holiday",
};

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/"]}>
      <Routes>
        <Route path="/" element={<AccountsPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("AccountsPage", () => {
  beforeEach(() => {
    useBalanceVisibility.setState({ hidden: false });

    useOutletContextMock.mockReset();
    useAccountsMock.mockReset();
    useActiveAccountHolderMock.mockReset();

    useOutletContextMock.mockReturnValue({ user: USER });
    useActiveAccountHolderMock.mockReturnValue({
      status: "ready",
      activeHolder: HOLDER,
      error: null,
    });
    useAccountsMock.mockReturnValue({
      accounts: [ACCOUNT_A, ACCOUNT_B],
      isPending: false,
      isError: false,
      error: null,
    });
  });

  it("renders a 'Welcome back, {firstName}' greeting with no time-of-day modifier", () => {
    renderPage();
    const heading = screen.getByRole("heading", { level: 1 });
    expect(heading).toHaveTextContent(/^Welcome back, Ada$/);
  });

  it("falls back via displayNameOf when the user has no firstName", () => {
    useOutletContextMock.mockReturnValue({
      user: {
        firstName: undefined,
        lastName: "Lovelace",
        email: "ada@example.com",
        roles: ["CUSTOMER"],
      } satisfies UserResponse,
    });
    renderPage();
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(
      /^Welcome back, Lovelace$/,
    );
  });

  it("renders three quick actions with the correct destinations for both breakpoints", () => {
    renderPage();
    const quick = screen.getByTestId("quick-actions");

    const send = within(quick).getByTestId("quick-action-send");
    const payees = within(quick).getByTestId("quick-action-payees");
    const settingsDesktop = within(quick).getByTestId(
      "quick-action-settings-desktop",
    );
    const settingsMobile = within(quick).getByTestId(
      "quick-action-settings-mobile",
    );

    expect(send).toHaveAttribute("href", "/move/send");
    expect(payees).toHaveAttribute("href", "/move/payees");
    expect(settingsDesktop).toHaveAttribute("href", "/settings");
    expect(settingsMobile).toHaveAttribute("href", "/profile");

    expect(settingsDesktop).toHaveClass("hidden");
    expect(settingsDesktop).toHaveClass("md:inline-flex");
    expect(settingsMobile).toHaveClass("md:hidden");
  });

  it("renders one account card per Account", () => {
    renderPage();
    const cards = screen.getAllByTestId("account-card-stub");
    expect(cards).toHaveLength(2);
    expect(cards[0]).toHaveTextContent("Everyday");
    expect(cards[1]).toHaveTextContent("Holiday");
  });

  it("renders the active holder name under the greeting", () => {
    renderPage();
    expect(screen.getByTestId("active-holder-name")).toHaveTextContent(
      "Ada Lovelace",
    );
  });

  it("does not render an aggregate total-balance figure or its skeleton anywhere on the page", () => {
    renderPage();
    expect(screen.queryByTestId("total-balance-hero")).not.toBeInTheDocument();
    expect(
      screen.queryByTestId("total-balance-skeleton"),
    ).not.toBeInTheDocument();
    expect(screen.queryByTestId("total-balance-error")).not.toBeInTheDocument();
    expect(
      screen.queryByTestId("total-balance-hero-masked"),
    ).not.toBeInTheDocument();
    expect(screen.queryByText(/Total balance/i)).not.toBeInTheDocument();
  });

  it("renders the master visibility toggle at the right of the Accounts section heading", () => {
    renderPage();
    const section = screen.getByRole("region", { name: /accounts/i });
    const toggle = within(section).getByTestId(
      "master-balance-visibility-toggle",
    );
    expect(toggle).toBeInTheDocument();
    expect(toggle).toHaveAttribute("aria-label", "Hide balances");
    expect(toggle).toHaveAttribute("aria-pressed", "false");
  });

  it("clicking the master toggle flips the shared balance-visibility state", async () => {
    const user = userEvent.setup();
    renderPage();
    expect(useBalanceVisibility.getState().hidden).toBe(false);

    await user.click(screen.getByTestId("master-balance-visibility-toggle"));
    expect(useBalanceVisibility.getState().hidden).toBe(true);

    const toggle = screen.getByTestId("master-balance-visibility-toggle");
    expect(toggle).toHaveAttribute("aria-pressed", "true");
    expect(toggle).toHaveAttribute("aria-label", "Show balances");

    await user.click(toggle);
    expect(useBalanceVisibility.getState().hidden).toBe(false);
  });

  it("renders a failure panel, not the empty state, when accounts fail to load", () => {
    useAccountsMock.mockReturnValue({
      accounts: null,
      isPending: false,
      isError: true,
      error: new Error("boom"),
    });
    renderPage();
    expect(screen.getByTestId("accounts-error")).toHaveTextContent(
      "We couldn’t load your accounts",
    );
    expect(screen.queryByText("No accounts to show.")).not.toBeInTheDocument();
    expect(screen.queryByTestId("accounts-list")).not.toBeInTheDocument();
  });
});
