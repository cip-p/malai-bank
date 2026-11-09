import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { AccountDetailPage } from "./AccountDetailPage";

const useAccountMock = vi.fn();
vi.mock("@/accounts/useAccount", () => ({
  useAccount: (uid: string) => useAccountMock(uid),
}));
vi.mock("@/accounts/AccountHeroCard", () => ({
  AccountHeroCard: ({ accountUid }: { accountUid: string }) => (
    <div data-testid="hero-stub">{accountUid}</div>
  ),
}));
vi.mock("@/accounts/ForecastCard", () => ({
  ForecastCard: ({ accountUid }: { accountUid: string }) => (
    <div data-testid="forecast-card-stub">{accountUid}</div>
  ),
}));
vi.mock("@/accounts/TransactionsList", () => ({
  TransactionsList: ({ accountUid }: { accountUid: string }) => (
    <div data-testid="transactions-stub">{accountUid}</div>
  ),
}));
vi.mock("@/move-money/SendMoneyCta", () => ({
  SendMoneyCta: ({ accountUid }: { accountUid: string }) => (
    <div data-testid="send-money-cta-stub">{accountUid}</div>
  ),
}));
vi.mock("@/move-money/ScheduledPaymentsPeek", () => ({
  ScheduledPaymentsPeek: ({ accountUid }: { accountUid: string }) => (
    <div data-testid="scheduled-payments-peek-stub">{accountUid}</div>
  ),
}));

function renderAt(uid: string) {
  return render(
    <MemoryRouter initialEntries={[`/accounts/${uid}`]}>
      <Routes>
        <Route path="/accounts/:accountUid" element={<AccountDetailPage />} />
        <Route path="/" element={<div>home</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("AccountDetailPage routing", () => {
  it("renders AccountNotFoundPage when useAccount returns status 'not-found'", () => {
    useAccountMock.mockReturnValue({ account: null, status: "not-found" });
    renderAt("00000000-0000-0000-0000-000000000000");

    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(
      "Account not found",
    );
    expect(
      screen.getByRole("link", { name: "← Back to Accounts" }),
    ).toHaveAttribute("href", "/");
    expect(screen.queryByTestId("hero-stub")).not.toBeInTheDocument();
    expect(screen.queryByTestId("forecast-card-stub")).not.toBeInTheDocument();
    expect(screen.queryByTestId("transactions-stub")).not.toBeInTheDocument();
    expect(screen.queryByTestId("send-money-cta-stub")).not.toBeInTheDocument();
    expect(
      screen.queryByTestId("scheduled-payments-peek-stub"),
    ).not.toBeInTheDocument();
  });

  it("renders the hero + forecast + CTA + transactions + scheduled-peek composition when 'ready'", () => {
    useAccountMock.mockReturnValue({
      account: { accountUid: "abc" },
      status: "ready",
    });
    renderAt("abc");

    expect(screen.getByTestId("hero-stub")).toHaveTextContent("abc");
    expect(screen.getByTestId("forecast-card-stub")).toHaveTextContent("abc");
    expect(screen.getByTestId("send-money-cta-stub")).toHaveTextContent("abc");
    expect(screen.getByTestId("transactions-stub")).toHaveTextContent("abc");
    expect(
      screen.getByTestId("scheduled-payments-peek-stub"),
    ).toHaveTextContent("abc");
    expect(screen.getByRole("link", { name: /Spending/ })).toHaveAttribute(
      "href",
      "/accounts/abc/spending",
    );
    expect(
      screen.queryByRole("heading", { name: "Account not found" }),
    ).not.toBeInTheDocument();
  });

  it("renders the loading skeleton while useAccount is fetching", () => {
    useAccountMock.mockReturnValue({ account: null, status: "fetching" });
    renderAt("abc");

    expect(screen.getByTestId("account-detail-skeleton")).toBeInTheDocument();
    expect(screen.queryByTestId("hero-stub")).not.toBeInTheDocument();
    expect(screen.queryByTestId("send-money-cta-stub")).not.toBeInTheDocument();
    expect(
      screen.queryByTestId("scheduled-payments-peek-stub"),
    ).not.toBeInTheDocument();
  });

  it("orders the page sections: hero, forecast, send-money CTA, transactions, scheduled-peek", () => {
    useAccountMock.mockReturnValue({
      account: { accountUid: "abc" },
      status: "ready",
    });
    renderAt("abc");

    const hero = screen.getByTestId("hero-stub");
    const forecast = screen.getByTestId("forecast-card-stub");
    const cta = screen.getByTestId("send-money-cta-stub");
    const transactions = screen.getByTestId("transactions-stub");
    const peek = screen.getByTestId("scheduled-payments-peek-stub");

    const order = [hero, forecast, cta, transactions, peek];
    for (let i = 0; i < order.length - 1; i++) {
      const a = order[i]!;
      const b = order[i + 1]!;
      expect(
        a.compareDocumentPosition(b) & Node.DOCUMENT_POSITION_FOLLOWING,
      ).toBeTruthy();
    }
  });
});
